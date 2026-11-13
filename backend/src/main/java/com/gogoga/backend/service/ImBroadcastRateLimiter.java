package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 群发发送的限流与并发控制。
 *
 * <p>两层控制：全局固定窗口令牌桶约束整体对美团的 QPS；单门店信号量约束同一门店同时执行的分片数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImBroadcastRateLimiter {

    private static final String GLOBAL_QPS_KEY_PREFIX = "im:broadcast:qps:";
    private static final String SHOP_SLOT_KEY_PREFIX = "im:broadcast:shop:";

    /**
     * 固定窗口计数：首次创建时带上过期时间，避免键泄漏。
     */
    private static final DefaultRedisScript<Long> ACQUIRE_QPS_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('incr', KEYS[1]) "
                    + "if current == 1 then redis.call('pexpire', KEYS[1], ARGV[2]) end "
                    + "if current > tonumber(ARGV[1]) then return 0 else return 1 end",
            Long.class
    );

    private static final DefaultRedisScript<Long> RELEASE_SLOT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final MtProperties mtProperties;

    /**
     * 阻塞等待一个全局发送配额。等待期间自旋到下一个窗口，最多等待 {@code maxWaitMillis}。
     *
     * @return 是否取得配额
     */
    public boolean acquireGlobalPermit(long maxWaitMillis) {
        int qps = Math.max(mtProperties.getImBroadcast().getGlobalQps(), 1);
        long deadline = System.currentTimeMillis() + Math.max(maxWaitMillis, 0L);
        while (true) {
            long second = System.currentTimeMillis() / 1000L;
            Long allowed = stringRedisTemplate.execute(
                    ACQUIRE_QPS_SCRIPT,
                    Collections.singletonList(GLOBAL_QPS_KEY_PREFIX + second),
                    String.valueOf(qps),
                    String.valueOf(2000L)
            );
            if (allowed != null && allowed == 1L) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            sleepUntilNextWindow();
        }
    }

    /**
     * 抢占门店发送槽位，限制单店同时执行的分片数。
     *
     * @return 槽位句柄；抢占失败返回 null
     */
    public SlotHandle tryAcquireShopSlot(Long shopId, Duration lease) {
        int concurrency = Math.max(mtProperties.getImBroadcast().getShopConcurrency(), 1);
        for (int slot = 0; slot < concurrency; slot++) {
            String key = SHOP_SLOT_KEY_PREFIX + shopId + ":" + slot;
            String token = UUID.randomUUID().toString();
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, lease);
            if (Boolean.TRUE.equals(acquired)) {
                return new SlotHandle(key, token);
            }
        }
        return null;
    }

    private void sleepUntilNextWindow() {
        try {
            Thread.sleep(1000L - (System.currentTimeMillis() % 1000L) + 1L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("IM_BROADCAST_RATE_LIMIT_INTERRUPTED", e);
        }
    }

    private void releaseSlot(String key, String token) {
        try {
            stringRedisTemplate.execute(RELEASE_SLOT_SCRIPT, List.of(key), token);
        } catch (Exception e) {
            log.warn("释放群发门店槽位失败，key={}", key, e);
        }
    }

    /**
     * 门店发送槽位句柄，close 时释放。
     */
    public class SlotHandle implements AutoCloseable {

        private final String key;
        private final String token;

        private SlotHandle(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            releaseSlot(key, token);
        }
    }
}

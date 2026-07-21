package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.ImAutoReplyChatMessage;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.entity.mt.MtImTestChatLog;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtImTestChatLogMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.param.ImTestChatParam;
import com.gogoga.backend.util.CustomerPhoneExtractor;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.ImTestChatLogVO;
import com.gogoga.backend.vo.ImTestChatReplyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库测试聊天：复用线上分类/召回/生成/敏感词链路，但剥离计费、美团发送与会话持久化，
 * 对话历史由前端持有并随请求带上，仅写审计表用于留痕与每日限流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImTestChatService {

    private static final String PROMPT_KEY = "IM_AUTO_REPLY_SYSTEM";
    private static final String FALLBACK_SYSTEM_PROMPT = "你是门店在线客服，请基于短期会话历史与门店知识片段简洁、礼貌地回复。"
            + "联系方式、营业时间、门店地址、价格与订单进度等信息，只有知识片段中有明确内容时才可回复，"
            + "否则说明没有确切信息并建议联系人工客服，严禁猜测或编造任何可能误导顾客的内容。";

    /** 每个门店每日测试消息上限。 */
    static final int DAILY_LIMIT_PER_SHOP = 100;

    /** 召回诊断展示的候选数量。 */
    private static final int DIAGNOSIS_TOP_N = 8;

    private final MtProperties mtProperties;
    private final MtShopMapper shopMapper;
    private final MtImTestChatLogMapper testChatLogMapper;
    private final ImMessageClassificationService messageClassificationService;
    private final ImAutoReplyRagService ragService;
    private final ShopBasicInfoPromptService shopBasicInfoPromptService;
    private final ImAutoReplyChatService chatService;
    private final SystemPromptService systemPromptService;
    private final SensitiveWordService sensitiveWordService;
    private final ImReplyContactSuffixService contactSuffixService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;

    public ImTestChatReplyVO chat(Long shopId, ImTestChatParam param) {
        String message = StringUtils.trimToNull(param == null ? null : param.getMessage());
        if (message == null) {
            throw new BusinessException("测试消息不能为空");
        }
        long usedToday = testChatLogMapper.countByShopSince(shopId, LocalDate.now().atStartOfDay());
        if (usedToday >= DAILY_LIMIT_PER_SHOP) {
            throw new BusinessException("今日该门店测试消息已达上限（" + DAILY_LIMIT_PER_SHOP + " 条），请明日再试");
        }

        List<ImAutoReplyChatMessage> history = buildHistory(param, message);
        List<String> recentUserMessages = latestUserMessages(history,
                mtProperties.getImAutoReply().getRagRecentUserMessages());
        ImMessageClassificationService.ClassificationResult classification =
                messageClassificationService.classifyDetailed(recentUserMessages);
        String category = classification.category().name();
        ImAutoReplyRagService.RagContext ragContext = ragService.retrieve(shopId, recentUserMessages, category);

        MtShop shop = shopMapper.selectById(shopId);
        String shopName = shop == null ? "当前门店"
                : StringUtils.defaultIfBlank(shop.getPoiName(), StringUtils.defaultIfBlank(shop.getName(), "当前门店"));
        ResolvedSystemPromptDTO prompt = systemPromptService.render(
                PROMPT_KEY, Map.of("shop_name", shopName), FALLBACK_SYSTEM_PROMPT);
        String contactPhone = contactSuffixService.resolveContactPhone(shopId);
        String modelContext = shopBasicInfoPromptService.merge(
                shopBasicInfoPromptService.build(shop, StringUtils.isNotBlank(contactPhone)),
                ragContext.promptContext());

        ImAutoReplyChatResult result = chatService.generate(
                prompt.getContent(), modelContext, history);
        String content = result.content();
        SensitiveWordHitDTO triggerHit = sensitiveWordService.detect(content);
        SensitiveWordHitDTO currentHit = triggerHit;
        int rewriteCount = 0;
        int maxRewriteAttempts = Math.max(mtProperties.getSensitiveWord().getMaxRewriteAttempts(), 0);
        while (currentHit.isHit() && rewriteCount < maxRewriteAttempts) {
            ImAutoReplyChatResult rewritten = chatService.rewrite(
                    prompt.getContent(), modelContext, history, content);
            result = result.plusUsage(rewritten);
            content = result.content();
            currentHit = sensitiveWordService.detect(content);
            rewriteCount++;
        }
        boolean blocked = false;
        if (currentHit.isHit()) {
            content = mtProperties.getImAutoReply().getSafeFallbackReply();
            blocked = true;
        }
        // 与线上自动回复保持一致：测试对话也要能看到结尾拼接的联系方式。
        content = contactSuffixService.appendPhone(content, contactPhone);
        if (triggerHit.isHit()) {
            log.warn("测试聊天回复命中敏感词，shopId={}, operator={}, categories={}, words={}, severity={}, rewriteCount={}, blocked={}",
                    shopId, UserContext.getUserId(), triggerHit.getCategories(), triggerHit.getHitWords(),
                    triggerHit.getHighestSeverity(), rewriteCount, blocked);
        }

        // 审计始终全量落库（管理员可见）；返回体按身份裁剪。
        ImTestChatReplyVO full = buildReplyVo(content, category, ragContext, rewriteCount, blocked, result);
        full.setSensitiveHit(toSensitiveHit(triggerHit));
        full.setClassificationDebug(toClassificationDebug(classification));
        full.setUserPhones(CustomerPhoneExtractor.extract(message));
        saveLog(shopId, StringUtils.trimToNull(param.getSessionId()), message, full);

        if (!isAdminView()) {
            // 普通商户只返回回复文本，不下发召回诊断/分类原始输出/命中词等调试信息。
            ImTestChatReplyVO minimal = new ImTestChatReplyVO();
            minimal.setReply(full.getReply());
            return minimal;
        }
        full.setDiagnostics(buildDiagnostics(shopId, recentUserMessages));
        return full;
    }

    /**
     * 是否管理员视角：ADMIN 工作台，或总后台（未切工作台）下的超级管理员。普通商户（ORDINARY）不展示调试信息。
     */
    private boolean isAdminView() {
        BackendPortalType portal = UserContext.getActivePortal();
        return portal == BackendPortalType.ADMIN || (portal == null && UserContext.isSuperAdmin());
    }

    private ImTestChatReplyVO.ClassificationDebug toClassificationDebug(
            ImMessageClassificationService.ClassificationResult result) {
        if (result == null) {
            return null;
        }
        ImTestChatReplyVO.ClassificationDebug debug = new ImTestChatReplyVO.ClassificationDebug();
        debug.setRawOutput(result.rawOutput());
        debug.setPromptSource(result.promptSource());
        debug.setError(result.error());
        return debug;
    }

    private ImTestChatReplyVO.SensitiveHit toSensitiveHit(SensitiveWordHitDTO hit) {
        if (hit == null || !hit.isHit()) {
            return null;
        }
        ImTestChatReplyVO.SensitiveHit vo = new ImTestChatReplyVO.SensitiveHit();
        vo.setCategories(hit.getCategories());
        vo.setWords(hit.getHitWords());
        vo.setHighestSeverity(hit.getHighestSeverity());
        vo.setHitCount(hit.getHitCount());
        return vo;
    }

    /**
     * 只读召回诊断：对本轮 query 单独探测候选片段及其原始余弦分（不做阈值过滤），
     * 用于定位“未命中”是阈值差一点、还是 embedding 配置不匹配导致零候选。
     */
    private ImTestChatReplyVO.Diagnostics buildDiagnostics(Long shopId, List<String> recentUserMessages) {
        MtProperties.Knowledge knowledge = mtProperties.getKnowledge();
        ImTestChatReplyVO.Diagnostics diagnostics = new ImTestChatReplyVO.Diagnostics();
        diagnostics.setThreshold(knowledge.getMinContextCosineScore());
        diagnostics.setHasShopKnowledge(knowledgeVectorStore.hasShopKnowledge(shopId));
        diagnostics.setEmbeddingConfig(knowledge.getEmbeddingProvider() + " / "
                + knowledge.getEmbeddingModel() + " / " + knowledge.getEmbeddingDim());
        diagnostics.setCandidateCount(0);
        diagnostics.setTopCandidates(List.of());

        String query = recentUserMessages == null ? "" : String.join("\n", recentUserMessages);
        if (!knowledge.isEnabled() || StringUtils.isBlank(query)) {
            return diagnostics;
        }
        try {
            float[] embedding = knowledgeEmbeddingService.embed(query);
            List<KnowledgeSearchHitDTO> hits = knowledgeVectorStore.diagnoseCandidates(shopId, embedding, DIAGNOSIS_TOP_N);
            double threshold = knowledge.getMinContextCosineScore();
            List<ImTestChatReplyVO.Diagnostics.Candidate> candidates = new ArrayList<>();
            for (KnowledgeSearchHitDTO hit : hits) {
                if (hit == null || hit.getChunk() == null) {
                    continue;
                }
                ImTestChatReplyVO.Diagnostics.Candidate candidate = new ImTestChatReplyVO.Diagnostics.Candidate();
                candidate.setChunkText(hit.getChunk().getChunkText());
                candidate.setCosineScore(hit.getCosineScore());
                candidate.setSourceType(hit.getChunk().getSourceType());
                candidate.setCategory(hit.getChunk().getCategory());
                candidate.setPassed(hit.getCosineScore() != null && hit.getCosineScore() >= threshold);
                candidates.add(candidate);
            }
            diagnostics.setCandidateCount(candidates.size());
            diagnostics.setTopCandidates(candidates);
        } catch (RuntimeException ignored) {
            // 诊断为辅助信息，失败不影响测试回复主流程。
        }
        return diagnostics;
    }

    public List<ImTestChatLogVO> listLogs(Long shopId) {
        if (!isAdminView()) {
            throw new BusinessException("无权限查看测试记录");
        }
        return testChatLogMapper.selectByShop(shopId).stream()
                .map(this::toLogVo)
                .toList();
    }

    private List<ImAutoReplyChatMessage> buildHistory(ImTestChatParam param, String currentMessage) {
        int maxRounds = Math.max(mtProperties.getImAutoReply().getHistoryMaxRounds(), 1);
        int maxMessages = Math.min(maxRounds * 4, 1000);
        int maxChars = Math.max(mtProperties.getImAutoReply().getMaxContextChars(), 1);

        List<ImAutoReplyChatMessage> all = new ArrayList<>();
        if (param != null && param.getHistory() != null) {
            for (ImTestChatParam.HistoryMessage item : param.getHistory()) {
                if (item == null) {
                    continue;
                }
                String text = StringUtils.trimToNull(item.getContent());
                if (text == null) {
                    continue;
                }
                String role = "assistant".equals(item.getRole()) ? "assistant" : "user";
                all.add(new ImAutoReplyChatMessage(role, truncate(text, maxChars)));
            }
        }
        all.add(new ImAutoReplyChatMessage("user", truncate(currentMessage, maxChars)));

        // 仅保留最近的若干条，防止前端塞入超长历史。
        if (all.size() > maxMessages) {
            return new ArrayList<>(all.subList(all.size() - maxMessages, all.size()));
        }
        return all;
    }

    private List<String> latestUserMessages(List<ImAutoReplyChatMessage> history, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> userMessages = new ArrayList<>();
        for (ImAutoReplyChatMessage message : history) {
            if ("user".equals(message.role())) {
                userMessages.add(message.content());
            }
        }
        if (userMessages.isEmpty()) {
            return List.of();
        }
        return userMessages.subList(Math.max(userMessages.size() - limit, 0), userMessages.size());
    }

    private ImTestChatReplyVO buildReplyVo(String content,
                                           String category,
                                           ImAutoReplyRagService.RagContext ragContext,
                                           int rewriteCount,
                                           boolean blocked,
                                           ImAutoReplyChatResult result) {
        ImTestChatReplyVO vo = new ImTestChatReplyVO();
        vo.setReply(content);
        vo.setCategory(category);
        vo.setRagUsed(ragContext.used());
        vo.setRagMaxScore(ragContext.maxCosineScore());
        vo.setRewriteCount(rewriteCount);
        vo.setBlocked(blocked);
        vo.setPromptTokens(result.promptTokens());
        vo.setCompletionTokens(result.completionTokens());
        List<ImTestChatReplyVO.RagHit> hits = new ArrayList<>();
        if (ragContext.hits() != null) {
            for (KnowledgeSearchHitDTO hit : ragContext.hits()) {
                if (hit == null || hit.getChunk() == null || StringUtils.isBlank(hit.getChunk().getChunkText())) {
                    continue;
                }
                ImTestChatReplyVO.RagHit item = new ImTestChatReplyVO.RagHit();
                item.setChunkText(hit.getChunk().getChunkText().trim());
                item.setScore(hit.getCosineScore());
                hits.add(item);
            }
        }
        vo.setRagChunkCount(hits.size());
        vo.setRagHits(hits);
        return vo;
    }

    private void saveLog(Long shopId, String sessionId, String userMessage, ImTestChatReplyVO vo) {
        MtProperties.ImAutoReply config = mtProperties.getImAutoReply();
        MtImTestChatLog log = new MtImTestChatLog();
        log.setShopId(shopId);
        log.setSessionId(sessionId);
        log.setOperatorUserId(UserContext.getUserId());
        log.setOperatorName(UserContext.getUserName());
        log.setUserMessage(userMessage);
        log.setAiReply(vo.getReply());
        log.setUserMessageCategory(vo.getCategory());
        log.setRagUsed(vo.getRagUsed());
        log.setRagChunkCount(vo.getRagChunkCount());
        log.setRagMaxScore(vo.getRagMaxScore());
        log.setRewriteCount(vo.getRewriteCount());
        log.setBlocked(vo.getBlocked());
        log.setModelProvider(config.getModelProvider());
        log.setModelName(config.getChatModel());
        log.setPromptTokens(vo.getPromptTokens());
        log.setCompletionTokens(vo.getCompletionTokens());
        log.setCreateTime(LocalDateTime.now());
        testChatLogMapper.insert(log);
    }

    private ImTestChatLogVO toLogVo(MtImTestChatLog log) {
        ImTestChatLogVO vo = new ImTestChatLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private String truncate(String text, int maxChars) {
        return text.length() > maxChars ? StringUtils.right(text, maxChars) : text;
    }
}

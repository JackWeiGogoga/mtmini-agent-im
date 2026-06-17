package com.gogoga.backend.service;

import com.gogoga.backend.constant.MtH5GuideCodeConstant;
import com.gogoga.backend.entity.mt.MtH5GuideState;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtH5GuideStateMapper;
import com.gogoga.backend.vo.MtH5GuideStateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * H5 引导状态服务
 *
 * @author system
 * @since 2026-05-12
 */
@Service
@RequiredArgsConstructor
public class MtH5GuideStateService {

    private final MtH5ShopService mtH5ShopService;
    private final MtH5GuideStateMapper mtH5GuideStateMapper;

    public MtH5GuideStateVO getCurrentGuideState(String guideCode) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return getGuideState(shop.getId(), guideCode);
    }

    public MtH5GuideStateVO getGuideState(Long shopId, String guideCode) {
        validateGuideCode(guideCode);
        MtH5GuideState state = mtH5GuideStateMapper.selectByShopIdAndGuideCode(shopId, guideCode);
        return buildVO(guideCode, state);
    }

    public MtH5GuideStateVO completeCurrentGuide(String guideCode) {
        validateGuideCode(guideCode);
        MtShop shop = mtH5ShopService.getCurrentShop();
        LocalDateTime now = LocalDateTime.now();
        mtH5GuideStateMapper.upsertCompleted(shop.getId(), guideCode, now);
        MtH5GuideState state = mtH5GuideStateMapper.selectByShopIdAndGuideCode(shop.getId(), guideCode);
        return buildVO(guideCode, state);
    }

    private void validateGuideCode(String guideCode) {
        if (!MtH5GuideCodeConstant.isSupported(guideCode)) {
            throw new BusinessException(400, "不支持的引导类型");
        }
    }

    private MtH5GuideStateVO buildVO(String guideCode, MtH5GuideState state) {
        MtH5GuideStateVO vo = new MtH5GuideStateVO();
        vo.setGuideCode(guideCode);
        vo.setCompleted(Boolean.TRUE.equals(state == null ? null : state.getCompleted()));
        vo.setCompletedAt(state == null ? null : state.getCompletedAt());
        return vo;
    }
}

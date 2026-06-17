package com.gogoga.backend.service;

import com.gogoga.backend.client.result.PromotionBidOpenApiResult;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionBidConstraint;
import com.gogoga.backend.mapper.MtPromotionBidConstraintMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionBidConstraintServiceTest {

    @Mock
    private MtPromotionBidConstraintMapper mtPromotionBidConstraintMapper;

    @Test
    void shouldUseLowerActiveShopMaxBid() {
        MtPromotionBidConstraintService service = new MtPromotionBidConstraintService(mtPromotionBidConstraintMapper);
        LocalDateTime now = LocalDateTime.of(2026, 5, 10, 10, 0);
        MtPromotionBidConstraint constraint = new MtPromotionBidConstraint();
        constraint.setMaxBidAmount(new BigDecimal("3.00"));
        when(mtPromotionBidConstraintMapper.selectActiveByShopId(eq(1L), eq(now))).thenReturn(constraint);

        BigDecimal effectiveMaxBidAmount = service.resolveEffectiveMaxBidAmount(1L, new BigDecimal("4.00"), now);

        assertEquals(new BigDecimal("3.00"), effectiveMaxBidAmount);
    }

    @Test
    void shouldParseAndStoreObservedMaxBidForSevenDays() {
        MtPromotionBidConstraintService service = new MtPromotionBidConstraintService(mtPromotionBidConstraintMapper);
        LocalDateTime observedAt = LocalDateTime.of(2026, 5, 10, 10, 0);
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1L);
        candidate.setPoiId("poi-1");
        PromotionBidOpenApiResult result = new PromotionBidOpenApiResult();
        result.setErrorMsg("出价不能高于3.0元");
        result.setPayloadJson("{\"response\":\"MeituanResponse{code='1', msg='出价不能高于3.0元'}\"}");

        Optional<BigDecimal> parsedMaxBidAmount = service.observeMaxBidLimitFromFailure(candidate, result, observedAt);

        assertTrue(parsedMaxBidAmount.isPresent());
        assertEquals(new BigDecimal("3.00"), parsedMaxBidAmount.get());
        ArgumentCaptor<MtPromotionBidConstraint> captor = ArgumentCaptor.forClass(MtPromotionBidConstraint.class);
        verify(mtPromotionBidConstraintMapper).upsert(captor.capture());
        MtPromotionBidConstraint constraint = captor.getValue();
        assertEquals(1L, constraint.getShopId());
        assertEquals("poi-1", constraint.getPoiId());
        assertEquals(new BigDecimal("3.00"), constraint.getMaxBidAmount());
        assertEquals("MEITUAN_ERROR", constraint.getSource());
        assertEquals(observedAt, constraint.getObservedAt());
        assertEquals(observedAt.plusDays(7), constraint.getExpireAt());
    }
}

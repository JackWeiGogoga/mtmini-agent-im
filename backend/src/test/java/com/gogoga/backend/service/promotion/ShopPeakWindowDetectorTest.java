package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtOrderHourlyAggregate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopPeakWindowDetectorTest {

    private final ShopPeakWindowDetector detector = new ShopPeakWindowDetector();

    @Test
    void shouldMergeContinuousCandidateHoursIntoPeakWindows() {
        List<ShopPeakWindow> windows = detector.detect(List.of(
                aggregate(9, 2),
                aggregate(10, 5),
                aggregate(11, 6),
                aggregate(12, 1),
                aggregate(18, 4),
                aggregate(19, 5)
        ));

        assertEquals(2, windows.size());
        assertEquals("10:00-12:00", windows.get(0).toDisplayText());
        assertEquals("18:00-20:00", windows.get(1).toDisplayText());
    }

    private MtOrderHourlyAggregate aggregate(int hourBucket, int orderCount) {
        MtOrderHourlyAggregate aggregate = new MtOrderHourlyAggregate();
        aggregate.setHourBucket(hourBucket);
        aggregate.setOrderCount(orderCount);
        aggregate.setIncomeAmount(BigDecimal.TEN);
        return aggregate;
    }
}

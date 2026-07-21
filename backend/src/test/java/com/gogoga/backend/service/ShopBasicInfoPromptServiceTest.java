package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtShop;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBasicInfoPromptServiceTest {

    private final ShopBasicInfoPromptService service = new ShopBasicInfoPromptService();

    private MtShop shop(String address, String shippingTime) {
        MtShop shop = new MtShop();
        shop.setAddress(address);
        shop.setShippingTime(shippingTime);
        return shop;
    }

    @Test
    void buildsBlockWithBothFields() {
        String block = service.build(shop("上海市徐汇区漕溪北路 100 号", "09:00-22:00"));

        assertTrue(block.contains("- 门店地址：上海市徐汇区漕溪北路 100 号"));
        assertTrue(block.contains("- 营业时间：09:00-22:00"));
        assertTrue(block.contains("以平台页面展示为准"));
    }

    @Test
    void omitsMissingFieldRatherThanWritingPlaceholder() {
        String block = service.build(shop("上海市徐汇区漕溪北路 100 号", null));

        assertTrue(block.contains("- 门店地址："));
        // 写“营业时间：无”会被模型读成这家店不营业，只能整行省略（块尾说明里提到营业时间不算）
        assertFalse(block.contains("- 营业时间："));
    }

    @Test
    void returnsEmptyWhenNoFieldUsable() {
        assertEquals("", service.build(shop(null, "   ")));
        assertEquals("", service.build(null));
    }

    @Test
    void flattensNewlinesFromSyncedData() {
        // 同步字段进入 system 消息，换行会被模型读成新的指令行
        String block = service.build(shop("上海市\n徐汇区\r\n漕溪北路", "09:00-22:00"));

        assertTrue(block.contains("- 门店地址：上海市 徐汇区 漕溪北路"));
    }

    @Test
    void truncatesOverlongField() {
        String block = service.build(shop("很长的地址".repeat(50), null));

        assertTrue(block.contains("…"));
        assertTrue(block.length() < 400);
    }

    @Test
    void mergeKeepsBothBlocksAndToleratesBlankSides() {
        assertEquals("门店信息\n\n知识片段", service.merge("门店信息", "知识片段"));
        assertEquals("知识片段", service.merge("", "知识片段"));
        assertEquals("门店信息", service.merge("门店信息", null));
        assertEquals("", service.merge(null, null));
    }
}

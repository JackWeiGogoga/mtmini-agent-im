package com.gogoga.backend.service.sms;

import com.gogoga.backend.entity.mt.MtShop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopContactPhoneParserTest {

    private final ShopContactPhoneParser parser = new ShopContactPhoneParser();

    @Test
    void shouldMergeExtractNormalizeAndDeduplicateAllShopMobiles() {
        MtShop shop = new MtShop();
        shop.setContactPhone("19355654200/13858611082；座机 0571-88888888");
        shop.setPhone("+86 193-5565-4200，139 1234 5678，13858611082");

        assertEquals(
                List.of("19355654200", "13858611082", "13912345678"),
                parser.parse(shop)
        );
    }

    @Test
    void shouldIgnoreLandlineServiceNumberForeignNumberExtensionAndInvalidText() {
        List<String> phones = parser.parse(
                "0571-88888888, 400-800-1234, +852 13800138000, 13800138000转123, 13800138000-123",
                "12345678901, 12800138000, 138001380001"
        );

        assertEquals(List.of(), phones);
    }

    @Test
    void shouldNotExtractMobileLookingSuffixFromLandlineLikeNumber() {
        assertEquals(List.of(), parser.parse("0571-13800138000", null));
    }

    @Test
    void shouldSupportCountryCodeAndMixedSeparators() {
        assertEquals(
                List.of("13800138000", "13900139000", "18800188000"),
                parser.parse("8613800138000／+86\t13900139000", "联系人：188-0018-8000\n备用")
        );
    }
}

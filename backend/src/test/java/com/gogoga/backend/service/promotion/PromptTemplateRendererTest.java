package com.gogoga.backend.service.promotion;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    void shouldRenderSuccessPromptTemplateWithProvidedValues() {
        String content = renderer.render("prompts/promotion-log-success-prompt.txt", Map.ofEntries(
                Map.entry("execute_status", "SUCCESS"),
                Map.entry("status_text", "自动调价执行成功"),
                Map.entry("decision_reason_text", "综合分析当前推广情况，保持当前出价"),
                Map.entry("error_msg", "无"),
                Map.entry("current_date", "2026-04-08"),
                Map.entry("current_time", "12:30"),
                Map.entry("current_weekday", "星期三"),
                Map.entry("current_day_type", "工作日"),
                Map.entry("shop_shipping_time", "09:00-22:00"),
                Map.entry("peak_window_prompt_section", "## 门店高峰期参考\n按工作日画像参考，近21天样本8天、126单，历史高峰时段11:00-13:00、17:00-19:00\n当前时段属于历史高峰时段（11:00-13:00）\n"),
                Map.entry("7d_exposure_count", "700"),
                Map.entry("7d_click_count", "90"),
                Map.entry("7d_promotion_cost", "56"),
                Map.entry("lastweek_hour_summary", "上周同小时时段付费曝光量18次，点击量2次，推广花费1.5元\n"),
                Map.entry("historical_compare_hint", "和上周同小时"),
                Map.entry("yesterday_hour_exposure_count", "20"),
                Map.entry("yesterday_hour_click_count", "3"),
                Map.entry("yesterday_hour_promotion_cost", "1.8"),
                Map.entry("window_exposure_count", "120"),
                Map.entry("window_click_count", "16"),
                Map.entry("window_order_count", "6"),
                Map.entry("window_promotion_cost", "3.3"),
                Map.entry("current_hour_exposure_count", "25"),
                Map.entry("current_hour_click_count", "4"),
                Map.entry("current_hour_order_count", "2"),
                Map.entry("current_hour_promotion_cost", "2.2"),
                Map.entry("bid", "0.4")
        ));

        assertTrue(content.contains("近7天累计付费曝光量700次"));
        assertTrue(content.contains("## 门店高峰期参考"));
        assertTrue(content.contains("当前推广窗口累计付费曝光120次"));
        assertTrue(content.contains("当前小时截止当前付费曝光25次"));
        assertTrue(content.contains("0.4元竞价"));
    }

    @Test
    void shouldRenderExceptionPromptTemplateWithProvidedValues() {
        String content = renderer.render("prompts/promotion-log-exception-prompt.txt", Map.ofEntries(
                Map.entry("execute_status", "SKIPPED"),
                Map.entry("status_text", "当前时段未执行自动调价"),
                Map.entry("decision_reason_text", "今日美团推广预算已用尽，本槽位已跳过自动调价"),
                Map.entry("error_msg", "无"),
                Map.entry("current_date", "2026-04-08"),
                Map.entry("current_time", "12:30"),
                Map.entry("current_weekday", "星期三"),
                Map.entry("current_day_type", "工作日"),
                Map.entry("shop_shipping_time", "09:00-22:00"),
                Map.entry("peak_window_prompt_section", ""),
                Map.entry("7d_exposure_count", "700"),
                Map.entry("7d_click_count", "90"),
                Map.entry("7d_promotion_cost", "56"),
                Map.entry("lastweek_hour_summary", "上周同小时时段付费曝光量18次，点击量2次，推广花费1.5元\n"),
                Map.entry("historical_compare_hint", "和上周同小时"),
                Map.entry("yesterday_hour_exposure_count", "20"),
                Map.entry("yesterday_hour_click_count", "3"),
                Map.entry("yesterday_hour_promotion_cost", "1.8"),
                Map.entry("window_exposure_count", "120"),
                Map.entry("window_click_count", "16"),
                Map.entry("window_order_count", "6"),
                Map.entry("window_promotion_cost", "3.3"),
                Map.entry("current_hour_exposure_count", "25"),
                Map.entry("current_hour_click_count", "4"),
                Map.entry("current_hour_order_count", "2"),
                Map.entry("current_hour_promotion_cost", "2.2"),
                Map.entry("bid", "0")
        ));

        assertTrue(content.contains("执行状态SKIPPED"));
        assertTrue(content.contains("不要输出“出价分析”"));
        assertTrue(content.contains("0元竞价"));
    }

    @Test
    void shouldThrowWhenPlaceholderIsMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> renderer.render("prompts/promotion-log-success-prompt.txt", Map.of()));
    }

    @Test
    void shouldThrowWhenRenderedPromptStillContainsPlaceholder() {
        assertThrows(IllegalStateException.class,
                () -> renderer.render("prompts/promotion-log-exception-prompt.txt", Map.ofEntries(
                        Map.entry("execute_status", "未执行"),
                        Map.entry("status_text", "当前时段未执行自动调价"),
                        Map.entry("decision_reason_text", "${internal_reason}"),
                        Map.entry("error_msg", "无"),
                        Map.entry("current_date", "2026-04-08"),
                        Map.entry("current_time", "12:30"),
                        Map.entry("current_weekday", "星期三"),
                        Map.entry("current_day_type", "工作日"),
                        Map.entry("shop_shipping_time", "09:00-22:00"),
                        Map.entry("peak_window_prompt_section", ""),
                        Map.entry("7d_exposure_count", "700"),
                        Map.entry("7d_click_count", "90"),
                        Map.entry("7d_promotion_cost", "56"),
                        Map.entry("lastweek_hour_summary", ""),
                        Map.entry("historical_compare_hint", ""),
                        Map.entry("yesterday_hour_exposure_count", "20"),
                        Map.entry("yesterday_hour_click_count", "3"),
                        Map.entry("yesterday_hour_promotion_cost", "1.8"),
                        Map.entry("window_exposure_count", "120"),
                        Map.entry("window_click_count", "16"),
                        Map.entry("window_order_count", "6"),
                        Map.entry("window_promotion_cost", "3.3"),
                        Map.entry("current_hour_exposure_count", "25"),
                        Map.entry("current_hour_click_count", "4"),
                        Map.entry("current_hour_order_count", "2"),
                        Map.entry("current_hour_promotion_cost", "2.2"),
                        Map.entry("bid", "0")
                )));
    }
}

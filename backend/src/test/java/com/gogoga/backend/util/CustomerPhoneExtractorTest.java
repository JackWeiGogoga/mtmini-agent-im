package com.gogoga.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerPhoneExtractorTest {

    @Test
    @DisplayName("识别顾客消息中的手机号并去掉分隔符")
    void shouldExtractMobileWithSeparators() {
        assertThat(CustomerPhoneExtractor.extract("我的电话是13800138000，麻烦送到门口"))
                .containsExactly("13800138000");
        assertThat(CustomerPhoneExtractor.extract("打138-0013-8000联系我"))
                .containsExactly("13800138000");
        assertThat(CustomerPhoneExtractor.extract("138 0013 8000"))
                .containsExactly("13800138000");
    }

    @Test
    @DisplayName("同一号码的不同写法只展示一次，多个号码按出现顺序保留")
    void shouldDeduplicateByDigits() {
        assertThat(CustomerPhoneExtractor.extract("13800138000 或 138-0013-8000 都行，备用 13912345678"))
                .containsExactly("13800138000", "13912345678");
    }

    @Test
    @DisplayName("订单号等长数字串不能被截出手机号")
    void shouldNotMatchInsideLongerDigitSequence() {
        // 订单号内部含有 13800138000 片段，边界保护必须挡住
        assertThat(CustomerPhoneExtractor.extract("订单号 999138001380009999")).isEmpty();
        assertThat(CustomerPhoneExtractor.extract("1380013800")).isEmpty();
        assertThat(CustomerPhoneExtractor.extract("138001380001")).isEmpty();
    }

    @Test
    @DisplayName("非手机号段与空正文返回空列表")
    void shouldIgnoreNonMobile() {
        assertThat(CustomerPhoneExtractor.extract("座机 057112345678")).isEmpty();
        assertThat(CustomerPhoneExtractor.extract("12345678901")).isEmpty();
        assertThat(CustomerPhoneExtractor.extract("  ")).isEmpty();
        assertThat(CustomerPhoneExtractor.extract(null)).isEmpty();
    }

    @Test
    @DisplayName("返回值可安全用于只读遍历")
    void shouldReturnListForEmptyCase() {
        List<String> phones = CustomerPhoneExtractor.extract("没有号码");
        assertThat(phones).isNotNull().isEmpty();
    }
}

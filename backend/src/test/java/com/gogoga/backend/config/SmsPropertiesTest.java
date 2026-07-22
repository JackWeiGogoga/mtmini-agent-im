package com.gogoga.backend.config;

import com.gogoga.backend.enums.BusinessSmsDeliveryModeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsPropertiesTest {

    @Test
    void shouldAllowLogOnlyBusinessSmsWhileLoginUsesRealGateway() {
        SmsProperties properties = new SmsProperties();
        properties.setMockEnabled(false);
        properties.getBusiness().setEnabled(true);
        properties.getBusiness().setDeliveryMode(BusinessSmsDeliveryModeEnum.LOG_ONLY);

        assertDoesNotThrow(properties::validateBusinessConfig);
    }

    @Test
    void shouldRequireTencentCredentialsWhenRealGatewayIsEnabled() {
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setEnabled(true);
        properties.getBusiness().setDeliveryMode(BusinessSmsDeliveryModeEnum.REAL);

        assertThrows(IllegalStateException.class, properties::validateBusinessConfig);
    }

    @Test
    void shouldRejectBlankBusinessTemplateWithoutFallingBackToLoginTemplate() {
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setEnabled(true);
        properties.getBusiness().setPowerExhaustedTemplateId(" ");
        properties.getTencent().setLoginTemplateId("login-template");

        assertThrows(IllegalStateException.class, properties::validateBusinessConfig);
    }

    @Test
    void shouldRejectPlaceholderTencentCredentialsWhenBusinessSmsIsEnabled() {
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setEnabled(true);
        properties.getBusiness().setDeliveryMode(BusinessSmsDeliveryModeEnum.REAL);
        properties.getTencent().setSecretId("your-secret-id");
        properties.getTencent().setSecretKey("your-secret-key");
        properties.getTencent().setSdkAppId("1400000000");
        properties.getTencent().setSignName("your-sign-name");

        assertThrows(IllegalStateException.class, properties::validateBusinessConfig);
    }

    @Test
    void shouldRequireBusinessDeliveryModeWhenBusinessSmsIsEnabled() {
        SmsProperties properties = new SmsProperties();
        properties.getBusiness().setEnabled(true);
        properties.getBusiness().setDeliveryMode(null);

        assertThrows(IllegalStateException.class, properties::validateBusinessConfig);
    }
}

package com.gogoga.backend.client;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.service.async.MtApiDryRunException;
import com.meituan.sdk.CommonRequest;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MtDryRunMeituanClientTest {

    @Mock private MeituanClient delegate;
    @Mock private MeituanRequest<Object> request;
    @Mock private CommonRequest commonRequest;
    @Mock private Type responseType;

    private MtProperties properties;
    private MtDryRunMeituanClient client;

    @BeforeEach
    void setUp() {
        properties = new MtProperties();
        client = new MtDryRunMeituanClient(delegate, properties);
    }

    @Test
    void blocksEverySdkMethodByDefaultWithoutCallingDelegate() {
        assertThrows(MtApiDryRunException.class, () -> client.invokeApi(request));
        assertThrows(MtApiDryRunException.class, () -> client.invokeApi(request, "access-token"));
        assertThrows(MtApiDryRunException.class, () -> client.invokeApi(request, 0, 100));
        assertThrows(MtApiDryRunException.class, () -> client.invokeApi(request, "access-token", 0, 100));
        assertThrows(MtApiDryRunException.class, () -> client.commonInvokeApi(commonRequest, responseType));
        assertThrows(MtApiDryRunException.class, () -> client.getOAuthToken(16, "oauth-code"));
        assertThrows(MtApiDryRunException.class, () -> client.refreshToken(16, "refresh-token"));

        verifyNoInteractions(delegate);
    }

    @Test
    void remainsFailSafeWhenApiConfigurationIsMissing() {
        properties.setApi(null);

        assertThrows(MtApiDryRunException.class, () -> client.invokeApi(request));

        verifyNoInteractions(delegate);
    }

    @Test
    void delegatesEverySdkMethodWhenDryRunIsExplicitlyDisabled() throws Exception {
        properties.getApi().setDryRun(false);

        client.invokeApi(request);
        client.invokeApi(request, "access-token");
        client.invokeApi(request, 0, 100);
        client.invokeApi(request, "access-token", 0, 100);
        client.commonInvokeApi(commonRequest, responseType);
        client.getOAuthToken(16, "oauth-code");
        client.refreshToken(16, "refresh-token");

        verify(delegate).invokeApi(request);
        verify(delegate).invokeApi(request, "access-token");
        verify(delegate).invokeApi(request, 0, 100);
        verify(delegate).invokeApi(request, "access-token", 0, 100);
        verify(delegate).commonInvokeApi(commonRequest, responseType);
        verify(delegate).getOAuthToken(16, "oauth-code");
        verify(delegate).refreshToken(16, "refresh-token");
    }
}

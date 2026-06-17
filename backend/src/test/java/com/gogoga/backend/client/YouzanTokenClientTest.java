package com.gogoga.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.config.YouzanProperties;
import com.gogoga.backend.util.JsonUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouzanTokenClientTest {

    @Test
    void shouldPostJsonAndParseTokenResponseWithAbsoluteExpires() throws Exception {
        long expiresAtMillis = 1583720718116L;
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "success": true,
                              "code": 200,
                              "data": {
                                "expires": %d,
                                "scope": "beauty_order beauty_shop",
                                "access_token": "access-1",
                                "authority_id": "42587152",
                                "refresh_token": "refresh-1"
                              },
                              "message": null
                            }
                            """.formatted(expiresAtMillis)));

            YouzanTokenClient client = new YouzanTokenClient(properties(server));

            YouzanTokenResponse response = client.exchangeCode("code-1");

            assertTrue(response.isSuccess());
            assertEquals("200", response.getCode());
            assertEquals("access-1", response.getAccessToken());
            assertEquals("refresh-1", response.getRefreshToken());
            assertEquals("42587152", response.getAuthorityId());
            assertEquals(42587152L, response.getKdtId());
            assertEquals(expiresAtMillis, response.getExpiresAtMillis());

            RecordedRequest request = server.takeRequest();
            assertEquals("POST", request.getMethod());
            assertEquals("/auth/token", request.getPath());
            assertNotNull(request.getHeader("Content-Type"));
            assertTrue(request.getHeader("Content-Type").startsWith("application/json"));

            JsonNode requestBody = JsonUtil.readTree(request.getBody().readUtf8());
            assertEquals("authorization_code", requestBody.get("authorize_type").asText());
            assertEquals("client-id", requestBody.get("client_id").asText());
            assertEquals("client-secret", requestBody.get("client_secret").asText());
            assertEquals("code-1", requestBody.get("code").asText());
        }
    }

    @Test
    void shouldPostJsonForRefreshToken() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "success": true,
                              "code": 200,
                              "data": {
                                "expires": 1583720718116,
                                "access_token": "access-2",
                                "authority_id": "42587152",
                                "refresh_token": "refresh-2"
                              },
                              "message": null
                            }
                            """));

            YouzanTokenClient client = new YouzanTokenClient(properties(server));

            YouzanTokenResponse response = client.refreshToken("refresh-old");

            assertTrue(response.isSuccess());
            assertEquals("access-2", response.getAccessToken());
            assertEquals("refresh-2", response.getRefreshToken());

            RecordedRequest request = server.takeRequest();
            JsonNode requestBody = JsonUtil.readTree(request.getBody().readUtf8());
            assertEquals("refresh_token", requestBody.get("authorize_type").asText());
            assertEquals("refresh-old", requestBody.get("refresh_token").asText());
        }
    }

    @Test
    void shouldKeepLongScopeFromTokenResponse() throws Exception {
        String longScope = "scope_".repeat(400);
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "success": true,
                              "code": 200,
                              "data": {
                                "expires": 1583720718116,
                                "scope": "%s",
                                "access_token": "access-1",
                                "authority_id": "42587152",
                                "refresh_token": "refresh-1"
                              },
                              "message": null
                            }
                            """.formatted(longScope)));

            YouzanTokenClient client = new YouzanTokenClient(properties(server));

            YouzanTokenResponse response = client.exchangeCode("code-1");

            assertTrue(response.isSuccess());
            assertEquals(longScope, response.getScope());
        }
    }

    private YouzanProperties properties(MockWebServer server) {
        YouzanProperties properties = new YouzanProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setTokenUrl(server.url("/auth/token").toString());
        return properties;
    }
}

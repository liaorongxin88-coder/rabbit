package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class E2eApiClient {
    private final TestRestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public E2eApiClient(TestRestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public JsonNode getOk(String path, String token, Long houseId) {
        return expectOk(exchange(path, HttpMethod.GET, token, houseId, null));
    }

    public JsonNode postOk(String path, String token, Long houseId, Object body) {
        return expectOk(exchange(path, HttpMethod.POST, token, houseId, body));
    }

    public JsonNode postResponse(String path, String token, Long houseId, Object body) {
        return root(exchange(path, HttpMethod.POST, token, houseId, body));
    }

    public JsonNode postResponseWithHeaders(
            String path,
            String token,
            Long houseId,
            Object body,
            Map<String, String> extraHeaders
    ) {
        HttpHeaders headers = headers(token, houseId);
        extraHeaders.forEach(headers::set);
        return root(exchange(path, HttpMethod.POST, headers, body));
    }

    public JsonNode uploadImage(
            String path,
            String token,
            Long houseId,
            String fileName,
            byte[] bytes
    ) {
        HttpHeaders headers = headers(token, houseId);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + path,
                HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, Object>>(body, headers),
                String.class
        );
        return expectOk(response);
    }

    public JsonNode putOk(String path, String token, Long houseId, Object body) {
        return expectOk(exchange(path, HttpMethod.PUT, token, houseId, body));
    }

    public JsonNode putResponse(String path, String token, Long houseId, Object body) {
        return root(exchange(path, HttpMethod.PUT, token, houseId, body));
    }

    public JsonNode deleteOk(String path, String token, Long houseId) {
        return expectOk(exchange(path, HttpMethod.DELETE, token, houseId, null));
    }

    public JsonNode expectError(String path, HttpMethod method, String token, Long houseId, Object body, int code, String messagePart) {
        JsonNode root = root(exchange(path, method, token, houseId, body));
        Assertions.assertEquals(code, root.get("code").asInt(), "business code for " + method + " " + path);
        if (messagePart != null) {
            Assertions.assertTrue(root.get("message").asText().contains(messagePart),
                    "message should contain " + messagePart + " but was " + root.get("message").asText());
        }
        return root;
    }

    public Download download(String path, String token, Long houseId) {
        HttpHeaders headers = headers(token, houseId);
        ResponseEntity<byte[]> resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<Object>(headers), byte[].class);
        Assertions.assertTrue(resp.getStatusCode().is2xxSuccessful(), "HTTP status for download " + path);
        return new Download(resp.getHeaders().getContentType(), resp.getBody() == null ? new byte[0] : resp.getBody());
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token, Long houseId, Object body) {
        HttpHeaders headers = headers(token, houseId);
        return exchange(path, method, headers, body);
    }

    private ResponseEntity<String> exchange(
            String path,
            HttpMethod method,
            HttpHeaders headers,
            Object body
    ) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(baseUrl + path, method, new HttpEntity<Object>(body, headers), String.class);
    }

    private HttpHeaders headers(String token, Long houseId) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (houseId != null) {
            headers.set("X-House-Id", String.valueOf(houseId.longValue()));
        }
        return headers;
    }

    private JsonNode expectOk(ResponseEntity<String> resp) {
        JsonNode root = root(resp);
        Assertions.assertEquals(0, root.get("code").asInt(), "business code, body=" + resp.getBody());
        return root.get("data");
    }

    private JsonNode root(ResponseEntity<String> resp) {
        Assertions.assertTrue(resp.getStatusCode().is2xxSuccessful(), "HTTP status, body=" + resp.getBody());
        try {
            JsonNode root = mapper.readTree(resp.getBody());
            Assertions.assertTrue(root.has("code"), "response should contain code: " + resp.getBody());
            Assertions.assertTrue(root.has("message"), "response should contain message: " + resp.getBody());
            return root;
        } catch (IOException e) {
            throw new AssertionError("invalid JSON response: " + resp.getBody(), e);
        }
    }

    public static class Download {
        public final MediaType contentType;
        public final byte[] bytes;

        Download(MediaType contentType, byte[] bytes) {
            this.contentType = contentType;
            this.bytes = bytes;
        }

        public String utf8() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}

package com.rabbit.app.modules.hardware.gateway;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpHardwareGateway implements HardwareGateway {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String token;

    public HttpHardwareGateway(RestTemplate restTemplate, String baseUrl, String token) {
        this.restTemplate = restTemplate;
        this.baseUrl = trimSlash(baseUrl);
        this.token = token == null ? "" : token.trim();
    }

    @Override
    public void aphrodisiacStart(Long houseId, Long batchId, List<Long> rabbitIds) {
        post("/aphrodisiac/start", houseId, batchId, rabbitIds);
    }

    @Override
    public void aphrodisiacFinish(Long houseId, Long batchId, List<Long> rabbitIds) {
        post("/aphrodisiac/finish", houseId, batchId, rabbitIds);
    }

    @Override
    public String getType() {
        return "http";
    }

    private void post(String path, Long houseId, Long batchId, List<Long> rabbitIds) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new RuntimeException("hardware gateway baseUrl missing");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!token.isEmpty()) {
            headers.add("X-Token", token);
        }
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("houseId", houseId);
        body.put("batchId", batchId);
        body.put("rabbitIds", rabbitIds);
        HttpEntity<Map<String, Object>> req = new HttpEntity<Map<String, Object>>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + path, req, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("hardware gateway http " + resp.getStatusCodeValue());
        }
        Map m = resp.getBody();
        if (m != null && m.get("code") != null) {
            try {
                int code = Integer.parseInt(String.valueOf(m.get("code")));
                if (code != 0) {
                    Object msg = m.get("message");
                    throw new RuntimeException("hardware gateway code=" + code + " msg=" + (msg == null ? "" : String.valueOf(msg)));
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception ignored) {
            }
        }
    }

    private String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}


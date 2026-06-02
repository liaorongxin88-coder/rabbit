package com.rabbit.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WechatService {
    private final boolean enabled;
    private final String appid;
    private final String secret;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public WechatService(
            @Value("${app.wechat.enabled:false}") boolean enabled,
            @Value("${app.wechat.appid:}") String appid,
            @Value("${app.wechat.secret:}") String secret
    ) {
        this.enabled = enabled;
        this.appid = appid;
        this.secret = secret;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    public String codeToOpenid(String code) {
        if (!enabled) {
            throw new BizException(410, "微信登录未启用");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new BizException(400, "code不能为空");
        }
        if (appid == null || appid.trim().isEmpty() || secret == null || secret.trim().isEmpty()) {
            throw new BizException(500, "微信登录未配置appid/secret");
        }
        try {
            String c = URLEncoder.encode(code.trim(), StandardCharsets.UTF_8.name());
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + URLEncoder.encode(appid.trim(), StandardCharsets.UTF_8.name())
                    + "&secret=" + URLEncoder.encode(secret.trim(), StandardCharsets.UTF_8.name())
                    + "&js_code=" + c
                    + "&grant_type=authorization_code";
            String body = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                String msg = node.has("errmsg") ? node.get("errmsg").asText() : "wx error";
                throw new BizException(400, "微信登录失败:" + msg);
            }
            String openid = node.has("openid") ? node.get("openid").asText() : null;
            if (openid == null || openid.trim().isEmpty()) {
                throw new BizException(400, "微信登录失败:openid缺失");
            }
            return openid;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "微信登录异常:" + e.getMessage());
        }
    }
}


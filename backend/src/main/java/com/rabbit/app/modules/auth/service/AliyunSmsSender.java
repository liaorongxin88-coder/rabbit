package com.rabbit.app.modules.auth.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AliyunSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);
    private static final Pattern TEMPLATE_PARAM_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,31}$");

    private final boolean enabled;
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String signName;
    private final String templateCode;
    private final String templateParamName;
    private final ObjectMapper objectMapper;
    private volatile Client client;

    public AliyunSmsSender(
            @Value("${app.sms.enabled:false}") boolean enabled,
            @Value("${app.sms.aliyun.endpoint:dysmsapi.aliyuncs.com}") String endpoint,
            @Value("${app.sms.aliyun.access-key-id:}") String accessKeyId,
            @Value("${app.sms.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${app.sms.aliyun.sign-name:}") String signName,
            @Value("${app.sms.aliyun.template-code:}") String templateCode,
            @Value("${app.sms.aliyun.template-param-name:code}") String templateParamName,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.signName = signName;
        this.templateCode = templateCode;
        this.templateParamName = templateParamName;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendVerificationCode(String phone, String code) throws Exception {
        validateConfiguration();
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam(templateParams(code));
        try {
            SendSmsResponse response = client().sendSms(request);
            if (response == null || response.getBody() == null
                    || !Objects.equals("OK", response.getBody().getCode())) {
                String responseCode = response == null || response.getBody() == null
                        ? "EMPTY_RESPONSE" : response.getBody().getCode();
                String requestId = response == null || response.getBody() == null
                        ? null : response.getBody().getRequestId();
                log.warn("Aliyun SMS rejected request: code={}, requestId={}", responseCode, requestId);
                throw new BizException(502, "验证码发送失败，请稍后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Aliyun SMS request failed: {}", e.getClass().getSimpleName());
            throw new BizException(502, "验证码发送失败，请稍后重试");
        }
    }

    private Client client() throws Exception {
        Client current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                Config config = new Config()
                        .setAccessKeyId(accessKeyId)
                        .setAccessKeySecret(accessKeySecret)
                        .setEndpoint(endpoint);
                client = new Client(config);
            }
            return client;
        }
    }

    private String templateParams(String code) {
        try {
            return objectMapper.writeValueAsString(Map.of(templateParamName, code));
        } catch (JsonProcessingException e) {
            throw new BizException(500, "短信模板参数生成失败");
        }
    }

    private void validateConfiguration() {
        if (!enabled) {
            throw new BizException(503, "短信登录暂未启用");
        }
        if (isBlank(accessKeyId) || isBlank(accessKeySecret) || isBlank(signName) || isBlank(templateCode)) {
            throw new BizException(503, "短信服务配置不完整");
        }
        if (!TEMPLATE_PARAM_NAME.matcher(templateParamName).matches()) {
            throw new BizException(503, "短信模板变量名配置不正确");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

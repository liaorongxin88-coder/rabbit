package com.rabbit.app.modules.auth.service;

import com.rabbit.app.config.ApplicationSecretValidator;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PhoneIdentityService {
    private final String hashSecret;

    public PhoneIdentityService(
            @Value("${app.auth.phone-hash-secret:}") String hashSecret
    ) {
        ApplicationSecretValidator.requireConfigured("APP_PHONE_HASH_SECRET", hashSecret);
        this.hashSecret = hashSecret;
    }

    public String hash(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成手机号摘要", e);
        }
    }

    public String mask(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

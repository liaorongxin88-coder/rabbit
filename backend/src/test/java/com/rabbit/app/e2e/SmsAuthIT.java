package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.auth.service.SmsSender;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.mockito.Mockito;

public class SmsAuthIT extends E2eTestSupport {
    @MockBean
    private SmsSender smsSender;

    @Test
    void verifiedPhoneCreatesAnAccountAndCodeCannotBeReused() throws Exception {
        AtomicReference<String> sentCode = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            sentCode.set(invocation.getArgument(1, String.class));
            return null;
        }).when(smsSender).sendVerificationCode(Mockito.eq("13800138000"), Mockito.anyString());

        JsonNode delivery = api.postOk("/api/auth/sms/code", null, null, obj(
                "phone", "+86 13800138000"
        ));
        Assertions.assertEquals(300, delivery.get("expiresInSeconds").asInt());
        Assertions.assertEquals(60, delivery.get("retryAfterSeconds").asInt());
        Assertions.assertNotNull(sentCode.get());

        JsonNode auth = api.postOk("/api/auth/sms/login", null, null, obj(
                "phone", "13800138000",
                "code", sentCode.get()
        ));
        Assertions.assertTrue(auth.get("userId").asLong() > 0);
        Assertions.assertFalse(auth.get("token").asText().isBlank());
        Assertions.assertTrue(auth.get("phoneBound").asBoolean());
        Assertions.assertEquals("138****8000", auth.get("maskedPhone").asText());

        JsonNode profile = api.getOk("/api/auth/me", auth.get("token").asText(), null);
        Assertions.assertEquals(auth.get("userId").asLong(), profile.get("userId").asLong());
        Assertions.assertTrue(profile.get("phoneBound").asBoolean());
        Assertions.assertEquals("138****8000", profile.get("maskedPhone").asText());

        api.expectError("/api/auth/sms/login", HttpMethod.POST, null, null, obj(
                "phone", "13800138000",
                "code", sentCode.get()
        ), 400, "验证码无效或已过期");
    }
}

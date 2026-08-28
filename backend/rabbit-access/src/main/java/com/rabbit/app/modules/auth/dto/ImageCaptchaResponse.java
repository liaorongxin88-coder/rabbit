package com.rabbit.app.modules.auth.dto;

public record ImageCaptchaResponse(
        String captchaId,
        String imageBase64,
        int expiresInSeconds
) {
}

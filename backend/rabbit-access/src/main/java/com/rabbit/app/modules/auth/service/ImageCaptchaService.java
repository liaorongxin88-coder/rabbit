package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.config.ApplicationSecretValidator;
import com.rabbit.app.modules.auth.dto.ImageCaptchaResponse;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageCaptchaService {
    private static final Logger log = LoggerFactory.getLogger(ImageCaptchaService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final ImageCaptchaStore store;
    private final boolean enabled;
    private final String codeSecret;
    private final int codeLength;
    private final int ttlSeconds;
    private final int maxAttempts;
    private final Supplier<String> codeGenerator;

    @Autowired
    public ImageCaptchaService(
            ImageCaptchaStore store,
            @Value("${app.captcha.enabled:true}") boolean enabled,
            @Value("${app.captcha.code-secret:}") String codeSecret,
            @Value("${app.captcha.code-length:4}") int codeLength,
            @Value("${app.captcha.ttl-seconds:300}") int ttlSeconds,
            @Value("${app.captcha.max-attempts:5}") int maxAttempts
    ) {
        this(store, enabled, codeSecret, codeLength, ttlSeconds, maxAttempts,
                () -> randomCode(codeLength));
    }

    ImageCaptchaService(
            ImageCaptchaStore store,
            boolean enabled,
            String codeSecret,
            int codeLength,
            int ttlSeconds,
            int maxAttempts,
            Supplier<String> codeGenerator
    ) {
        this.store = store;
        this.enabled = enabled;
        this.codeSecret = codeSecret;
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
        this.maxAttempts = maxAttempts;
        this.codeGenerator = codeGenerator;
        validateSettings();
    }

    public ImageCaptchaResponse issue() {
        requireEnabled();
        String captchaId = randomId();
        String code = codeGenerator.get();
        if (code == null || !code.matches("[A-HJ-NP-Z2-9]{" + codeLength + "}")) {
            throw new IllegalStateException("图片验证码生成器返回了无效结果");
        }
        try {
            store.issue(captchaId, hashCode(captchaId, code), Duration.ofSeconds(ttlSeconds));
        } catch (ImageCaptchaStoreUnavailableException unavailable) {
            throw cacheUnavailable(unavailable);
        }
        return new ImageCaptchaResponse(captchaId, renderBase64(code), ttlSeconds);
    }

    public void verifyAndConsume(String rawCaptchaId, String rawCode) {
        if (!enabled) {
            return;
        }
        String captchaId = normalizeCaptchaId(rawCaptchaId);
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-HJ-NP-Z2-9]{" + codeLength + "}")) {
            throw new BizException(400, "请输入" + codeLength + "位图片验证码");
        }
        ImageCaptchaStore.VerificationResult result;
        try {
            result = store.verifyAndConsume(captchaId, hashCode(captchaId, code), maxAttempts);
        } catch (ImageCaptchaStoreUnavailableException unavailable) {
            throw cacheUnavailable(unavailable);
        }
        if (result == ImageCaptchaStore.VerificationResult.MISSING) {
            throw new BizException(400, "图片验证码无效或已过期，请刷新后重试");
        }
        if (result == ImageCaptchaStore.VerificationResult.WRONG
                || result == ImageCaptchaStore.VerificationResult.LOCKED) {
            throw new BizException(400, "图片验证码错误，请刷新后重试");
        }
    }

    private BizException cacheUnavailable(ImageCaptchaStoreUnavailableException unavailable) {
        log.warn("Image captcha cache unavailable: error={}", unavailable.getClass().getSimpleName());
        return new BizException(503, "图片验证码服务暂不可用，请稍后重试");
    }

    private String normalizeCaptchaId(String rawCaptchaId) {
        String captchaId = rawCaptchaId == null ? "" : rawCaptchaId.trim();
        if (!captchaId.matches("[0-9a-f]{32}")) {
            throw new BizException(400, "图片验证码无效或已过期，请刷新后重试");
        }
        return captchaId;
    }

    private String hashCode(String captchaId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    ("CAPTCHA:" + captchaId + ":" + code).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception error) {
            throw new IllegalStateException("无法生成图片验证码摘要", error);
        }
    }

    private String renderBase64(String code) {
        BufferedImage image = new BufferedImage(132, 44, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setStroke(new BasicStroke(1.2f));
            for (int index = 0; index < 5; index++) {
                graphics.setColor(new Color(130 + SECURE_RANDOM.nextInt(70), 130 + SECURE_RANDOM.nextInt(70),
                        130 + SECURE_RANDOM.nextInt(70)));
                graphics.drawLine(0, SECURE_RANDOM.nextInt(image.getHeight()), image.getWidth(),
                        SECURE_RANDOM.nextInt(image.getHeight()));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
            for (int index = 0; index < code.length(); index++) {
                AffineTransform originalTransform = graphics.getTransform();
                graphics.rotate((SECURE_RANDOM.nextDouble() - 0.5) * 0.35, 16 + index * 25, 27);
                graphics.setColor(new Color(30 + SECURE_RANDOM.nextInt(70), 55 + SECURE_RANDOM.nextInt(70),
                        80 + SECURE_RANDOM.nextInt(70)));
                graphics.drawString(String.valueOf(code.charAt(index)), 12 + index * 27,
                        31 + SECURE_RANDOM.nextInt(7) - 3);
                graphics.setTransform(originalTransform);
            }
            for (int index = 0; index < 24; index++) {
                graphics.setColor(new Color(130 + SECURE_RANDOM.nextInt(100), 130 + SECURE_RANDOM.nextInt(100),
                        130 + SECURE_RANDOM.nextInt(100)));
                graphics.fillRect(SECURE_RANDOM.nextInt(image.getWidth()), SECURE_RANDOM.nextInt(image.getHeight()), 1, 1);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG writer is unavailable");
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("无法生成图片验证码", error);
        } finally {
            graphics.dispose();
        }
    }

    private void validateSettings() {
        if (enabled) {
            ApplicationSecretValidator.requireConfigured("APP_CAPTCHA_CODE_SECRET", codeSecret);
        }
        if (codeLength < 4 || codeLength > 8 || ttlSeconds <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("图片验证码参数配置不正确");
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BizException(503, "图片验证码暂未启用");
        }
    }

    private static String randomId() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            code.append(CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}

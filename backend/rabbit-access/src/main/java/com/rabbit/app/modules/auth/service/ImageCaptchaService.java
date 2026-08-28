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
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
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

    /**
     * 出图按 3 倍分辨率绘制。admin 展示框 128x36 CSS px、app 展示框 132x52 逻辑 px，
     * 3 倍屏上分别对应 384 和 396 物理像素，1 倍图会被拉糊。
     */
    private static final int RENDER_SCALE = 3;
    private static final int IMAGE_WIDTH = 132 * RENDER_SCALE;
    private static final int IMAGE_HEIGHT = 44 * RENDER_SCALE;
    private static final int SIDE_PADDING = 8 * RENDER_SCALE;
    private static final double MAX_TILT_RADIANS = 0.30;

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
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 27 * RENDER_SCALE);
            graphics.setFont(font);

            // 按每个字的真实宽度排版。固定步长会让 M（最宽）挤住下一个字、而 2（最窄）空出一大块。
            int count = code.length();
            double[] advance = new double[count];
            double glyphTotal = 0;
            for (int index = 0; index < count; index++) {
                Rectangle2D bounds = font.getStringBounds(
                        String.valueOf(code.charAt(index)), graphics.getFontRenderContext());
                advance[index] = bounds.getWidth();
                glyphTotal += advance[index];
            }
            double tracking = count > 1
                    ? Math.max(2.0 * RENDER_SCALE,
                            (IMAGE_WIDTH - SIDE_PADDING * 2 - glyphTotal) / (count - 1))
                    : 0;
            double penX = (IMAGE_WIDTH - (glyphTotal + tracking * (count - 1))) / 2.0;

            // 垂直居中按大写字母的视觉高度算。ascent 含重音区，拿它居中会让字整体偏上。
            double capHeight = font.createGlyphVector(graphics.getFontRenderContext(), "H")
                    .getVisualBounds().getHeight();
            double baseY = (IMAGE_HEIGHT + capHeight) / 2.0;
            double maxJitter = Math.min(2.5 * RENDER_SCALE,
                    Math.max(0, (IMAGE_HEIGHT - capHeight) / 2.0 - 2.0 * RENDER_SCALE));

            for (int index = 0; index < count; index++) {
                AffineTransform originalTransform = graphics.getTransform();
                double jitterY = (SECURE_RANDOM.nextDouble() - 0.5) * 2 * maxJitter;
                // 绕这个字自己的中心转。支点若按固定步长推进，会和绘制位置逐字错开，越靠右偏得越多。
                double centerX = penX + advance[index] / 2.0;
                double centerY = baseY + jitterY - capHeight / 2.0;
                graphics.rotate((SECURE_RANDOM.nextDouble() - 0.5) * MAX_TILT_RADIANS, centerX, centerY);
                graphics.setColor(new Color(25 + SECURE_RANDOM.nextInt(45), 45 + SECURE_RANDOM.nextInt(50),
                        90 + SECURE_RANDOM.nextInt(60)));
                graphics.drawString(String.valueOf(code.charAt(index)), (float) penX,
                        (float) (baseY + jitterY));
                graphics.setTransform(originalTransform);
                penX += advance[index] + tracking;
            }

            // 干扰用贯穿画布的曲线。等长直线看起来像尺子画的格线，对识别的干扰也更小。
            graphics.setStroke(new BasicStroke(1.4f * RENDER_SCALE, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            for (int index = 0; index < 3; index++) {
                graphics.setColor(new Color(110 + SECURE_RANDOM.nextInt(60), 130 + SECURE_RANDOM.nextInt(60),
                        155 + SECURE_RANDOM.nextInt(60), 165));
                double startY = IMAGE_HEIGHT * (0.15 + SECURE_RANDOM.nextDouble() * 0.7);
                double endY = IMAGE_HEIGHT * (0.15 + SECURE_RANDOM.nextDouble() * 0.7);
                Path2D.Double curve = new Path2D.Double();
                curve.moveTo(-RENDER_SCALE, startY);
                curve.curveTo(
                        IMAGE_WIDTH * 0.3, startY + (SECURE_RANDOM.nextDouble() - 0.5) * IMAGE_HEIGHT * 0.9,
                        IMAGE_WIDTH * 0.7, endY + (SECURE_RANDOM.nextDouble() - 0.5) * IMAGE_HEIGHT * 0.9,
                        IMAGE_WIDTH + RENDER_SCALE, endY);
                graphics.draw(curve);
            }

            // 噪点要有对比度才起作用。原先 130~229 的灰画在白底上几乎看不见。
            for (int index = 0; index < 26; index++) {
                graphics.setColor(new Color(90 + SECURE_RANDOM.nextInt(80), 105 + SECURE_RANDOM.nextInt(80),
                        125 + SECURE_RANDOM.nextInt(80)));
                graphics.fillRect(SECURE_RANDOM.nextInt(IMAGE_WIDTH), SECURE_RANDOM.nextInt(IMAGE_HEIGHT),
                        RENDER_SCALE, RENDER_SCALE);
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

    /**
     * 未启用返回 501 而不是 503，是为了让两端能把「功能未启用」和「服务暂时不可用」分开：
     * 前者应该放行直接登录（verifyAndConsume 在 enabled=false 时本来就直接返回），
     * 后者应该拦住并提示重试。两者同为 503 时，关掉验证码反而会让两端彻底登不进去。
     */
    private void requireEnabled() {
        if (!enabled) {
            throw new BizException(501, "图片验证码未启用");
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

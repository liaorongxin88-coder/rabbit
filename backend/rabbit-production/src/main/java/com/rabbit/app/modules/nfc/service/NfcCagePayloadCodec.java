package com.rabbit.app.modules.nfc.service;

import com.rabbit.app.common.BizException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NfcCagePayloadCodec {
    public static final String EXTERNAL_TYPE = "dzht.top:rabbit-cage";
    public static final String VERSION = "r1";
    public static final int ERROR_UNSUPPORTED_PAYLOAD = 4601;
    public static final int ERROR_INVALID_SIGNATURE = 4602;
    private static final int SIGNATURE_BYTES = 12;

    private final int activeKeyId;
    private final Map<Integer, byte[]> keys;

    public NfcCagePayloadCodec(
            @Value("${app.nfc.tag-signing.active-key-id:1}") int activeKeyId,
            @Value("${app.nfc.tag-signing.keys:1=cmFiYml0LW5mYy1kZXYtc2lnbmluZy1rZXktY2hhbmdlLW1l}") String configuredKeys
    ) {
        this.activeKeyId = activeKeyId;
        this.keys = Collections.unmodifiableMap(parseKeys(configuredKeys));
        if (!keys.containsKey(activeKeyId)) {
            throw new IllegalArgumentException("NFC active signing key is not configured: " + activeKeyId);
        }
    }

    public String create(Long houseId, Long cageId) {
        if (houseId == null || houseId <= 0 || cageId == null || cageId <= 0) {
            throw new IllegalArgumentException("houseId and cageId must be positive");
        }
        String canonical = VERSION
                + "." + Long.toString(houseId, 36)
                + "." + Long.toString(cageId, 36)
                + "." + Integer.toString(activeKeyId, 36);
        return canonical + "." + sign(canonical, keys.get(activeKeyId));
    }

    public ParsedPayload verify(String payload) {
        if (payload == null || payload.isBlank()) {
            throw unsupported("NFC标签内容为空");
        }
        String normalized = payload.trim();
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0].toLowerCase(Locale.ROOT))) {
            throw unsupported("NFC标签协议不受支持");
        }
        try {
            long houseId = Long.parseLong(parts[1], 36);
            long cageId = Long.parseLong(parts[2], 36);
            int keyId = Integer.parseInt(parts[3], 36);
            if (houseId <= 0 || cageId <= 0 || keyId <= 0 || parts[4].isEmpty()) {
                throw unsupported("NFC标签内容不完整");
            }
            byte[] key = keys.get(keyId);
            if (key == null) {
                throw invalidSignature();
            }
            String canonical = String.join(".",
                    parts[0].toLowerCase(Locale.ROOT),
                    parts[1].toLowerCase(Locale.ROOT),
                    parts[2].toLowerCase(Locale.ROOT),
                    parts[3].toLowerCase(Locale.ROOT));
            byte[] expected = Base64.getUrlDecoder().decode(sign(canonical, key));
            byte[] actual = Base64.getUrlDecoder().decode(parts[4]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw invalidSignature();
            }
            return new ParsedPayload(houseId, cageId, keyId, normalized);
        } catch (IllegalArgumentException e) {
            throw unsupported("NFC标签内容格式不正确");
        }
    }

    private String sign(String canonical, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] full = mac.doFinal(canonical.getBytes(StandardCharsets.US_ASCII));
            byte[] truncated = new byte[SIGNATURE_BYTES];
            System.arraycopy(full, 0, truncated, 0, SIGNATURE_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign NFC payload", e);
        }
    }

    private Map<Integer, byte[]> parseKeys(String configuredKeys) {
        Map<Integer, byte[]> parsed = new LinkedHashMap<Integer, byte[]>();
        if (configuredKeys == null || configuredKeys.isBlank()) {
            throw new IllegalArgumentException("At least one NFC signing key is required");
        }
        for (String entry : configuredKeys.split(",")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Invalid NFC signing key entry");
            }
            int keyId = Integer.parseInt(pair[0].trim());
            byte[] key = Base64.getUrlDecoder().decode(pair[1].trim());
            if (keyId <= 0 || key.length < 16 || parsed.put(keyId, key) != null) {
                throw new IllegalArgumentException("Invalid NFC signing key id or value: " + keyId);
            }
        }
        return parsed;
    }

    private BizException unsupported(String message) {
        return new BizException(ERROR_UNSUPPORTED_PAYLOAD, message);
    }

    private BizException invalidSignature() {
        return new BizException(ERROR_INVALID_SIGNATURE, "NFC标签签名无效");
    }

    public record ParsedPayload(long houseId, long cageId, int keyId, String payload) {
    }
}

package com.rabbit.app.modules.nfc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.common.BizException;
import org.junit.jupiter.api.Test;

class NfcCagePayloadCodecTest {
    private static final String KEY = "cmFiYml0LW5mYy1kZXYtc2lnbmluZy1rZXktY2hhbmdlLW1l";

    @Test
    void createsAndVerifiesCompactPayload() {
        NfcCagePayloadCodec codec = new NfcCagePayloadCodec(1, "1=" + KEY);

        String payload = codec.create(123L, 456L);
        NfcCagePayloadCodec.ParsedPayload parsed = codec.verify(payload);

        assertEquals(123L, parsed.houseId());
        assertEquals(456L, parsed.cageId());
        assertEquals(1, parsed.keyId());
        assertTrue(payload.startsWith("r1.3f.co.1."));
    }

    @Test
    void verifiesPayloadCreatedWithPreviousKey() {
        NfcCagePayloadCodec oldCodec = new NfcCagePayloadCodec(1, "1=" + KEY);
        String oldPayload = oldCodec.create(7L, 9L);
        NfcCagePayloadCodec rotatedCodec = new NfcCagePayloadCodec(2, "1=" + KEY + ",2=" + KEY);

        NfcCagePayloadCodec.ParsedPayload parsed = rotatedCodec.verify(oldPayload);

        assertEquals(1, parsed.keyId());
        assertEquals(7L, parsed.houseId());
        assertEquals(9L, parsed.cageId());
    }

    @Test
    void rejectsTamperedPayload() {
        NfcCagePayloadCodec codec = new NfcCagePayloadCodec(1, "1=" + KEY);
        String payload = codec.create(10L, 20L);
        String tampered = payload.replace("r1.a.k.", "r1.a.l.");

        BizException error = assertThrows(BizException.class, () -> codec.verify(tampered));

        assertEquals(NfcCagePayloadCodec.ERROR_INVALID_SIGNATURE, error.getCode());
    }

    @Test
    void rejectsUnsupportedPayload() {
        NfcCagePayloadCodec codec = new NfcCagePayloadCodec(1, "1=" + KEY);

        BizException error = assertThrows(BizException.class, () -> codec.verify("rabbit://cage/1/2"));

        assertEquals(NfcCagePayloadCodec.ERROR_UNSUPPORTED_PAYLOAD, error.getCode());
    }

    @Test
    void rejectsMalformedSignatureEncoding() {
        NfcCagePayloadCodec codec = new NfcCagePayloadCodec(1, "1=" + KEY);

        BizException error = assertThrows(BizException.class, () -> codec.verify("r1.a.k.1.%%%"));

        assertEquals(NfcCagePayloadCodec.ERROR_UNSUPPORTED_PAYLOAD, error.getCode());
    }

    @Test
    void maximumIdsFitAnNtag213NdefExternalRecord() {
        NfcCagePayloadCodec codec = new NfcCagePayloadCodec(1, "1=" + KEY);
        String payload = codec.create(Long.MAX_VALUE, Long.MAX_VALUE);
        int estimatedNdefBytes = payload.length() + NfcCagePayloadCodec.EXTERNAL_TYPE.length() + 6;

        assertTrue(estimatedNdefBytes <= 144, "NDEF record bytes=" + estimatedNdefBytes);
    }
}

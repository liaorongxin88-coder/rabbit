package com.rabbit.app.util;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Parcelable;

import java.nio.charset.Charset;
import java.util.Locale;

public class NfcUtil {
    public static String getTagUid(Intent intent) {
        if (intent == null) {
            return null;
        }
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            return null;
        }
        byte[] id = tag.getId();
        if (id == null || id.length == 0) {
            return null;
        }
        return toHex(id);
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }

    public static String readNdefTextOrUri(Intent intent) {
        if (intent == null) {
            return null;
        }
        Parcelable[] raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (raw == null || raw.length == 0) {
            return null;
        }
        for (Parcelable p : raw) {
            if (!(p instanceof NdefMessage)) {
                continue;
            }
            NdefMessage msg = (NdefMessage) p;
            for (NdefRecord r : msg.getRecords()) {
                String s = parseRecord(r);
                if (s != null && !s.trim().isEmpty()) {
                    return s.trim();
                }
            }
        }
        return null;
    }

    private static String parseRecord(NdefRecord r) {
        if (r == null) {
            return null;
        }
        short tnf = r.getTnf();
        byte[] type = r.getType();
        byte[] payload = r.getPayload();
        if (payload == null) {
            return null;
        }

        if (tnf == NdefRecord.TNF_WELL_KNOWN && type != null && java.util.Arrays.equals(type, NdefRecord.RTD_URI)) {
            return parseWellKnownUri(payload);
        }
        if (tnf == NdefRecord.TNF_ABSOLUTE_URI) {
            return new String(type, Charset.forName("UTF-8"));
        }
        if (tnf == NdefRecord.TNF_WELL_KNOWN && type != null && java.util.Arrays.equals(type, NdefRecord.RTD_TEXT)) {
            return parseWellKnownText(payload);
        }
        return null;
    }

    private static String parseWellKnownText(byte[] payload) {
        if (payload.length == 0) {
            return null;
        }
        int status = payload[0] & 0xFF;
        int langLen = status & 0x3F;
        boolean utf16 = (status & 0x80) != 0;
        int textStart = 1 + langLen;
        if (textStart > payload.length) {
            return null;
        }
        Charset cs = utf16 ? Charset.forName("UTF-16") : Charset.forName("UTF-8");
        return new String(payload, textStart, payload.length - textStart, cs);
    }

    private static String parseWellKnownUri(byte[] payload) {
        if (payload.length == 0) {
            return null;
        }
        int prefix = payload[0] & 0xFF;
        String uriPrefix = uriPrefix(prefix);
        String rest = new String(payload, 1, payload.length - 1, Charset.forName("UTF-8"));
        return uriPrefix + rest;
    }

    private static String uriPrefix(int code) {
        switch (code) {
            case 0x00:
                return "";
            case 0x01:
                return "http://www.";
            case 0x02:
                return "https://www.";
            case 0x03:
                return "http://";
            case 0x04:
                return "https://";
            default:
                return "";
        }
    }
}


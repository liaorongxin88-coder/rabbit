package com.rabbit.app.modules.auth.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 账号的生成与归一化。
 *
 * <p>{@code looksLikeUserCode} 是邀请接口分流的唯一依据：命中就当场把人拉进兔场，
 * 不命中就按手机号挂起等注册。这条判断错一次，两个方向都不体面——手机号被误判成账号，
 * 用户收到「没找到账号」；账号被误判成手机号，用户收到「请输入有效手机号」。
 * 所以这里把「手机号绝不能命中」单独立成用例。
 *
 * <p>归一化承担的是另一件事：账号会被用户口头报出来、手抄下来。十六进制字母表里没有
 * O/I/L，所以这三个字母出现时一定是 0/1/1 抄错了，可以放心替换——这个前提一旦
 * 被打破（比如字母表改成 Base32），替换就会把合法字符改坏。
 */
class UserCodesTest {

    @Test
    void generatedCodesLookLikeCodes() {
        for (int i = 0; i < 200; i++) {
            String code = UserCodes.random();
            assertEquals(11, code.length());
            assertTrue(UserCodes.looksLikeUserCode(code), code);
        }
    }

    @Test
    void generatedCodesDoNotRepeatInASmallDraw() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(UserCodes.random());
        }
        assertEquals(500, seen.size());
    }

    /**
     * 随机生成的账号必须已经是归一化形态，否则用户照着屏幕原样抄回来反而对不上。
     */
    @Test
    void generatedCodesSurviveNormalisationUnchanged() {
        for (int i = 0; i < 200; i++) {
            String code = UserCodes.random();
            assertEquals(code, UserCodes.normalize(code));
        }
    }

    @Test
    void normalisationRepairsTheUsualTranscriptionMistakes() {
        assertEquals("R0123456789", UserCodes.normalize("r0123456789"));
        assertEquals("R0123456789", UserCodes.normalize("  R0123456789  "));
        assertEquals("R0123456789", UserCodes.normalize("R0123-456789"));
        assertEquals("R0123456789", UserCodes.normalize("R0123_456789"));
        assertEquals("R0123456789", UserCodes.normalize("R0123 456 789"));
        assertEquals("R01234567AB", UserCodes.normalize("rO1234567ab"));
        assertEquals("R1111111111", UserCodes.normalize("rIlLiI11l1I"));
        assertTrue(UserCodes.looksLikeUserCode(UserCodes.normalize("rIlLiI11l1I")));
    }

    @Test
    void normalisingNothingYieldsAnEmptyString() {
        assertEquals("", UserCodes.normalize(null));
        assertEquals("", UserCodes.normalize("   "));
    }

    /**
     * 这条是邀请分流的地基：手机号是纯数字，永远不能被当成账号。
     */
    @Test
    void aPhoneNumberIsNeverMistakenForACode() {
        assertFalse(UserCodes.looksLikeUserCode(UserCodes.normalize("13800001111")));
        assertFalse(UserCodes.looksLikeUserCode(UserCodes.normalize("+8613800001111")));
        assertFalse(UserCodes.looksLikeUserCode(UserCodes.normalize("138-0000-1111")));
    }

    @Test
    void malformedCodesAreRejected() {
        assertFalse(UserCodes.looksLikeUserCode(null));
        assertFalse(UserCodes.looksLikeUserCode(""));
        assertFalse(UserCodes.looksLikeUserCode("R012345678"));
        assertFalse(UserCodes.looksLikeUserCode("R01234567890"));
        assertFalse(UserCodes.looksLikeUserCode("X0123456789"));
        assertFalse(UserCodes.looksLikeUserCode("R012345678G"));
        assertFalse(UserCodes.looksLikeUserCode("r0123456789"));
        assertFalse(UserCodes.looksLikeUserCode("R0123456789 "));
    }

    @Test
    void aWellFormedCodeIsAccepted() {
        assertTrue(UserCodes.looksLikeUserCode("R0123456789"));
        assertTrue(UserCodes.looksLikeUserCode("RABCDEF0123"));
        assertTrue(UserCodes.looksLikeUserCode("RFFFFFFFFFF"));
    }
}

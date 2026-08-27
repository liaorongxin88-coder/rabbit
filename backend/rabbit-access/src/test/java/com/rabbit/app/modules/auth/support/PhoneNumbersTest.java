package com.rabbit.app.modules.auth.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbit.app.common.BizException;
import org.junit.jupiter.api.Test;

/**
 * 手机号归一化。这是账号体系里唯一的自然键——{@code phoneHash} 由归一化结果算出，
 * 登录、注册、换绑、邀请兑现全靠它对上。
 *
 * <p>因此「同一个号码的不同写法必须归一到同一个串」不是体验问题而是正确性问题：
 * 用 {@code +86138...} 注册、用 {@code 138...} 登录，如果归一化不一致，
 * 用户会拿到「手机号未注册」，而他的账号确实存在，只是再也进不去了。
 *
 * <p>反过来，宽进也不行：格式校验松掉之后，各种垃圾串都能算出哈希并占掉一个账号位。
 */
class PhoneNumbersTest {

    @Test
    void allTheWaysToWriteOneNumberCollapseToTheSameString() {
        assertEquals("13800001111", PhoneNumbers.normalizeMainlandMobile("13800001111"));
        assertEquals("13800001111", PhoneNumbers.normalizeMainlandMobile("  13800001111  "));
        assertEquals("13800001111", PhoneNumbers.normalizeMainlandMobile("138 0000 1111"));
        assertEquals("13800001111", PhoneNumbers.normalizeMainlandMobile("138-0000-1111"));
        assertEquals("13800001111", PhoneNumbers.normalizeMainlandMobile("+8613800001111"));
        assertEquals("13800001111", PhoneNumbers.normalizeMainlandMobile("+86 138-0000-1111"));
        assertEquals("13800001111", PhoneNumbers.normalizeMainlandMobile("008613800001111"));
    }

    @Test
    void everyValidSecondDigitIsAccepted() {
        for (char second = '3'; second <= '9'; second++) {
            String phone = "1" + second + "800001111";
            assertEquals(phone, PhoneNumbers.normalizeMainlandMobile(phone));
        }
    }

    @Test
    void numbersThatAreNotMainlandMobilesAreRejected() {
        assertRejected("10800001111");
        assertRejected("12800001111");
        assertRejected("23800001111");
        assertRejected("1380000111");
        assertRejected("138000011112");
        assertRejected("1380000111a");
        assertRejected("+8512345678901");
    }

    @Test
    void blankInputIsRejected() {
        assertRejected(null);
        assertRejected("");
        assertRejected("   ");
        assertRejected("+86");
    }

    private void assertRejected(String value) {
        BizException error = assertThrows(
                BizException.class,
                () -> PhoneNumbers.normalizeMainlandMobile(value),
                () -> "应当拒绝: " + value
        );
        assertEquals(400, error.getCode());
    }
}

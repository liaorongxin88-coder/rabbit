package com.rabbit.app.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RequestIdUtilTest {
    @Test
    void keepsReadableSuffixWhenItFits() {
        assertEquals("round-17", RequestIdUtil.deriveChild("round", 17L));
    }

    @Test
    void hashesOnlyOverflowingChildKeysAndRemainsDeterministic() {
        String parent = "p".repeat(64);
        String first = RequestIdUtil.deriveChild(parent, 17L);
        String second = RequestIdUtil.deriveChild(parent, 17L);
        String other = RequestIdUtil.deriveChild(parent, 18L);

        assertEquals(36, first.length());
        assertEquals(first, second);
        assertNotEquals(first, other);
    }

    @Test
    void preservesNullForOptionalLegacyRequestIds() {
        assertNull(RequestIdUtil.deriveChild(null, 17L));
    }
}

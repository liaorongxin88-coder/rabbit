package com.rabbit.app.modules.operation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rabbit.app.common.BizException;
import org.junit.jupiter.api.Test;

class OperationEventCursorTest {

    @Test
    void aCursorSurvivesARoundTrip() {
        String encoded = OperationEventCursor.of(1_788_000_000_000L, 4_321L).encode();
        OperationEventCursor decoded = OperationEventCursor.decode(encoded);

        assertEquals(1_788_000_000_000L, decoded.getOccurredAtMillis());
        assertEquals(4_321L, decoded.getId());
    }

    @Test
    void theCursorIsOpaqueSoClientsCannotBuildOne() {
        // 不是为了保密，是为了保住改排序键的自由：一旦客户端会拼游标，
        // keyset 的排序列就再也动不了了。
        String encoded = OperationEventCursor.of(1_788_000_000_000L, 4_321L).encode();
        assertFalse(encoded.contains("1788000000000"));
        assertFalse(encoded.contains(":"));
    }

    @Test
    void garbageDecodesToABusinessErrorNotACrash() {
        for (String bad : new String[] {"", "not-base64!!", "Zm9vOmJhcg", "MTIz", "OjEyMw"}) {
            BizException error = assertThrows(
                BizException.class,
                () -> OperationEventCursor.decode(bad),
                "游标 " + bad + " 应当被拒绝"
            );
            assertEquals(400, error.getCode());
        }
    }
}

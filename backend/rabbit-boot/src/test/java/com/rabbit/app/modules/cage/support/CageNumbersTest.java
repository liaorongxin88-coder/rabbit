package com.rabbit.app.modules.cage.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CageNumbersTest {

    @Test
    @DisplayName("编号是排-位-层，跟人在舍里走的路线一致")
    void canonicalIsRowPositionLayer() {
        assertEquals("2-3-1", CageNumbers.canonical(2, 3, 1));
        assertEquals("1-1-1", CageNumbers.canonical(1, 1, 1));
    }

    @Test
    @DisplayName("排号带 R 前缀，编号里只留数字")
    void stripsRowPrefix() {
        assertEquals("2-3-1", CageNumbers.canonical("R2", 3, 1));
        assertEquals("2-3-1", CageNumbers.canonical(" r2 ", 3, 1));
        // 前导零是人手填出来的，别让 R02 和 R2 变成两个不同的排
        assertEquals("2-3-1", CageNumbers.canonical("R02", 3, 1));
    }

    @Test
    @DisplayName("排号不是 R+数字时原样保留，至少还看得出是哪一排")
    void keepsUnusualRowCode() {
        assertEquals("A-3-1", CageNumbers.canonical("A", 3, 1));
        assertEquals("东区-3-1", CageNumbers.canonical("东区", 3, 1));
    }

    @Test
    @DisplayName("坐标不全就推不出编号，交给调用方报错，不能瞎编一个")
    void refusesIncompleteCoordinates() {
        assertNull(CageNumbers.canonical("R2", null, 1));
        assertNull(CageNumbers.canonical("R2", 3, null));
        assertNull(CageNumbers.canonical("R2", 0, 1));
        assertNull(CageNumbers.canonical("R2", 3, 0));
        assertNull(CageNumbers.canonical(null, 3, 1));
        assertNull(CageNumbers.canonical("  ", 3, 1));
        // LEGACY 是历史数据里「没有坐标」的占位，不能当成一个排名
        assertNull(CageNumbers.canonical("LEGACY", 3, 1));
    }
}

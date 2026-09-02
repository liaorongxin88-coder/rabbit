package com.rabbit.app.modules.nfc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.nfc.dto.NfcCageQueueItem;
import com.rabbit.app.modules.nfc.dto.NfcCageQueueRow;
import com.rabbit.app.modules.nfc.mapper.CageNfcTagMapper;
import com.rabbit.app.modules.nfc.mapper.NfcTagMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 写卡队列要带上排、层、位。
 *
 * <p>贴签的人站在一排货架前面，队列却是整舍几百个笼位的平铺列表，没法按排和层筛。
 * 编号本身指望不上：{@code 1-1-1} 和 {@code 2(上)1} 两套写法在同一个产品里并存，
 * 客户端拆不出坐标，所以坐标必须由服务端按列给出。
 */
class NfcCageServiceTest {
    private static final String KEY = "cmFiYml0LW5mYy1kZXYtc2lnbmluZy1rZXktY2hhbmdlLW1l";

    private final HouseService houseService = mock(HouseService.class);
    private final NfcTagMapper nfcTagMapper = mock(NfcTagMapper.class);
    private final NfcCageService service = new NfcCageService(
            houseService,
            mock(CageMapper.class),
            nfcTagMapper,
            mock(CageNfcTagMapper.class),
            new NfcCagePayloadCodec(1, "1=" + KEY),
            mock(RequestDedupService.class)
    );

    @Test
    void carriesRowLayerAndPositionThrough() {
        when(nfcTagMapper.selectCageQueue(7L)).thenReturn(List.of(
                row(101L, "2-3-1", "R2", 1, 3, "AA11", "AA11")));

        List<NfcCageQueueItem> queue = service.listWriteQueue(9L, 7L);

        assertEquals(1, queue.size());
        NfcCageQueueItem item = queue.get(0);
        assertEquals("R2", item.getRowCode());
        assertEquals(Integer.valueOf(1), item.getLayerIndex());
        assertEquals(Integer.valueOf(3), item.getPositionIndex());
        // 既有字段不能因为这次增列而变样。
        assertEquals(Long.valueOf(101L), item.getCageId());
        assertEquals("2-3-1", item.getCageNumber());
        assertEquals("BOUND", item.getBindingStatus());
        assertEquals("AA11", item.getTagUid());
        assertNotNull(item.getPayload());
    }

    /**
     * 坐标缺失的笼位照样要出现在队列里，只是三个坐标字段为 null——
     * 别拿 0 或空串冒充，客户端得能分清「没录坐标」和「第 0 层」。
     */
    @Test
    void leavesPositionFieldsNullWhenCageHasNoCoordinates() {
        when(nfcTagMapper.selectCageQueue(7L)).thenReturn(List.of(
                row(202L, "角落笼", null, null, null, null, null)));

        List<NfcCageQueueItem> queue = service.listWriteQueue(9L, 7L);

        assertEquals(1, queue.size());
        NfcCageQueueItem item = queue.get(0);
        assertNull(item.getRowCode());
        assertNull(item.getLayerIndex());
        assertNull(item.getPositionIndex());
        // 缺坐标不影响这条记录本身可用。
        assertEquals(Long.valueOf(202L), item.getCageId());
        assertEquals("角落笼", item.getCageNumber());
        assertEquals("UNBOUND", item.getBindingStatus());
        assertNull(item.getTagUid());
        assertNotNull(item.getPayload());
    }

    /**
     * 历史数据的排号是字符串 'LEGACY'，不是 null。原样透传，
     * 才能和笼位地图读到的排号对上——否则按排筛会筛空。
     */
    @Test
    void passesLegacyRowCodeThroughUntouched() {
        when(nfcTagMapper.selectCageQueue(7L)).thenReturn(List.of(
                row(303L, "77", "LEGACY", 1, 303, null, "BB22")));

        List<NfcCageQueueItem> queue = service.listWriteQueue(9L, 7L);

        NfcCageQueueItem item = queue.get(0);
        assertEquals("LEGACY", item.getRowCode());
        assertEquals(Integer.valueOf(1), item.getLayerIndex());
        assertEquals(Integer.valueOf(303), item.getPositionIndex());
        assertEquals("CONFLICT", item.getBindingStatus());
    }

    @Test
    void stillChecksHousePermissionBeforeReadingTheQueue() {
        when(nfcTagMapper.selectCageQueue(7L)).thenReturn(List.of());

        service.listWriteQueue(9L, 7L);

        verify(houseService).assertHousePermission(eq(9L), eq(7L), eq("control"));
    }

    private static NfcCageQueueRow row(Long cageId, String cageNumber, String rowCode, Integer layerIndex,
                                       Integer positionIndex, String genericTagUid, String cageTagUid) {
        NfcCageQueueRow row = new NfcCageQueueRow();
        row.setCageId(cageId);
        row.setCageNumber(cageNumber);
        row.setRowCode(rowCode);
        row.setLayerIndex(layerIndex);
        row.setPositionIndex(positionIndex);
        row.setGenericTagUid(genericTagUid);
        row.setCageTagUid(cageTagUid);
        return row;
    }
}

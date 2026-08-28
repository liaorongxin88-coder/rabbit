package com.rabbit.app.modules.rabbit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.file.service.BusinessFileService;
import com.rabbit.app.modules.rabbit.dto.CreateAbnormalRequest;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbnormalServiceTest {
    private static final long HOUSE_ID = 8L;
    private static final long USER_ID = 12L;
    private static final long RABBIT_ID = 34L;

    private RabbitAbnormalConditionMapper abnormalMapper;
    private RabbitMapper rabbitMapper;
    private BusinessFileService businessFileService;
    private RequestDedupService requestDedupService;
    private AbnormalService service;

    @BeforeEach
    void setUp() {
        abnormalMapper = mock(RabbitAbnormalConditionMapper.class);
        rabbitMapper = mock(RabbitMapper.class);
        businessFileService = mock(BusinessFileService.class);
        requestDedupService = mock(RequestDedupService.class);
        service = new AbnormalService(
                abnormalMapper, rabbitMapper, businessFileService, requestDedupService
        );
    }

    @Test
    void createsAnUnhandledRecordForAHouseRabbitAndOwnedImage() {
        Rabbit rabbit = new Rabbit();
        rabbit.setId(RABBIT_ID);
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit);
        when(abnormalMapper.insert(any())).thenReturn(1);

        service.create(HOUSE_ID, USER_ID, request());

        verify(businessFileService).requireFile(HOUSE_ID, "file_abc");
        ArgumentCaptor<RabbitAbnormalCondition> captured =
                ArgumentCaptor.forClass(RabbitAbnormalCondition.class);
        verify(abnormalMapper).insert(captured.capture());
        RabbitAbnormalCondition condition = captured.getValue();
        assertEquals(HOUSE_ID, condition.getHouseId());
        assertEquals(RABBIT_ID, condition.getRabbitId());
        assertEquals("外伤", condition.getWarningStatus());
        assertEquals("file_abc", condition.getImgUrl());
        assertEquals("右耳有擦伤", condition.getRemark());
        assertEquals(Boolean.FALSE, condition.getIsDeal());
        assertNotNull(condition.getWarningTime());
        verify(requestDedupService).markDone(HOUSE_ID, USER_ID, "abnormal.create", "request-1");
    }

    @Test
    void rejectsRabbitOutsideTheCurrentHouseBeforeWriting() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(null);

        BizException error = assertThrows(
                BizException.class,
                () -> service.create(HOUSE_ID, USER_ID, request())
        );

        assertEquals(404, error.getCode());
        verify(businessFileService, never()).requireFile(anyLong(), anyString());
        verify(abnormalMapper, never()).insert(any());
    }

    @Test
    void completedRequestIdDoesNotCreateAnotherRecord() {
        when(requestDedupService.shouldSkipAsDone(HOUSE_ID, USER_ID, "abnormal.create", "request-1"))
                .thenReturn(true);

        service.create(HOUSE_ID, USER_ID, request());

        verify(rabbitMapper, never()).selectById(anyLong(), anyLong());
        verify(abnormalMapper, never()).insert(any());
        verify(requestDedupService, never()).markProcessing(anyLong(), anyLong(), anyString(), anyString());
    }

    private CreateAbnormalRequest request() {
        CreateAbnormalRequest request = new CreateAbnormalRequest();
        request.setRabbitId(RABBIT_ID);
        request.setWarningStatus("外伤");
        request.setImageFileId("file_abc");
        request.setRemark("右耳有擦伤");
        request.setRequestId("request-1");
        return request;
    }
}

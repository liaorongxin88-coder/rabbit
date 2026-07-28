package com.rabbit.app.modules.dedup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.entity.RequestDedup;
import com.rabbit.app.modules.dedup.mapper.RequestDedupMapper;
import org.junit.jupiter.api.Test;

class RequestDedupServiceTest {
    @Test
    void atomicallyStartsANewRequest() {
        FakeRequestDedupMapper mapper = new FakeRequestDedupMapper();
        mapper.insertResult = 1;
        RequestDedupService service = new RequestDedupService(mapper);

        RequestDedupService.BeginResult result = service.begin(8L, 3L, "nfc:cage:bind", "request-1");

        assertEquals(RequestDedupService.BeginResult.STARTED, result);
        assertEquals(0, mapper.selectCalls);
    }

    @Test
    void returnsDoneWhenAnotherRequestAlreadyCompleted() {
        FakeRequestDedupMapper mapper = new FakeRequestDedupMapper();
        mapper.selected = item(RequestDedupService.STATUS_DONE);
        RequestDedupService service = new RequestDedupService(mapper);

        RequestDedupService.BeginResult result = service.begin(8L, 3L, "nfc:cage:bind", "request-1");

        assertEquals(RequestDedupService.BeginResult.DONE, result);
    }

    @Test
    void rejectsARequestThatIsStillProcessing() {
        FakeRequestDedupMapper mapper = new FakeRequestDedupMapper();
        mapper.selected = item(RequestDedupService.STATUS_PROCESSING);
        RequestDedupService service = new RequestDedupService(mapper);

        BizException error = assertThrows(
                BizException.class,
                () -> service.begin(8L, 3L, "nfc:cage:bind", "request-1")
        );

        assertEquals(429, error.getCode());
    }

    private static RequestDedup item(String status) {
        RequestDedup item = new RequestDedup();
        item.setHouseId(8L);
        item.setUserId(3L);
        item.setApi("nfc:cage:bind");
        item.setRequestId("request-1");
        item.setStatus(status);
        return item;
    }

    private static class FakeRequestDedupMapper implements RequestDedupMapper {
        int insertResult;
        int selectCalls;
        RequestDedup selected;

        @Override
        public RequestDedup selectByKey(Long houseId, Long userId, String api, String requestId) {
            selectCalls++;
            return selected;
        }

        @Override
        public int insert(RequestDedup item) {
            return 1;
        }

        @Override
        public int insertIgnore(RequestDedup item) {
            return insertResult;
        }

        @Override
        public int updateStatus(
                Long houseId,
                Long userId,
                String api,
                String requestId,
                String status,
                String errorMessage
        ) {
            return 1;
        }
    }
}

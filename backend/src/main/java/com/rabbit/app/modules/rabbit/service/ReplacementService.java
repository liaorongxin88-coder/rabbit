package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplacementService {
    private final ReplacementRecordMapper replacementRecordMapper;
    private final RequestDedupService requestDedupService;

    public ReplacementService(
            ReplacementRecordMapper replacementRecordMapper,
            RequestDedupService requestDedupService
    ) {
        this.replacementRecordMapper = replacementRecordMapper;
        this.requestDedupService = requestDedupService;
    }

    public List<ReplacementRecord> list(
            Long houseId,
            Boolean matureNotified,
            Date from,
            Date to,
            Integer page,
            Integer pageSize
    ) {
        int normalizedPage = page == null || page <= 0 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        return replacementRecordMapper.selectByHouse(
                houseId,
                matureNotified,
                from,
                to,
                offset,
                normalizedPageSize
        );
    }

    public List<ReplacementRecord> listDue(Long houseId, boolean onlyUnnotified) {
        if (onlyUnnotified) {
            return replacementRecordMapper.selectDueUnnotified(houseId, DateUtil.now());
        }
        return replacementRecordMapper.selectDue(houseId, DateUtil.now());
    }

    @Transactional
    public void markNotified(Long userId, Long houseId, List<Long> recordIds, String requestId) {
        String api = "replacement.markNotified";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Date notifyDate = DateUtil.now();
            for (Long id : recordIds) {
                replacementRecordMapper.markNotified(houseId, id, notifyDate, String.valueOf(userId));
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException error) {
            requestDedupService.markFailed(houseId, userId, api, requestId, error.getMessage());
            throw error;
        }
    }
}

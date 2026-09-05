package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldPage;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldRequest;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldView;
import com.rabbit.app.modules.batch.entity.BatchCarcassYieldVersion;
import com.rabbit.app.modules.batch.mapper.BatchCarcassYieldMapper;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.support.BatchWritePayloadHasher;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.file.service.BusinessFileService;
import com.rabbit.app.tracking.OperationContext;
import com.rabbit.app.tracking.TrackedOperation;
import com.rabbit.app.util.DateUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchCarcassYieldService {
    private static final String API_CODE = "batch:carcass-yield";

    private final BatchMapper batchMapper;
    private final BatchCarcassYieldMapper carcassYieldMapper;
    private final RequestDedupService requestDedupService;
    private final BusinessFileService businessFileService;

    public BatchCarcassYieldService(
        BatchMapper batchMapper,
        BatchCarcassYieldMapper carcassYieldMapper,
        RequestDedupService requestDedupService,
        BusinessFileService businessFileService
    ) {
        this.batchMapper = batchMapper;
        this.carcassYieldMapper = carcassYieldMapper;
        this.requestDedupService = requestDedupService;
        this.businessFileService = businessFileService;
    }

    @TrackedOperation(
        code = API_CODE,
        eventType = "CARCASS_YIELD_RECORDED",
        targetType = "BATCH",
        targetId = "#batchId",
        requestId = "#request.requestId"
    )
    @Transactional
    public BatchCarcassYieldView append(
        Long userId,
        Long houseId,
        Long batchId,
        BatchCarcassYieldRequest request
    ) {
        requireBatchForUpdate(houseId, batchId);
        BigDecimal yieldRate = normalizeRate(request.getYieldRate());
        String sourceUnit = requiredText(request.getSourceUnit(), "sourceUnit不能为空");
        String changeReason = requiredText(request.getChangeReason(), "changeReason不能为空");
        String evidenceFileId = trim(request.getEvidenceFileId());
        if (evidenceFileId != null) {
            businessFileService.requireFile(houseId, evidenceFileId);
        }
        String payloadHash = payloadHash(
            batchId, request, yieldRate, sourceUnit, evidenceFileId, changeReason
        );

        BatchCarcassYieldVersion existing = carcassYieldMapper.selectByRequestId(
            houseId, request.getRequestId()
        );
        if (existing != null) {
            return replay(existing, batchId, payloadHash);
        }
        if (requestDedupService.begin(
            houseId, userId, API_CODE, request.getRequestId(), payloadHash
        ) == RequestDedupService.BeginResult.DONE) {
            BatchCarcassYieldVersion replayed = carcassYieldMapper.selectByRequestId(
                houseId, request.getRequestId()
            );
            if (replayed == null) {
                throw new BizException(409, "出肉率幂等记录与业务版本不一致");
            }
            return replay(replayed, batchId, payloadHash);
        }

        try {
            BatchCarcassYieldVersion version = new BatchCarcassYieldVersion();
            version.setHouseId(houseId);
            version.setBatchId(batchId);
            version.setYieldRate(yieldRate);
            version.setSourceUnit(sourceUnit);
            version.setMeasuredDate(request.getMeasuredDate());
            version.setReportNumber(trim(request.getReportNumber()));
            version.setEvidenceFile(evidenceFileId);
            version.setRemark(trim(request.getRemark()));
            version.setChangeReason(changeReason);
            version.setRequestId(request.getRequestId());
            version.setPayloadHash(payloadHash);
            version.setCreatedBy(userId);
            version.setCreatedAt(DateUtil.now());
            carcassYieldMapper.insert(version);
            requestDedupService.markDone(houseId, userId, API_CODE, request.getRequestId());
            return view(version);
        } catch (RuntimeException error) {
            requestDedupService.markFailed(
                houseId, userId, API_CODE, request.getRequestId(), error.getMessage()
            );
            throw error;
        }
    }

    public BatchCarcassYieldPage list(
        Long houseId,
        Long batchId,
        int page,
        int pageSize
    ) {
        requireBatch(houseId, batchId);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(200, pageSize));
        List<BatchCarcassYieldView> items = carcassYieldMapper.selectPage(
            houseId,
            batchId,
            (normalizedPage - 1) * normalizedSize,
            normalizedSize
        ).stream().map(BatchCarcassYieldService::view).toList();
        return new BatchCarcassYieldPage(
            items,
            carcassYieldMapper.countByBatch(houseId, batchId),
            normalizedPage,
            normalizedSize
        );
    }

    private BatchCarcassYieldView replay(
        BatchCarcassYieldVersion existing,
        Long batchId,
        String payloadHash
    ) {
        if (!Objects.equals(existing.getBatchId(), batchId)
            || !Objects.equals(existing.getPayloadHash(), payloadHash)) {
            throw new BizException(409, "requestId已用于不同的出肉率记录");
        }
        OperationContext context = OperationContext.current();
        if (context != null) {
            context.setDedupReplay(true);
        }
        return view(existing);
    }

    private void requireBatch(Long houseId, Long batchId) {
        if (batchId == null || batchMapper.selectById(houseId, batchId) == null) {
            throw new BizException(404, "批次不存在");
        }
    }

    private void requireBatchForUpdate(Long houseId, Long batchId) {
        if (batchId == null || batchMapper.selectByIdForUpdate(houseId, batchId) == null) {
            throw new BizException(404, "批次不存在");
        }
    }

    private static BigDecimal normalizeRate(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0
            || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(400, "yieldRate必须大于0且不超过1");
        }
        try {
            return value.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new BizException(400, "yieldRate最多保留六位小数");
        }
    }

    private static String payloadHash(
        Long batchId,
        BatchCarcassYieldRequest request,
        BigDecimal yieldRate,
        String sourceUnit,
        String evidenceFileId,
        String changeReason
    ) {
        String canonical = String.join(
            "|",
            String.valueOf(batchId),
            BatchWritePayloadHasher.decimal(yieldRate),
            BatchWritePayloadHasher.text(sourceUnit),
            String.valueOf(request.getMeasuredDate()),
            BatchWritePayloadHasher.text(trim(request.getReportNumber())),
            BatchWritePayloadHasher.text(evidenceFileId),
            BatchWritePayloadHasher.text(trim(request.getRemark())),
            BatchWritePayloadHasher.text(changeReason)
        );
        return BatchWritePayloadHasher.sha256(canonical);
    }

    private static String requiredText(String value, String message) {
        String normalized = trim(value);
        if (normalized == null) {
            throw new BizException(400, message);
        }
        return normalized;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BatchCarcassYieldView view(BatchCarcassYieldVersion version) {
        return new BatchCarcassYieldView(
            version.getId(),
            version.getHouseId(),
            version.getBatchId(),
            version.getYieldRate(),
            version.getSourceUnit(),
            version.getMeasuredDate(),
            version.getReportNumber(),
            version.getEvidenceFile(),
            version.getRemark(),
            version.getChangeReason(),
            version.getRequestId(),
            version.getCreatedBy(),
            null,
            version.getCreatedAt()
        );
    }
}

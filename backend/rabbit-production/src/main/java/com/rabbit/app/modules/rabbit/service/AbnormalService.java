package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.file.service.BusinessFileService;
import com.rabbit.app.modules.rabbit.dto.CreateAbnormalRequest;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.tracking.TrackedOperation;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AbnormalService {
    private static final String CREATE_API = "abnormal.create";

    private final RabbitAbnormalConditionMapper abnormalConditionMapper;
    private final RabbitMapper rabbitMapper;
    private final BusinessFileService businessFileService;
    private final RequestDedupService requestDedupService;

    public AbnormalService(
            RabbitAbnormalConditionMapper abnormalConditionMapper,
            RabbitMapper rabbitMapper,
            BusinessFileService businessFileService,
            RequestDedupService requestDedupService
    ) {
        this.abnormalConditionMapper = abnormalConditionMapper;
        this.rabbitMapper = rabbitMapper;
        this.businessFileService = businessFileService;
        this.requestDedupService = requestDedupService;
    }

    @TrackedOperation(
        code = CREATE_API, eventType = "RABBIT_ABNORMAL_RECORDED", requestId = "#request.requestId",
        targetType = "RABBIT", targetId = "#request.rabbitId", dedup = true
    )
    @Transactional
    public void create(Long houseId, Long userId, CreateAbnormalRequest request) {
        String requestId = request.getRequestId();
        if (requestDedupService.shouldSkipAsDone(houseId, userId, CREATE_API, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, CREATE_API, requestId);
        try {
            Rabbit rabbit = rabbitMapper.selectById(houseId, request.getRabbitId());
            if (rabbit == null) {
                throw new BizException(404, "兔只不存在");
            }
            String imageFileId = request.getImageFileId().trim();
            businessFileService.requireFile(houseId, imageFileId);

            RabbitAbnormalCondition condition = new RabbitAbnormalCondition();
            condition.setHouseId(houseId);
            condition.setRabbitId(rabbit.getId());
            condition.setWarningStatus(request.getWarningStatus().trim());
            condition.setWarningTime(new Date());
            condition.setImgUrl(imageFileId);
            condition.setRemark(request.getRemark().trim());
            condition.setIsDeal(Boolean.FALSE);
            if (abnormalConditionMapper.insert(condition) != 1) {
                throw new BizException(500, "异常记录保存失败，请重试");
            }
            requestDedupService.markDone(houseId, userId, CREATE_API, requestId);
        } catch (RuntimeException error) {
            requestDedupService.markFailed(houseId, userId, CREATE_API, requestId, error.getMessage());
            throw error;
        }
    }
}

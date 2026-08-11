package com.rabbit.app.modules.batch.service;

import com.rabbit.app.modules.batch.entity.Batch;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Stable application facade for batch commands. Transaction boundaries live in
 * the focused workflow services so each business process can evolve independently.
 */
@Service
public class BatchService {
    private final BatchLifecycleService lifecycleService;
    private final BatchBreedingService breedingService;
    private final BatchParturitionService parturitionService;
    private final BatchWeaningService weaningService;
    private final BatchAphrodisiacService aphrodisiacService;
    private final BatchSaleService saleService;

    public BatchService(
            BatchLifecycleService lifecycleService,
            BatchBreedingService breedingService,
            BatchParturitionService parturitionService,
            BatchWeaningService weaningService,
            BatchAphrodisiacService aphrodisiacService,
            BatchSaleService saleService
    ) {
        this.lifecycleService = lifecycleService;
        this.breedingService = breedingService;
        this.parturitionService = parturitionService;
        this.weaningService = weaningService;
        this.aphrodisiacService = aphrodisiacService;
        this.saleService = saleService;
    }

    public Batch createBatch(
            Long userId,
            Long houseId,
            String batchCode,
            List<Long> femaleRabbitIds,
            String remark,
            String requestId
    ) {
        return lifecycleService.createBatch(
                userId,
                houseId,
                batchCode,
                femaleRabbitIds,
                remark,
                requestId
        );
    }

    public void mating(
            Long userId,
            Long houseId,
            Long batchId,
            Long femaleRabbitId,
            Long maleRabbitId,
            Date matingDate,
            String requestId
    ) {
        breedingService.mate(
                userId,
                houseId,
                batchId,
                femaleRabbitId,
                maleRabbitId,
                matingDate,
                requestId
        );
    }

    public void pregnancyCheck(
            Long userId,
            Long houseId,
            Long batchId,
            Long rabbitId,
            Date checkDate,
            String result,
            String remark,
            String requestId
    ) {
        breedingService.checkPregnancy(
                userId,
                houseId,
                batchId,
                rabbitId,
                checkDate,
                result,
                remark,
                requestId
        );
    }

    public void prepartumFinish(
            Long userId,
            Long houseId,
            Long batchId,
            Long rabbitId,
            Date actionDate,
            String remark,
            String requestId
    ) {
        breedingService.finishPrepartum(
                userId,
                houseId,
                batchId,
                rabbitId,
                actionDate,
                remark,
                requestId
        );
    }

    public void parturition(
            Long userId,
            Long houseId,
            Long batchId,
            Long rabbitId,
            Date birthDate,
            int totalKits,
            int liveKits,
            boolean failed,
            String remark,
            String requestId
    ) {
        parturitionService.record(
                userId,
                houseId,
                batchId,
                rabbitId,
                birthDate,
                totalKits,
                liveKits,
                failed,
                remark,
                requestId
        );
    }

    public void weaning(
            Long userId,
            Long houseId,
            Long batchId,
            Long rabbitId,
            Date weaningDate,
            int weaningCount,
            Integer maleCount,
            Integer femaleCount,
            Long targetCageId,
            Double avgWeight,
            String remark,
            String requestId
    ) {
        weaningService.wean(
                userId,
                houseId,
                batchId,
                rabbitId,
                weaningDate,
                weaningCount,
                maleCount,
                femaleCount,
                targetCageId,
                avgWeight,
                remark,
                requestId
        );
    }

    public void aphrodisiacStart(
            Long userId,
            Long houseId,
            Long batchId,
            List<Long> rabbitIds,
            String requestId
    ) {
        aphrodisiacService.start(userId, houseId, batchId, rabbitIds, requestId);
    }

    public void aphrodisiacFinish(
            Long userId,
            Long houseId,
            Long batchId,
            List<Long> rabbitIds,
            String requestId
    ) {
        aphrodisiacService.finish(userId, houseId, batchId, rabbitIds, requestId);
    }

    public void sale(
            Long userId,
            Long houseId,
            Long batchId,
            List<Long> rabbitIds,
            Date saleDate,
            String remark,
            String requestId
    ) {
        saleService.sell(userId, houseId, batchId, rabbitIds, saleDate, remark, requestId);
    }

    public void completeBatch(
            Long userId,
            Long houseId,
            Long batchId,
            Date endDate,
            boolean force,
            String remark,
            String requestId
    ) {
        lifecycleService.completeBatch(
                userId,
                houseId,
                batchId,
                endDate,
                force,
                remark,
                requestId
        );
    }
}

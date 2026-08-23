package com.rabbit.app.modules.repro.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stores a completed weaning and later turns explicit cage allocations into commodity rabbits.
 *
 * <p>The reproduction state machine owns the former operation. The later inventory operation is
 * called by the batch separation service, after it has locked the batch, weaning record, and cages.
 * This class deliberately does not open its own transaction so both workflows remain atomic.
 */
@Service
public class KitPlacementService {

    private static final int BULK_WRITE_SIZE = 500;

    private final CageMapper cageMapper;
    private final RabbitMapper rabbitMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final WeaningRecordMapper weaningRecordMapper;
    private final BreedingPerformanceRecorder performanceRecorder;
    private final SettingService settingService;
    private final WorkTaskWriter workTaskWriter;
    private final int commodityCageCapacity;

    public KitPlacementService(
        CageMapper cageMapper,
        RabbitMapper rabbitMapper,
        RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
        BatchRabbitMapper batchRabbitMapper,
        WeaningRecordMapper weaningRecordMapper,
        BreedingPerformanceRecorder performanceRecorder,
        SettingService settingService,
        WorkTaskWriter workTaskWriter,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.cageMapper = cageMapper;
        this.rabbitMapper = rabbitMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.weaningRecordMapper = weaningRecordMapper;
        this.performanceRecorder = performanceRecorder;
        this.settingService = settingService;
        this.workTaskWriter = workTaskWriter;
        this.commodityCageCapacity = commodityCageCapacity;
    }

    /** Records completed weaning without allocating a cage or creating rabbit inventory. */
    public WeaningRecord registerPending(KitPlacementCommand command) {
        validateWeaning(command);

        WeaningRecord record = new WeaningRecord();
        record.setHouseId(command.houseId());
        record.setBatchId(command.batchId());
        record.setBreedingCycleId(command.cycleId());
        record.setRabbitId(command.motherRabbitId());
        // Kept for request compatibility only. WEANING must never allocate this cage.
        record.setTargetCageId(null);
        record.setInCageId(null);
        record.setWeaningDate(command.weaningDate());
        record.setWeaningCount(command.weanedCount());
        record.setWaitingCount(command.weanedCount());
        record.setMaleCount(command.maleCount());
        record.setFemaleCount(command.femaleCount());
        record.setAvgWeight(command.avgWeight());
        record.setRemark(command.remark());
        record.setCreateBy(command.operator());
        record.setUpdateBy(command.operator());
        weaningRecordMapper.insert(record);

        performanceRecorder.recordWeaning(
            command.houseId(), command.motherRabbitId(), command.weanedCount()
        );
        return record;
    }

    /** Creates only the rabbits represented by one explicit separation request. */
    public List<Long> separate(KitSeparationCommand command) {
        validateSeparation(command);
        List<Rabbit> kits = occupyCagesAndBuildKits(command);
        insertKitsAndHydrateIds(kits);
        linkKitsToBatch(command, kits);
        return kits.stream().map(Rabbit::getId).toList();
    }

    private void validateWeaning(KitPlacementCommand command) {
        if (command.weanedCount() < 0) {
            throw new BizException(400, "断奶数量错误");
        }
        int male = orZero(command.maleCount());
        int female = orZero(command.femaleCount());
        if (male + female != 0 && male + female != command.weanedCount()) {
            throw new BizException(400, "公母数量之和需等于断奶数量");
        }
    }

    private void validateSeparation(KitSeparationCommand command) {
        WeaningRecord record = command.weaningRecord();
        if (record == null || record.getId() == null) {
            throw new BizException(400, "待分笼记录不存在");
        }
        int total = allocationCount(command.allocations());
        if (total <= 0 || total > orZero(record.getWaitingCount())) {
            throw new BizException(400, "分笼数量超过待分笼数量");
        }
    }

    private List<Rabbit> occupyCagesAndBuildKits(KitSeparationCommand command) {
        WeaningRecord record = command.weaningRecord();
        List<Rabbit> kits = new ArrayList<>();
        int index = orZero(record.getWeaningCount()) - orZero(record.getWaitingCount());
        for (WeaningRecordAllocation allocation : command.allocations()) {
            int add = orZero(allocation.getAllocCount());
            if (add <= 0) {
                continue;
            }
            if (cageMapper.incrementCommodityRabbitCountWithinCapacity(
                record.getHouseId(), allocation.getCageId(), add, commodityCageCapacity,
                command.operator()
            ) != 1) {
                throw new BizException(409, "笼位状态或容量已变化，请刷新后重试");
            }
            for (int offset = 0; offset < add; offset++) {
                kits.add(newKit(command, allocation.getCageId(), index++));
            }
        }
        return kits;
    }

    private Rabbit newKit(KitSeparationCommand command, Long cageId, int index) {
        WeaningRecord record = command.weaningRecord();
        Rabbit kit = new Rabbit();
        kit.setHouseId(record.getHouseId());
        kit.setCageId(cageId);
        kit.setMotherId(record.getRabbitId());
        kit.setFatherId(command.sireRabbitId());
        kit.setBirthBatchId(record.getBatchId());
        kit.setBirthCycleId(record.getBreedingCycleId());
        kit.setType("2");
        kit.setGender(pickGender(index, orZero(record.getMaleCount()), orZero(record.getFemaleCount())));
        kit.setArrivalMethod("1");
        kit.setArrivalDate(command.separatedAt());
        kit.setWeight(record.getAvgWeight());
        kit.setGrowthStage("JUVENILE");
        kit.setGrowthStageEnteredAt(command.separatedAt());
        kit.setIsActive(Boolean.TRUE);
        kit.setIsQuarantined(Boolean.FALSE);
        kit.setRequestId(ReproRequestIds.derive(command.requestId(), "kit-" + index));
        kit.setCreateBy(command.operator());
        kit.setUpdateBy(command.operator());
        return kit;
    }

    private static String pickGender(int index, int maleCount, int femaleCount) {
        if (maleCount + femaleCount == 0) {
            return "0";
        }
        return index < maleCount ? "1" : "0";
    }

    private void insertKitsAndHydrateIds(List<Rabbit> kits) {
        for (int from = 0; from < kits.size(); from += BULK_WRITE_SIZE) {
            int to = Math.min(from + BULK_WRITE_SIZE, kits.size());
            List<Rabbit> chunk = kits.subList(from, to);
            if (rabbitMapper.insertBatch(chunk) != chunk.size()) {
                throw new BizException(500, "仔兔批量写入失败");
            }
            List<String> requestIds = new ArrayList<>(chunk.size());
            for (Rabbit kit : chunk) {
                requestIds.add(kit.getRequestId());
            }
            Map<String, Rabbit> persisted = new HashMap<>();
            for (Rabbit saved : rabbitMapper.selectByHouseAndRequestIds(
                chunk.get(0).getHouseId(), requestIds
            )) {
                persisted.put(saved.getRequestId(), saved);
            }
            if (persisted.size() != chunk.size()) {
                throw new BizException(500, "仔兔批量写入回查失败");
            }
            for (Rabbit kit : chunk) {
                Rabbit saved = persisted.get(kit.getRequestId());
                if (saved == null || saved.getId() == null) {
                    throw new BizException(500, "仔兔主键回查失败");
                }
                kit.setId(saved.getId());
            }
        }
    }

    private void linkKitsToBatch(KitSeparationCommand command, List<Rabbit> kits) {
        WeaningRecord record = command.weaningRecord();
        GlobalSetting setting = settingService.getEffectiveSetting(
            command.userId(), record.getHouseId()
        );
        Date saleDate = DateUtil.plusDays(command.separatedAt(), setting.commodityMaturityDays());

        List<BatchRabbit> links = new ArrayList<>(kits.size());
        List<RabbitStatusHistory> histories = new ArrayList<>(kits.size());
        for (Rabbit kit : kits) {
            BatchRabbit link = new BatchRabbit();
            link.setBatchId(record.getBatchId());
            link.setRabbitId(kit.getId());
            link.setJoinReason("分笼");
            link.setBatchRole("fattening");
            link.setCurrentStatus("幼兔适应期");
            link.setLastEventDate(command.separatedAt());
            link.setNextEventDate(saleDate);
            link.setNextEventType("出售");
            link.setIsActive(Boolean.TRUE);
            link.setJoinDate(command.separatedAt());
            link.setCreateBy(command.operator());
            link.setUpdateBy(command.operator());
            links.add(link);

            RabbitStatusHistory history = new RabbitStatusHistory();
            history.setHouseId(record.getHouseId());
            history.setRabbitId(kit.getId());
            history.setBatchId(record.getBatchId());
            history.setFromStatus(null);
            history.setToStatus("幼兔适应期");
            history.setChangeTime(command.separatedAt());
            history.setReason("分笼生成仔兔");
            history.setRelatedRecordId(record.getId());
            history.setRelatedRecordTable("weaning_records");
            history.setCreateBy(command.operator());
            history.setUpdateBy(command.operator());
            histories.add(history);

            workTaskWriter.scheduleForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
                record.getHouseId(),
                TaskType.SALE_READY,
                kit.getId(),
                record.getBatchId(),
                kit.getCageId(),
                saleDate,
                "商品兔成熟后可进入出售流程",
                command.operator()
            ));
        }
        for (int from = 0; from < links.size(); from += BULK_WRITE_SIZE) {
            int to = Math.min(from + BULK_WRITE_SIZE, links.size());
            batchRabbitMapper.insertBatch(links.subList(from, to));
        }
        for (int from = 0; from < histories.size(); from += BULK_WRITE_SIZE) {
            int to = Math.min(from + BULK_WRITE_SIZE, histories.size());
            rabbitStatusHistoryMapper.insertBatch(histories.subList(from, to));
        }
    }

    private static int allocationCount(List<WeaningRecordAllocation> allocations) {
        int total = 0;
        for (WeaningRecordAllocation allocation : allocations) {
            total += orZero(allocation.getAllocCount());
        }
        return total;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}

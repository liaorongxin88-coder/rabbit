package com.rabbit.app.modules.repro.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordAllocationMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
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
 * 分笼落位：把断奶仔兔从母兔笼迁入商品兔笼，并建立仔兔档案。
 *
 * <p><b>为什么单独一个服务，而不是塞进状态机。</b>
 * 状态机回答的是「母兔的周期走到哪一步」，只碰周期/事件/窝/任务/投影。
 * 落位回答的是「这一窝仔兔去哪个笼、算谁的账」，要碰笼位容量、兔子档案、
 * 批次成员、绩效统计。把后者并进状态机，它会重新长成这次重构要拆掉的
 * 那个上帝对象——旧的 {@code BatchService.weaning} 就是 288 行的反面教材。
 *
 * <p><b>并发不变式在这里，不在调用方。</b>笼位容量靠
 * {@code incrementCommodityRabbitCountWithinCapacity} 的条件 UPDATE 保证：
 * 先查后写会在并发下超员，只有「带容量判据的原子递增 + 影响行数校验」才成立。
 * 这条不变式由 WeaningCageConsistencyIT 的并发用例守着。
 *
 * <p>本服务不开自己的事务，靠调用方的事务传播：落位失败必须连同周期状态
 * 一起回滚，否则会出现「周期已断奶但仔兔没落位」的悬空数据。
 */
@Service
public class KitPlacementService {

    private static final int BULK_WRITE_SIZE = 500;

    private final CageMapper cageMapper;
    private final RabbitMapper rabbitMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final WeaningRecordMapper weaningRecordMapper;
    private final WeaningRecordAllocationMapper weaningRecordAllocationMapper;
    private final BreedingPerformanceRecorder performanceRecorder;
    private final SettingService settingService;
    private final int commodityCageCapacity;

    public KitPlacementService(
        CageMapper cageMapper,
        RabbitMapper rabbitMapper,
        RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
        BatchRabbitMapper batchRabbitMapper,
        WeaningRecordMapper weaningRecordMapper,
        WeaningRecordAllocationMapper weaningRecordAllocationMapper,
        BreedingPerformanceRecorder performanceRecorder,
        SettingService settingService,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.cageMapper = cageMapper;
        this.rabbitMapper = rabbitMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.weaningRecordMapper = weaningRecordMapper;
        this.weaningRecordAllocationMapper = weaningRecordAllocationMapper;
        this.performanceRecorder = performanceRecorder;
        this.settingService = settingService;
        this.commodityCageCapacity = commodityCageCapacity;
    }

    /**
     * 执行落位。必须在调用方的事务内运行。
     *
     * @return 生成的仔兔 id；断奶数为 0 时返回空列表
     */
    public List<Long> place(KitPlacementCommand command) {
        validate(command);
        if (command.weanedCount() == 0) {
            // 全窝损失也要留痕：记录一条 0 只的分笼，绩效才不会把这一窝算成没发生过。
            recordWeaning(command, List.of());
            return List.of();
        }

        List<WeaningRecordAllocation> allocations = allocate(command);
        WeaningRecord record = recordWeaning(command, allocations);

        for (WeaningRecordAllocation allocation : allocations) {
            allocation.setWeaningRecordId(record.getId());
        }
        weaningRecordAllocationMapper.insertBatch(allocations);

        List<Rabbit> kits = occupyCagesAndBuildKits(command, allocations);
        insertKitsAndHydrateIds(kits);
        linkKitsToBatch(command, record, kits);
        return kits.stream().map(Rabbit::getId).toList();
    }

    private void validate(KitPlacementCommand command) {
        if (command.weanedCount() < 0) {
            throw new BizException(400, "断奶数量错误");
        }
        int male = orZero(command.maleCount());
        int female = orZero(command.femaleCount());
        if (male + female != 0 && male + female != command.weanedCount()) {
            throw new BizException(400, "公母数量之和需等于断奶数量");
        }
    }

    /** 指定笼位时只校验不挑选；未指定时自动选笼。 */
    private List<WeaningRecordAllocation> allocate(KitPlacementCommand command) {
        Long targetCageId = command.targetCageId() != null && command.targetCageId() > 0
            ? command.targetCageId()
            : null;
        if (targetCageId == null) {
            List<WeaningRecordAllocation> picked =
                pickCommodityCages(command.houseId(), command.weanedCount());
            if (picked.isEmpty()) {
                throw new BizException(400, "没有可用商品兔笼位");
            }
            return picked;
        }

        Cage cage = cageMapper.selectByIdForUpdate(command.houseId(), targetCageId);
        if (cage == null) {
            throw new BizException(400, "目标笼位不存在");
        }
        if (!Boolean.TRUE.equals(cage.getIsEnabled())) {
            throw new BizException(400, "目标笼位已停用");
        }
        if (!"0".equals(cage.getStatus()) && !"3".equals(cage.getStatus())) {
            throw new BizException(400, "目标笼位不是商品兔笼位");
        }
        if (orZero(cage.getRabbitCount()) + command.weanedCount() > commodityCageCapacity) {
            throw new BizException(400, "目标笼位容量不足");
        }
        WeaningRecordAllocation allocation = new WeaningRecordAllocation();
        allocation.setCageId(cage.getId());
        allocation.setAllocCount(command.weanedCount());
        return new ArrayList<>(List.of(allocation));
    }

    /** 先填空笼（status 0）再填半满笼（status 3），保持旧的落位偏好不变。 */
    private List<WeaningRecordAllocation> pickCommodityCages(Long houseId, int count) {
        List<Cage> candidates = cageMapper.selectCommodityCagesForUpdate(houseId);
        List<WeaningRecordAllocation> rows = new ArrayList<>();
        int left = count;
        for (String status : List.of("0", "3")) {
            for (Cage cage : candidates) {
                if (!status.equals(cage.getStatus())) {
                    continue;
                }
                left = allocToCage(cage, left, rows);
                if (left <= 0) {
                    return rows;
                }
            }
        }
        throw new BizException(400, "没有可用商品兔笼位");
    }

    private int allocToCage(Cage cage, int left, List<WeaningRecordAllocation> rows) {
        int remain = commodityCageCapacity - orZero(cage.getRabbitCount());
        if (remain <= 0) {
            return left;
        }
        int add = Math.min(remain, left);
        WeaningRecordAllocation allocation = new WeaningRecordAllocation();
        allocation.setCageId(cage.getId());
        allocation.setAllocCount(add);
        rows.add(allocation);
        return left - add;
    }

    private WeaningRecord recordWeaning(
        KitPlacementCommand command,
        List<WeaningRecordAllocation> allocations
    ) {
        WeaningRecord record = new WeaningRecord();
        record.setHouseId(command.houseId());
        record.setBatchId(command.batchId());
        record.setBreedingCycleId(command.cycleId());
        record.setRabbitId(command.motherRabbitId());
        record.setTargetCageId(command.targetCageId());
        record.setInCageId(allocations.isEmpty() ? null : allocations.get(0).getCageId());
        record.setWeaningDate(command.weaningDate());
        record.setWeaningCount(command.weanedCount());
        record.setWaitingCount(0);
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

    /**
     * 占笼并生成仔兔对象。
     *
     * <p>占笼用带容量判据的原子递增，影响行数不为 1 即判定并发冲突——
     * 这是「同一批笼位被两个分笼请求同时写入」时唯一可靠的防超员手段。
     */
    private List<Rabbit> occupyCagesAndBuildKits(
        KitPlacementCommand command,
        List<WeaningRecordAllocation> allocations
    ) {
        List<Rabbit> kits = new ArrayList<>();
        int index = 0;
        for (WeaningRecordAllocation allocation : allocations) {
            int add = orZero(allocation.getAllocCount());
            if (add <= 0) {
                continue;
            }
            if (cageMapper.incrementCommodityRabbitCountWithinCapacity(
                command.houseId(), allocation.getCageId(), add,
                commodityCageCapacity, command.operator()
            ) != 1) {
                throw new BizException(409, "笼位状态或容量已变化，请刷新后重试");
            }
            for (int i = 0; i < add; i++) {
                kits.add(newKit(command, allocation.getCageId(), index));
                index++;
            }
        }
        return kits;
    }

    private Rabbit newKit(KitPlacementCommand command, Long cageId, int index) {
        Rabbit kit = new Rabbit();
        kit.setHouseId(command.houseId());
        kit.setCageId(cageId);
        kit.setMotherId(command.motherRabbitId());
        kit.setFatherId(command.sireRabbitId());
        kit.setBirthBatchId(command.batchId());
        kit.setBirthCycleId(command.cycleId());
        kit.setType("2");
        kit.setGender(pickGender(index, orZero(command.maleCount()), orZero(command.femaleCount())));
        kit.setArrivalMethod("1");
        kit.setArrivalDate(command.weaningDate());
        kit.setWeight(command.avgWeight());
        kit.setGrowthStage("GROWING");
        kit.setIsActive(Boolean.TRUE);
        kit.setIsQuarantined(Boolean.FALSE);
        kit.setRequestId(ReproRequestIds.derive(command.requestId(), "kit-" + index));
        kit.setCreateBy(command.operator());
        kit.setUpdateBy(command.operator());
        return kit;
    }

    /** 不区分性别时一律记为母；给了公母数则前 maleCount 只记公。 */
    private static String pickGender(int index, int maleCount, int femaleCount) {
        if (maleCount + femaleCount == 0) {
            return "0";
        }
        return index < maleCount ? "1" : "0";
    }

    /**
     * 批量插入仔兔并回填主键。
     *
     * <p>MyBatis 的批量插入不回填自增 id，而后续的批次成员和状态历史都需要它，
     * 所以按 request_id 回查一次——request_id 在仔兔上是唯一的。
     */
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

    private void linkKitsToBatch(
        KitPlacementCommand command,
        WeaningRecord record,
        List<Rabbit> kits
    ) {
        if (command.batchId() == null) {
            // 散养母兔的仔兔不进批次；批次是标签，没有标签也能养。
            return;
        }
        GlobalSetting setting =
            settingService.getEffectiveSetting(command.userId(), command.houseId());
        Date saleDate = DateUtil.plusDays(command.weaningDate(), setting.getSaleDays());

        List<BatchRabbit> links = new ArrayList<>(kits.size());
        List<RabbitStatusHistory> histories = new ArrayList<>(kits.size());
        for (Rabbit kit : kits) {
            BatchRabbit link = new BatchRabbit();
            link.setBatchId(command.batchId());
            link.setRabbitId(kit.getId());
            link.setJoinReason("断奶");
            link.setBatchRole("fattening");
            link.setCurrentStatus("成长期");
            link.setLastEventDate(command.weaningDate());
            link.setNextEventDate(saleDate);
            link.setNextEventType("出售");
            link.setIsActive(Boolean.TRUE);
            link.setJoinDate(command.weaningDate());
            link.setCreateBy(command.operator());
            link.setUpdateBy(command.operator());
            links.add(link);

            RabbitStatusHistory history = new RabbitStatusHistory();
            history.setHouseId(command.houseId());
            history.setRabbitId(kit.getId());
            history.setBatchId(command.batchId());
            history.setFromStatus(null);
            history.setToStatus("成长期");
            history.setChangeTime(DateUtil.now());
            history.setReason("断奶生成仔兔");
            history.setRelatedRecordId(record.getId());
            history.setRelatedRecordTable("weaning_records");
            history.setCreateBy(command.operator());
            history.setUpdateBy(command.operator());
            histories.add(history);
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

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}

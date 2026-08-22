package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import java.util.Date;
import org.springframework.stereotype.Service;

/**
 * 繁殖绩效的唯一写入口。
 *
 * <p>接产与分笼都要累加绩效，抽出来是为了让「绩效在哪儿被写」只有一个答案。
 * 之前它散在 {@code BatchService.parturition} 和 {@code BatchService.weaning}
 * 两处，各自 ensureExists 一遍——这种成对调用一旦漏掉一半，统计就会因为
 * 没有行而静默丢数，而不是报错。
 *
 * <p>P5 计划把 {@code BreedingPerformanceRecalcService} 改为读 repro_events /
 * litters 重算；届时替换实现只需要动这一个类。
 */
@Service
public class BreedingPerformanceRecorder {

    private final BreedingPerformanceMapper breedingPerformanceMapper;

    public BreedingPerformanceRecorder(BreedingPerformanceMapper breedingPerformanceMapper) {
        this.breedingPerformanceMapper = breedingPerformanceMapper;
    }

    /** 接产：累加窝数与产仔数。失败产也要记，否则分娩成功率会虚高。 */
    public void recordParturition(
        Long houseId, Long motherRabbitId, int totalKits, int liveKits, Date birthDate
    ) {
        breedingPerformanceMapper.ensureExists(houseId, motherRabbitId);
        breedingPerformanceMapper.addParturition(
            houseId, motherRabbitId, totalKits, liveKits, birthDate
        );
    }

    /** 分笼：累加断奶数。 */
    public void recordWeaning(Long houseId, Long motherRabbitId, int weanedCount) {
        breedingPerformanceMapper.ensureExists(houseId, motherRabbitId);
        breedingPerformanceMapper.addWeaning(houseId, motherRabbitId, weanedCount);
    }
}

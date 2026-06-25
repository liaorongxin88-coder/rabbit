package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.dto.BreedingSummary;
import com.rabbit.app.modules.batch.entity.BreedingPerformance;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BreedingPerformanceMapper {
    int ensureExists(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId);

    int incBreedingResult(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("success") boolean success);

    int addParturition(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("totalKits") int totalKits, @Param("liveKits") int liveKits, @Param("birthDate") Date birthDate);

    int addWeaning(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("weaned") int weaned);

    List<com.rabbit.app.modules.batch.entity.BreedingPerformance> selectByHouse(@Param("houseId") Long houseId);

    com.rabbit.app.modules.batch.entity.BreedingPerformance selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId);

    com.rabbit.app.modules.batch.dto.BreedingSummary selectSummary(@Param("houseId") Long houseId);

    int upsertRecalc(@Param("houseId") Long houseId,
                     @Param("rabbitId") Long rabbitId,
                     @Param("totalLitters") int totalLitters,
                     @Param("totalKits") int totalKits,
                     @Param("totalLiveKits") int totalLiveKits,
                     @Param("totalWeaned") int totalWeaned,
                     @Param("successBreedingCount") int successBreedingCount,
                     @Param("failedBreedingCount") int failedBreedingCount,
                     @Param("avgLitterSize") BigDecimal avgLitterSize,
                     @Param("avgWeaningSize") BigDecimal avgWeaningSize,
                     @Param("lastLitterDate") Date lastLitterDate);
}

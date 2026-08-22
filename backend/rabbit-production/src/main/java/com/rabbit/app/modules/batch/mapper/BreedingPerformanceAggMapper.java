package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.dto.BreedingPerformanceAggRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BreedingPerformanceAggMapper {
    List<BreedingPerformanceAggRow> selectParturitionAggByHouse(@Param("houseId") Long houseId);

    List<BreedingPerformanceAggRow> selectWeaningAggByHouse(@Param("houseId") Long houseId);

    List<BreedingPerformanceAggRow> selectPregnancyAggByHouse(@Param("houseId") Long houseId);
}


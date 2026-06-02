package com.rabbit.app.mapper;

import com.rabbit.app.dto.BreedingPerformanceAggRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BreedingPerformanceAggMapper {
    List<BreedingPerformanceAggRow> selectParturitionAggByHouse(@Param("houseId") Long houseId);

    List<BreedingPerformanceAggRow> selectWeaningAggByHouse(@Param("houseId") Long houseId);

    List<BreedingPerformanceAggRow> selectPregnancyAggByHouse(@Param("houseId") Long houseId);
}


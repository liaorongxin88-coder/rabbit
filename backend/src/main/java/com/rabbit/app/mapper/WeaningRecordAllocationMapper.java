package com.rabbit.app.mapper;

import com.rabbit.app.model.WeaningRecordAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WeaningRecordAllocationMapper {
    int insertBatch(@Param("rows") List<WeaningRecordAllocation> rows);

    List<WeaningRecordAllocation> selectByWeaningRecordId(@Param("weaningRecordId") Long weaningRecordId);
}


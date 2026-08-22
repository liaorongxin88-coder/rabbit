package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WeaningRecordAllocationMapper {
    int insertBatch(@Param("rows") List<WeaningRecordAllocation> rows);

    List<WeaningRecordAllocation> selectByWeaningRecordId(@Param("weaningRecordId") Long weaningRecordId);
}


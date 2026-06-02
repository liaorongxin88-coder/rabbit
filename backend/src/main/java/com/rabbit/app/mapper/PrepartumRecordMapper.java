package com.rabbit.app.mapper;

import com.rabbit.app.model.PrepartumRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PrepartumRecordMapper {
    int insert(PrepartumRecord record);

    List<PrepartumRecord> selectByHouse(@Param("houseId") Long houseId,
                                        @Param("batchId") Long batchId,
                                        @Param("rabbitId") Long rabbitId);
}

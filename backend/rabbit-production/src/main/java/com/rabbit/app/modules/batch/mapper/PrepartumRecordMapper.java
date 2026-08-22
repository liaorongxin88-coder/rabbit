package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.entity.PrepartumRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PrepartumRecordMapper {
    int insert(PrepartumRecord record);

    List<PrepartumRecord> selectByHouse(@Param("houseId") Long houseId,
                                        @Param("batchId") Long batchId,
                                        @Param("rabbitId") Long rabbitId);
}

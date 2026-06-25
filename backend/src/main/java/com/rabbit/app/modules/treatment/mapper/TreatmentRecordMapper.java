package com.rabbit.app.modules.treatment.mapper;

import com.rabbit.app.modules.treatment.entity.TreatmentRecord;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TreatmentRecordMapper {
    int insert(TreatmentRecord r);

    TreatmentRecord selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    TreatmentRecord selectByReq(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("requestId") String requestId);

    int updateStatus(@Param("houseId") Long houseId, @Param("id") Long id, @Param("status") String status, @Param("updateBy") String updateBy);

    List<TreatmentRecord> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);

    List<TreatmentRecord> selectDueReviewsByHouse(@Param("houseId") Long houseId, @Param("now") Date now);
}

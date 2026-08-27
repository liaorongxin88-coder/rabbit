package com.rabbit.app.modules.vaccination.mapper;

import com.rabbit.app.modules.vaccination.entity.VaccinationRecord;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VaccinationRecordMapper {

    /** 批量落库；一次最多 500 行，与请求上限对齐。 */
    int insertBatch(@Param("items") List<VaccinationRecord> items);

    /** 幂等回查：整批共用一个 requestId，按 requestId 取回全批。 */
    List<VaccinationRecord> selectByReq(@Param("houseId") Long houseId, @Param("requestId") String requestId);

    List<VaccinationRecord> selectByRabbit(@Param("houseId") Long houseId,
                                           @Param("rabbitId") Long rabbitId,
                                           @Param("limit") int limit);

    /**
     * 待接种列表：只看仍欠一针且已到期的记录，且兔只仍在场。
     *
     * <p>与 TreatmentRecordMapper.selectDueReviewsByHouse 同形。
     */
    List<VaccinationRecord> selectDueByHouse(@Param("houseId") Long houseId, @Param("now") Date now);

    /**
     * 同一疫苗补种后，把该兔早先仍标记 SCHEDULED 的记录收口为 DONE。
     *
     * <p>否则补过的针会永远挂在待接种列表里。
     */
    int markSupersededDone(@Param("houseId") Long houseId,
                           @Param("rabbitIds") List<Long> rabbitIds,
                           @Param("vaccineName") String vaccineName,
                           @Param("excludeRequestId") String excludeRequestId,
                           @Param("updateBy") String updateBy);
}

package com.rabbit.app.modules.report.mapper;

import com.rabbit.app.modules.batch.dto.BreedingSummary;
import com.rabbit.app.modules.report.dto.MonthlyCount;
import com.rabbit.app.modules.report.dto.RabbitDashboardStats;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardReportMapper {
    RabbitDashboardStats selectRabbitStats(@Param("houseIds") List<Long> houseIds,
                                            @Param("batchId") Long batchId);

    Integer countActiveBreedingMothers(@Param("houseIds") List<Long> houseIds,
                                        @Param("batchId") Long batchId);

    BreedingSummary selectBreedingSummary(@Param("houseIds") List<Long> houseIds,
                                           @Param("batchId") Long batchId);

    Integer sumCurrentNursingKits(@Param("houseIds") List<Long> houseIds,
                                  @Param("batchId") Long batchId);

    List<MonthlyCount> selectMonthlyBirths(@Param("houseIds") List<Long> houseIds,
                                           @Param("batchId") Long batchId,
                                           @Param("from") Date from,
                                           @Param("to") Date to);

    List<MonthlyCount> selectMonthlyWeaned(@Param("houseIds") List<Long> houseIds,
                                           @Param("batchId") Long batchId,
                                           @Param("from") Date from,
                                           @Param("to") Date to);
}

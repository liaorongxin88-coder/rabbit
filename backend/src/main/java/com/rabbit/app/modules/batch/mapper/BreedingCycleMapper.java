package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.entity.BreedingCycle;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BreedingCycleMapper {
    int insert(BreedingCycle cycle);

    int update(
        @Param("cycle") BreedingCycle cycle,
        @Param("expectedStatus") String expectedStatus
    );

    BreedingCycle selectById(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("id") Long id
    );

    BreedingCycle selectByIdForUpdate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("id") Long id
    );

    List<BreedingCycle> selectByBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("activeOnly") Boolean activeOnly
    );

    BreedingCycle selectLatestByStatusesForUpdate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("statuses") List<String> statuses
    );

    BreedingCycle selectOldestByStatusesForUpdate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("statuses") List<String> statuses
    );

    BreedingCycle selectDisplayOpen(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    BreedingCycle selectLatest(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    int countOpenGestations(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    int sumCurrentNursingKits(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    int countNursingLitters(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    int closeOverlaps(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("litterCycleNo") Integer litterCycleNo,
        @Param("overlapEndDate") Date overlapEndDate,
        @Param("updateBy") String updateBy
    );

    int closeOpenByBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("closedAt") Date closedAt,
        @Param("reason") String reason,
        @Param("updateBy") String updateBy,
        @Param("limit") int limit
    );

    int countOpenByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId);

    int countOpenByMother(@Param("houseId") Long houseId,
                          @Param("batchId") Long batchId,
                          @Param("motherRabbitId") Long motherRabbitId);

    int closeOpenByMother(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("closedAt") Date closedAt,
        @Param("reason") String reason,
        @Param("updateBy") String updateBy
    );

    List<BreedingCycle> selectDueEventsByHouse(
        @Param("houseId") Long houseId,
        @Param("today") Date today,
        @Param("onlyUnnotified") boolean onlyUnnotified
    );

    int markDueEventsAsNotified(
        @Param("houseId") Long houseId,
        @Param("today") Date today,
        @Param("updateBy") String updateBy,
        @Param("limit") int limit
    );
}

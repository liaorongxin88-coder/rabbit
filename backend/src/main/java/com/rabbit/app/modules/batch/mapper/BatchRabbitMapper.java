package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BatchRabbitMapper {
    int insertBatch(@Param("list") List<BatchRabbit> list);

    BatchRabbit selectActiveByBatchAndRabbit(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("rabbitId") Long rabbitId);

    List<BatchRabbit> selectActiveByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId);

    int updateStatusAndEvent(@Param("houseId") Long houseId,
                             @Param("id") Long id,
                             @Param("currentStatus") String currentStatus,
                             @Param("lastEventDate") Date lastEventDate,
                             @Param("nextEventDate") Date nextEventDate,
                             @Param("nextEventType") String nextEventType,
                             @Param("maleRabbitId") Long maleRabbitId,
                             @Param("updateBy") String updateBy);

    int updateStatusAndEventIfStatus(@Param("houseId") Long houseId,
                                     @Param("id") Long id,
                                     @Param("fromStatus") String fromStatus,
                                     @Param("currentStatus") String currentStatus,
                                     @Param("lastEventDate") Date lastEventDate,
                                     @Param("nextEventDate") Date nextEventDate,
                                     @Param("nextEventType") String nextEventType,
                                     @Param("maleRabbitId") Long maleRabbitId,
                                     @Param("updateBy") String updateBy);

    int deactivate(@Param("houseId") Long houseId, @Param("id") Long id, @Param("exitDate") Date exitDate, @Param("remark") String remark, @Param("updateBy") String updateBy);

    int deactivateIfActive(@Param("houseId") Long houseId, @Param("id") Long id, @Param("exitDate") Date exitDate, @Param("remark") String remark, @Param("updateBy") String updateBy);

    int deactivateByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("exitDate") Date exitDate, @Param("remark") String remark, @Param("updateBy") String updateBy);

    int updateNextEvent(@Param("houseId") Long houseId,
                        @Param("id") Long id,
                        @Param("nextEventDate") Date nextEventDate,
                        @Param("nextEventType") String nextEventType,
                        @Param("updateBy") String updateBy);

    int updateNextEventIfStatus(@Param("houseId") Long houseId,
                                @Param("id") Long id,
                                @Param("fromStatus") String fromStatus,
                                @Param("nextEventDate") Date nextEventDate,
                                @Param("nextEventType") String nextEventType,
                                @Param("updateBy") String updateBy);

    List<BatchRabbit> selectDueEventsByHouse(@Param("houseId") Long houseId, @Param("today") Date today);

    List<BatchRabbit> selectDueUnnotifiedEventsByHouse(@Param("houseId") Long houseId, @Param("today") Date today);

    int markDueEventsAsNotified(@Param("houseId") Long houseId, @Param("today") Date today, @Param("updateBy") String updateBy);

    int countActiveByBatch(@Param("batchId") Long batchId);

    List<BatchRabbitItem> selectItemsByBatch(@Param("batchId") Long batchId, @Param("role") String role, @Param("active") Boolean active);
}

package com.rabbit.app.modules.outbound.mapper;

import com.rabbit.app.modules.outbound.entity.OutboundTaskItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboundTaskItemMapper {
    int deleteByTaskLimited(@Param("taskId") String taskId, @Param("limit") int limit);
    int insertBatch(@Param("list") List<OutboundTaskItem> items);
    List<OutboundTaskItem> selectByTask(@Param("taskId") String taskId);
}

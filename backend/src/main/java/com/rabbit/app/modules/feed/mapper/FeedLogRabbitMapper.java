package com.rabbit.app.modules.feed.mapper;

import com.rabbit.app.modules.feed.entity.FeedLogRabbit;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FeedLogRabbitMapper {
    int insertBatch(@Param("list") List<FeedLogRabbit> list);
}

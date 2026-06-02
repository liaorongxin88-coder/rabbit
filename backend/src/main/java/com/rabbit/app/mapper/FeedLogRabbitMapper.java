package com.rabbit.app.mapper;

import com.rabbit.app.model.FeedLogRabbit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedLogRabbitMapper {
    int insertBatch(@Param("list") List<FeedLogRabbit> list);
}

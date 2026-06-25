package com.rabbit.app.modules.feed.mapper;

import com.rabbit.app.modules.feed.dto.FeedSummary;
import com.rabbit.app.modules.feed.entity.FeedLog;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FeedLogMapper {
    int insert(FeedLog log);

    List<FeedLog> selectByHouse(@Param("houseId") Long houseId);

    List<FeedLog> selectPageByHouse(@Param("houseId") Long houseId,
                                    @Param("from") Date from,
                                    @Param("to") Date to,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    FeedSummary selectSummary(@Param("houseId") Long houseId, @Param("from") Date from, @Param("to") Date to);

    FeedLog selectLatestByCage(@Param("houseId") Long houseId, @Param("cageId") Long cageId);

    List<FeedLog> selectWithoutRabbits(@Param("houseId") Long houseId, @Param("limit") int limit);
}

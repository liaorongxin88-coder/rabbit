package com.rabbit.app.modules.cage.mapper;

import com.rabbit.app.modules.cage.entity.Cage;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CageMapper {
    int insertBatch(@Param("list") List<Cage> list);

    int insert(Cage cage);

    Cage selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    List<Cage> selectByHouseId(@Param("houseId") Long houseId);

    int updateBasic(@Param("houseId") Long houseId, @Param("id") Long id, @Param("cageNumber") String cageNumber, @Param("remark") String remark, @Param("isEnabled") boolean isEnabled, @Param("updateBy") String updateBy);

    int deleteById(@Param("houseId") Long houseId, @Param("id") Long id);

    int setRabbitCount(@Param("houseId") Long houseId, @Param("id") Long id, @Param("rabbitCount") int rabbitCount, @Param("updateBy") String updateBy);

    int updateRabbitCountAndStatus(@Param("houseId") Long houseId, @Param("id") Long id, @Param("rabbitCount") int rabbitCount, @Param("status") String status, @Param("updateBy") String updateBy);

    int incRabbitCount(@Param("houseId") Long houseId, @Param("id") Long id, @Param("delta") int delta, @Param("updateBy") String updateBy);

    int updateIsFed(@Param("houseId") Long houseId, @Param("id") Long id, @Param("isFed") boolean isFed, @Param("updateBy") String updateBy);

    int resetAllFed(@Param("houseId") Long houseId, @Param("updateBy") String updateBy);
}

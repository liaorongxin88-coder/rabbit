package com.rabbit.app.modules.file.mapper;

import com.rabbit.app.modules.file.entity.BusinessFile;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessFileMapper {
    int insert(BusinessFile file);

    BusinessFile selectById(@Param("houseId") Long houseId, @Param("id") String id);

    BusinessFile selectByHouseAndSha(@Param("houseId") Long houseId, @Param("sha256") String sha256);

    int countByIds(@Param("houseId") Long houseId, @Param("ids") List<String> ids);
}

package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.admin.dto.AdminBusinessUserItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminBusinessUserMapper {
    long count(@Param("keyword") String keyword,
               @Param("status") String status);

    List<AdminBusinessUserItem> selectPage(@Param("keyword") String keyword,
                                           @Param("status") String status,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    AdminBusinessUserItem selectById(@Param("userId") Long userId);

    List<Long> selectOwnedHouseIdsForUpdate(@Param("userId") Long userId);

    long countNonDeletedHousesWhereSoleOwner(@Param("userId") Long userId);
}

package com.rabbit.app.modules.appupdate.mapper;

import com.rabbit.app.modules.appupdate.entity.AppRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppReleaseMapper {
    AppRelease selectLatestPublishedNewer(
            @Param("platform") String platform,
            @Param("buildNumber") Long buildNumber
    );

    AppRelease selectById(@Param("id") Long id);

    AppRelease selectByRequestId(@Param("requestId") String requestId);

    AppRelease selectByPlatformAndBuild(
            @Param("platform") String platform,
            @Param("buildNumber") Long buildNumber
    );

    int insert(AppRelease release);

    int updatePublishedById(
            @Param("id") Long id,
            @Param("published") Boolean published,
            @Param("updateBy") String updateBy
    );
}

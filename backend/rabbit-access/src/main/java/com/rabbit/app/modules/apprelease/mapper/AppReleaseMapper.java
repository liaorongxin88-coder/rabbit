package com.rabbit.app.modules.apprelease.mapper;

import com.rabbit.app.modules.apprelease.entity.AppRelease;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppReleaseMapper {
    int insert(AppRelease release);

    AppRelease selectById(@Param("id") String id);

    AppRelease selectByOperatorAndRequestId(
            @Param("createBy") String createBy,
            @Param("requestId") String requestId
    );

    AppRelease selectByChannelAndVersionCode(
            @Param("channel") String channel,
            @Param("versionCode") int versionCode
    );

    AppRelease selectLatestPublished(@Param("channel") String channel);

    AppRelease selectLatestPublishedNewerThan(
            @Param("channel") String channel,
            @Param("versionCode") int versionCode
    );

    int countForcedPublishedNewerThan(
            @Param("channel") String channel,
            @Param("versionCode") int versionCode
    );

    long count(
            @Param("channel") String channel,
            @Param("status") String status
    );

    List<AppRelease> selectPage(
            @Param("channel") String channel,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("publishedAt") java.util.Date publishedAt,
            @Param("updateBy") String updateBy
    );

    int updateMeta(
            @Param("id") String id,
            @Param("releaseNotes") String releaseNotes,
            @Param("forceUpdate") boolean forceUpdate,
            @Param("updateBy") String updateBy
    );

}

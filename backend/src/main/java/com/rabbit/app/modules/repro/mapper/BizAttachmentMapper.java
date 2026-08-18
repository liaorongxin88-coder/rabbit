package com.rabbit.app.modules.repro.mapper;

import com.rabbit.app.modules.repro.entity.BizAttachment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** biz_attachments 的读写口。 */
@Mapper
public interface BizAttachmentMapper {

    /** uk_ba_biz_file 去重：同一业务对象重复挂同一 file_id 视为幂等重放，忽略即可。 */
    int insertIgnore(BizAttachment attachment);

    List<BizAttachment> selectByBiz(
        @Param("houseId") Long houseId,
        @Param("bizType") String bizType,
        @Param("bizId") Long bizId
    );
}

package com.rabbit.app.modules.repro.mapper;

import com.rabbit.app.modules.batch.entity.ParturitionRecord;
import com.rabbit.app.modules.batch.entity.PregnancyCheckRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 把生产事件库还原成"分娩记录""摸胎记录"两张历史清单。
 *
 * <p>旧实现各自写一张记录表，doe-breeding-v2 删除旧写入路径后那两张表就停更了，
 * 而查询接口仍在照常返回——用户看到的是冻结在迁移当天的数据，比报错更难发现。
 * 现在统一从 repro_events 读：事件库是唯一权威来源，写一次就不会再有第二处漏写。
 *
 * <p>返回的仍是原来的实体形状，调用方无需改动；断奶记录不在此列，
 * 因为 weaning_records 承载笼位分配数据、至今仍在写入。
 */
@Mapper
public interface ReproRecordQueryMapper {
    List<ParturitionRecord> selectParturitionsByBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("limit") int limit
    );

    List<ParturitionRecord> selectParturitionsByRabbit(
        @Param("houseId") Long houseId,
        @Param("rabbitId") Long rabbitId,
        @Param("limit") int limit
    );

    List<PregnancyCheckRecord> selectPalpationsByBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("limit") int limit
    );

    List<PregnancyCheckRecord> selectPalpationsByRabbit(
        @Param("houseId") Long houseId,
        @Param("rabbitId") Long rabbitId,
        @Param("limit") int limit
    );
}

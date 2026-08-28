package com.rabbit.app.modules.rabbit.mapper;

import com.rabbit.app.modules.rabbit.entity.RabbitCageTransferRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RabbitCageTransferRecordMapper {
    int insert(RabbitCageTransferRecord record);
}

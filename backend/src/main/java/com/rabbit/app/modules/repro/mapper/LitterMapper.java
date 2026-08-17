package com.rabbit.app.modules.repro.mapper;

import com.rabbit.app.modules.repro.entity.Litter;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** litters 的读写口。 */
@Mapper
public interface LitterMapper {

    int insert(Litter litter);

    Litter selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    Litter selectByIdForUpdate(@Param("houseId") Long houseId, @Param("id") Long id);

    /** uk_lt_cycle 保证一个周期至多一窝。 */
    Litter selectByCycleId(@Param("houseId") Long houseId, @Param("cycleId") Long cycleId);

    Litter selectByCycleIdForUpdate(@Param("houseId") Long houseId, @Param("cycleId") Long cycleId);

    int update(Litter litter);

    /** 该母兔在哺的窝；血配时与管线周期并行存在。 */
    List<Litter> selectNursingByMother(
        @Param("houseId") Long houseId,
        @Param("motherRabbitId") Long motherRabbitId
    );
}

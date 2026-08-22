package com.rabbit.app.modules.rabbit.mapper;

import com.rabbit.app.modules.cage.dto.CageCountRow;
import com.rabbit.app.modules.rabbit.dto.RabbitCageRow;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RabbitMapper {
    int insert(Rabbit rabbit);

    int insertBatch(@Param("list") List<Rabbit> rabbits);

    Rabbit selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    List<Rabbit> selectByIdsForUpdate(@Param("houseId") Long houseId, @Param("ids") List<Long> ids);

    List<Rabbit> selectByHouseAndRequestIds(@Param("houseId") Long houseId,
                                            @Param("requestIds") List<String> requestIds);

    Rabbit selectByHouseAndRequestId(@Param("houseId") Long houseId, @Param("requestId") String requestId);

    List<Rabbit> selectByHouse(@Param("houseId") Long houseId,
                               @Param("cageId") Long cageId,
                               @Param("type") String type,
                               @Param("active") Boolean active);

    List<Rabbit> selectPageByHouse(@Param("houseId") Long houseId,
                                   @Param("cageId") Long cageId,
                                   @Param("type") String type,
                                   @Param("active") Boolean active,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    List<Rabbit> selectActiveByCageForUpdate(@Param("houseId") Long houseId, @Param("cageId") Long cageId);

    int updateTypeAndCage(@Param("houseId") Long houseId, @Param("id") Long id, @Param("type") String type, @Param("cageId") Long cageId, @Param("updateBy") String updateBy);

    int promoteReplacement(
        @Param("houseId") Long houseId,
        @Param("id") Long id,
        @Param("reproductiveStage") String reproductiveStage,
        @Param("updateBy") String updateBy
    );

    int updateCageIfActive(@Param("houseId") Long houseId,
                           @Param("id") Long id,
                           @Param("cageId") Long cageId,
                           @Param("updateBy") String updateBy);

    /**
     * 对调换笼的中间态：把兔子临时置为离场，让唯一键 uk_rabbits_house_active_breeding_cage
     * 让出笼位。只允许 {@code RabbitService#transferCage} 在同一事务里配合
     * {@link #restoreFromCageSwap} 使用，落单调用会造出一只查不到的活兔。
     */
    int parkForCageSwap(@Param("houseId") Long houseId,
                        @Param("id") Long id,
                        @Param("updateBy") String updateBy);

    /** 与 {@link #parkForCageSwap} 成对：带着新笼位复位在栏状态。 */
    int restoreFromCageSwap(@Param("houseId") Long houseId,
                            @Param("id") Long id,
                            @Param("cageId") Long cageId,
                            @Param("updateBy") String updateBy);

    int updateDeparture(@Param("houseId") Long houseId, @Param("id") Long id, @Param("departureDate") java.util.Date departureDate, @Param("departureReason") String departureReason, @Param("updateBy") String updateBy);

    int updateDepartureIfVersion(@Param("houseId") Long houseId, @Param("id") Long id,
                                 @Param("stateVersion") Long stateVersion,
                                 @Param("departureDate") java.util.Date departureDate,
                                 @Param("departureReason") String departureReason,
                                 @Param("updateBy") String updateBy);

    int bumpStateVersion(@Param("houseId") Long houseId, @Param("id") Long id,
                         @Param("updateBy") String updateBy);

    int bumpStateVersionIfActive(@Param("houseId") Long houseId, @Param("id") Long id,
                                 @Param("updateBy") String updateBy);

    int updateQuarantine(@Param("houseId") Long houseId,
                         @Param("id") Long id,
                         @Param("isQuarantined") Boolean isQuarantined,
                         @Param("quarantineTime") java.util.Date quarantineTime,
                         @Param("quarantineReason") String quarantineReason,
                         @Param("updateBy") String updateBy);

    int updateWeight(@Param("houseId") Long houseId, @Param("id") Long id, @Param("weight") Double weight, @Param("updateBy") String updateBy);

    int updateBaseInfo(@Param("houseId") Long houseId,
                       @Param("id") Long id,
                       @Param("cageId") Long cageId,
                       @Param("motherId") Long motherId,
                       @Param("breed") String breed,
                       @Param("arrivalMethod") String arrivalMethod,
                       @Param("arrivalDate") java.util.Date arrivalDate,
                       @Param("weight") Double weight,
                       @Param("growthStage") String growthStage,
                       @Param("reproductiveStage") String reproductiveStage,
                       @Param("updateBy") String updateBy);

    int countActiveByCage(@Param("houseId") Long houseId, @Param("cageId") Long cageId);

    List<CageCountRow> selectActiveCountsByCage(@Param("houseId") Long houseId);

    List<RabbitCageRow> selectCageIdsByIds(@Param("houseId") Long houseId, @Param("ids") List<Long> ids);

    int advanceCommodityGrowthStages(
        @Param("houseId") Long houseId,
        @Param("now") java.util.Date now,
        @Param("updateBy") String updateBy
    );
}

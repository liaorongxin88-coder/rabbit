package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.rabbit.dto.RangeRabbitEntryRequest;
import com.rabbit.app.modules.rabbit.dto.RangeRabbitEntryResult;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RangeRabbitEntryService {
    private static final int MAX_RANGE_SLOTS = 500;
    private static final int MAX_RABBITS_PER_REQUEST = 1000;

    private final CageMapper cageMapper;
    private final RabbitMapper rabbitMapper;
    private final RabbitService rabbitService;
    private final int commodityCageCapacity;

    public RangeRabbitEntryService(
        CageMapper cageMapper,
        RabbitMapper rabbitMapper,
        RabbitService rabbitService,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.cageMapper = cageMapper;
        this.rabbitMapper = rabbitMapper;
        this.rabbitService = rabbitService;
        this.commodityCageCapacity = commodityCageCapacity <= 0 ? 10 : commodityCageCapacity;
    }

    @Transactional
    public RangeRabbitEntryResult create(Long userId, Long houseId, RangeRabbitEntryRequest request) {
        Range range = Range.of(request);
        int requestedSlotCount = range.slotCount();
        int rabbitsPerCage = request.getRabbitsPerCage();
        if (requestedSlotCount > MAX_RANGE_SLOTS) {
            throw new BizException(400, "范围最多包含 " + MAX_RANGE_SLOTS + " 个笼位，请分批录入");
        }
        if ((long) requestedSlotCount * rabbitsPerCage > MAX_RABBITS_PER_REQUEST) {
            throw new BizException(400, "单次最多录入 " + MAX_RABBITS_PER_REQUEST + " 只兔，请降低范围或每笼数量");
        }
        if (!"2".equals(request.getType()) && rabbitsPerCage != 1) {
            throw new BizException(400, "种兔和后备兔每笼只能录入 1 只");
        }

        List<Cage> allCages = cageMapper.selectByHouseId(houseId);
        List<Cage> rangedCages = new ArrayList<>();
        int unplacedCageCount = 0;
        for (Cage cage : allCages) {
            Coordinate coordinate = Coordinate.of(cage);
            if (coordinate == null) {
                unplacedCageCount++;
            } else if (range.contains(coordinate)) {
                rangedCages.add(cage);
            }
        }

        Map<String, List<Cage>> cagesByCoordinate = new HashMap<>();
        for (Cage cage : rangedCages) {
            cagesByCoordinate.computeIfAbsent(Coordinate.of(cage).key(), ignored -> new ArrayList<>()).add(cage);
        }

        List<RangeRabbitEntryResult.SkippedCage> skipped = new ArrayList<>();
        List<Long> lockIds = new ArrayList<>();
        for (List<Cage> cages : cagesByCoordinate.values()) {
            if (cages.size() > 1) {
                for (Cage cage : cages) {
                    skipped.add(skipped(cage, "坐标重复，先修正笼位坐标"));
                }
            } else {
                lockIds.add(cages.get(0).getId());
            }
        }

        List<Cage> lockedCages = cageMapper.selectByIdsForUpdate(houseId, lockIds);
        Map<Long, Cage> lockedById = new HashMap<>();
        for (Cage cage : lockedCages) {
            lockedById.put(cage.getId(), cage);
        }

        int enteredCageCount = 0;
        int enteredRabbitCount = 0;
        int replayedCageCount = 0;
        for (Long cageId : lockIds) {
            Cage cage = lockedById.get(cageId);
            if (cage == null || !range.contains(Coordinate.of(cage))) {
                continue;
            }

            List<String> entryRequestIds = entryRequestIds(request.getRequestId(), cageId, rabbitsPerCage);
            List<Rabbit> existing = rabbitMapper.selectByHouseAndRequestIds(houseId, entryRequestIds);
            if (existing.size() == rabbitsPerCage) {
                replayedCageCount++;
                enteredRabbitCount += rabbitsPerCage;
                continue;
            }
            if (!existing.isEmpty()) {
                skipped.add(skipped(cage, "同一请求已部分完成，请刷新后检查"));
                continue;
            }

            String rejection = rejection(cage, request.getType(), rabbitsPerCage);
            if (rejection != null) {
                skipped.add(skipped(cage, rejection));
                continue;
            }

            try {
                for (int index = 0; index < rabbitsPerCage; index++) {
                    rabbitService.createRabbit(
                        userId,
                        houseId,
                        rabbit(request, cageId),
                        reproEntry(request),
                        entryRequestIds.get(index)
                    );
                }
                enteredCageCount++;
                enteredRabbitCount += rabbitsPerCage;
            } catch (BizException error) {
                skipped.add(skipped(cage, error.getMessage()));
            }
        }

        return new RangeRabbitEntryResult(
            requestedSlotCount,
            Math.max(0, requestedSlotCount - cagesByCoordinate.size()),
            unplacedCageCount,
            enteredCageCount,
            enteredRabbitCount,
            replayedCageCount,
            List.copyOf(skipped)
        );
    }

    private Rabbit rabbit(RangeRabbitEntryRequest request, Long cageId) {
        Rabbit rabbit = new Rabbit();
        rabbit.setCageId(cageId);
        rabbit.setMotherId(request.getMotherId());
        rabbit.setType(request.getType());
        rabbit.setGender(request.getGender());
        rabbit.setBreed(request.getBreed());
        rabbit.setArrivalMethod(request.getArrivalMethod());
        rabbit.setArrivalDate(request.getArrivalDate());
        rabbit.setWeight(request.getWeight());
        rabbit.setGrowthStage(request.getGrowthStage());
        rabbit.setGrowthStageEnteredAt(request.getGrowthStageEnteredAt());
        rabbit.setReproductiveStage(request.getReproductiveStage());
        return rabbit;
    }

    private RabbitService.ReproEntry reproEntry(RangeRabbitEntryRequest request) {
        return new RabbitService.ReproEntry(
            request.getReproStage(),
            request.getBatchId(),
            request.getStageEnteredAt(),
            request.getMatingDate(),
            request.getBirthDate(),
            request.getLiveKits()
        );
    }

    private String rejection(Cage cage, String rabbitType, int rabbitsPerCage) {
        if (!Boolean.TRUE.equals(cage.getIsEnabled())) {
            return "笼位已停用";
        }
        String targetStatus = targetStatus(rabbitType);
        String status = cage.getStatus() == null ? "0" : cage.getStatus();
        if (!"0".equals(status) && !targetStatus.equals(status)) {
            return "笼位用途不匹配";
        }
        int currentCount = cage.getRabbitCount() == null ? 0 : cage.getRabbitCount();
        if ("1".equals(status) || "2".equals(status)) {
            return currentCount + rabbitsPerCage > 1 ? "单兔笼已满" : null;
        }
        if ("2".equals(rabbitType) && currentCount + rabbitsPerCage > commodityCageCapacity) {
            return "商品兔笼已满（最多 " + commodityCageCapacity + " 只）";
        }
        return null;
    }

    private String targetStatus(String rabbitType) {
        return switch (rabbitType) {
            case "0" -> "1";
            case "1" -> "2";
            case "2" -> "3";
            default -> throw new BizException(400, "兔只类型不支持");
        };
    }

    private List<String> entryRequestIds(String requestId, Long cageId, int rabbitsPerCage) {
        List<String> ids = new ArrayList<>();
        for (int index = 1; index <= rabbitsPerCage; index++) {
            ids.add(UUID.nameUUIDFromBytes(
                (requestId + ":" + cageId + ":" + index).getBytes(StandardCharsets.UTF_8)
            ).toString());
        }
        return ids;
    }

    private RangeRabbitEntryResult.SkippedCage skipped(Cage cage, String reason) {
        return new RangeRabbitEntryResult.SkippedCage(cage.getId(), cage.getCageNumber(), reason);
    }

    private record Range(int rowStart, int rowEnd, int positionStart, int positionEnd, int layerStart, int layerEnd) {
        static Range of(RangeRabbitEntryRequest request) {
            return new Range(
                Math.min(request.getRowStart(), request.getRowEnd()),
                Math.max(request.getRowStart(), request.getRowEnd()),
                Math.min(request.getPositionStart(), request.getPositionEnd()),
                Math.max(request.getPositionStart(), request.getPositionEnd()),
                Math.min(request.getLayerStart(), request.getLayerEnd()),
                Math.max(request.getLayerStart(), request.getLayerEnd())
            );
        }

        boolean contains(Coordinate coordinate) {
            return coordinate != null
                && coordinate.row >= rowStart && coordinate.row <= rowEnd
                && coordinate.position >= positionStart && coordinate.position <= positionEnd
                && coordinate.layer >= layerStart && coordinate.layer <= layerEnd;
        }

        int slotCount() {
            long rows = (long) rowEnd - rowStart + 1;
            long positions = (long) positionEnd - positionStart + 1;
            long layers = (long) layerEnd - layerStart + 1;
            long slots = rows * positions * layers;
            return slots > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) slots;
        }
    }

    private record Coordinate(int row, int position, int layer) {
        static Coordinate of(Cage cage) {
            if (cage == null || cage.getLayerIndex() == null || cage.getPositionIndex() == null
                || cage.getLayerIndex() <= 0 || cage.getPositionIndex() <= 0) {
                return null;
            }
            String rowCode = cage.getRowCode() == null ? "" : cage.getRowCode().trim();
            if (rowCode.isEmpty() || "LEGACY".equalsIgnoreCase(rowCode)) {
                return null;
            }
            String numeric = rowCode.matches("(?i)R[0-9]+") ? rowCode.substring(1) : rowCode;
            if (!numeric.matches("[0-9]+")) {
                return null;
            }
            try {
                int row = Integer.parseInt(numeric);
                return row > 0 ? new Coordinate(row, cage.getPositionIndex(), cage.getLayerIndex()) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String key() {
            return row + ":" + position + ":" + layer;
        }
    }
}

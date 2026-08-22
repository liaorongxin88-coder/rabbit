package com.rabbit.app.modules.house.spi;

public record HouseInitializationContext(
        Long userId,
        Long houseId,
        int rows,
        int columns,
        int layers,
        String actorId
) {
}

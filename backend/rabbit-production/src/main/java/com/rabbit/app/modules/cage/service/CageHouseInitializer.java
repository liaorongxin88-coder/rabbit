package com.rabbit.app.modules.cage.service;

import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.cage.support.CageNumbers;
import com.rabbit.app.modules.house.spi.HouseInitializationContext;
import com.rabbit.app.modules.house.spi.HouseInitializer;
import com.rabbit.app.modules.setting.service.SettingService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CageHouseInitializer implements HouseInitializer {
    private final SettingService settingService;
    private final CageMapper cageMapper;

    public CageHouseInitializer(SettingService settingService, CageMapper cageMapper) {
        this.settingService = settingService;
        this.cageMapper = cageMapper;
    }

    @Override
    public void initialize(HouseInitializationContext context) {
        settingService.initializeHouseSetting(context.userId(), context.houseId());

        List<Cage> cages = new ArrayList<Cage>();
        for (int row = 1; row <= context.rows(); row++) {
            for (int column = 1; column <= context.columns(); column++) {
                for (int layer = 1; layer <= context.layers(); layer++) {
                    Cage cage = new Cage();
                    cage.setHouseId(context.houseId());
                    cage.setCageNumber(CageNumbers.canonical(row, column, layer));
                    cage.setRowCode("R" + row);
                    cage.setPositionIndex(column);
                    cage.setLayerIndex(layer);
                    cage.setStatus("0");
                    cage.setRabbitCount(0);
                    cage.setIsFed(Boolean.FALSE);
                    cage.setIsEnabled(Boolean.TRUE);
                    cage.setCreateBy(context.actorId());
                    cage.setUpdateBy(context.actorId());
                    cages.add(cage);
                }
            }
        }
        cageMapper.insertBatch(cages);
    }
}

package com.rabbit.app.modules.cage.service;

import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CageQueryService {
    private final CageMapper cageMapper;

    public CageQueryService(CageMapper cageMapper) {
        this.cageMapper = cageMapper;
    }

    public List<Cage> listByHouse(Long houseId) {
        return cageMapper.selectByHouseId(houseId);
    }
}

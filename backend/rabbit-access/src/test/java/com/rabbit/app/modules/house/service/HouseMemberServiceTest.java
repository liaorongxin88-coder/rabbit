package com.rabbit.app.modules.house.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class HouseMemberServiceTest {
    @Test
    void locksTheFarmBeforeCountingAndRemovingAnOwner() {
        HouseUserMapper houseUserMapper = mock(HouseUserMapper.class);
        RabbitHouseMapper rabbitHouseMapper = mock(RabbitHouseMapper.class);
        RequestDedupService dedupService = mock(RequestDedupService.class);
        HouseMemberService service = new HouseMemberService(
                houseUserMapper,
                rabbitHouseMapper,
                mock(SysUserMapper.class),
                dedupService
        );
        RabbitHouse house = new RabbitHouse();
        house.setId(8L);
        house.setStatus("ENABLED");
        house.setIsDeleted(false);
        HouseUser owner = owner(8L, 7L);
        when(rabbitHouseMapper.selectByIdForUpdate(8L)).thenReturn(house);
        when(houseUserMapper.selectByUserAndHouse(7L, 8L)).thenReturn(owner);
        when(houseUserMapper.countEnabledOwners(8L)).thenReturn(2);
        when(houseUserMapper.deleteMember(8L, 7L)).thenReturn(1);

        service.leaveHouse(8L, 7L, "leave-request");

        InOrder order = inOrder(rabbitHouseMapper, houseUserMapper);
        order.verify(rabbitHouseMapper).selectByIdForUpdate(8L);
        order.verify(houseUserMapper).selectByUserAndHouse(7L, 8L);
        order.verify(houseUserMapper).countEnabledOwners(8L);
        order.verify(houseUserMapper).deleteMember(8L, 7L);
    }

    @Test
    void keepsTheLastEnabledOwnerAfterTheFarmRowIsLocked() {
        HouseUserMapper houseUserMapper = mock(HouseUserMapper.class);
        RabbitHouseMapper rabbitHouseMapper = mock(RabbitHouseMapper.class);
        HouseMemberService service = new HouseMemberService(
                houseUserMapper,
                rabbitHouseMapper,
                mock(SysUserMapper.class),
                mock(RequestDedupService.class)
        );
        RabbitHouse house = new RabbitHouse();
        house.setId(8L);
        house.setStatus("ENABLED");
        house.setIsDeleted(false);
        when(rabbitHouseMapper.selectByIdForUpdate(8L)).thenReturn(house);
        when(houseUserMapper.selectByUserAndHouse(7L, 8L)).thenReturn(owner(8L, 7L));
        when(houseUserMapper.countEnabledOwners(8L)).thenReturn(1);

        BizException error = assertThrows(
                BizException.class,
                () -> service.leaveHouse(8L, 7L, "last-owner-request")
        );

        assertEquals(409, error.getCode());
        assertEquals("兔场至少需要一名启用的所有者", error.getMessage());
        verify(rabbitHouseMapper).selectByIdForUpdate(8L);
        verify(houseUserMapper, never()).deleteMember(8L, 7L);
    }

    private static HouseUser owner(long houseId, long userId) {
        HouseUser owner = new HouseUser();
        owner.setHouseId(houseId);
        owner.setUserId(userId);
        owner.setRole("OWNER");
        owner.setStatus("ENABLED");
        owner.setPerms("control");
        owner.setIsAdmin(true);
        return owner;
    }
}

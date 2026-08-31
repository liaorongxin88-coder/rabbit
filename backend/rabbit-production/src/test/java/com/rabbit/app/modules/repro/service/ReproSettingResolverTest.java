package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.repro.domain.ReproSettings;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 生产周期配置的解析。
 *
 * <p>这层很薄，但它决定了每一条待办的到期日。天数解析错一次，全场母兔的提醒集体偏移，
 * 而偏移后的待办看上去完全正常——没人会怀疑是配置读错了，只会以为自己记错了日子。
 *
 * <p>另一半价值在「优先级只有一处实现」：状态机若自己判断房级还是用户级，这条规则
 * 就会被复制多份并各自漂移。所以这里要钉死它就是原样转交给配置服务的那一次查询。
 */
class ReproSettingResolverTest {
    private static final Long HOUSE_ID = 1L;
    private static final Long USER_ID = 7L;

    private SettingService settingService;
    private ReproSettingResolver resolver;

    @BeforeEach
    void setUp() {
        settingService = mock(SettingService.class);
        resolver = new ReproSettingResolver(settingService);
    }

    /**
     * 房级优先、缺失回落用户级这条规则归配置服务所有。这里只确认解析器原样转交，
     * 没有偷偷补一套自己的优先级判断。
     */
    @Test
    void theHouseAndUserScopeAreHandedStraightToTheSettingService() {
        when(settingService.getEffectiveSetting(USER_ID, HOUSE_ID)).thenReturn(setting());

        resolver.resolve(USER_ID, HOUSE_ID);

        verify(settingService).getEffectiveSetting(USER_ID, HOUSE_ID);
    }

    /** 旧列名到语义字段的映射一旦串位，催情天数会被当成哺乳天数用，全场提醒同时错。 */
    @Test
    void eachLegacyColumnLandsOnItsOwnSemanticField() {
        when(settingService.getEffectiveSetting(USER_ID, HOUSE_ID)).thenReturn(setting());

        ReproSettings settings = resolver.resolve(USER_ID, HOUSE_ID);

        assertEquals(3, settings.estrusDurationDays());
        assertEquals(11, settings.palpationWaitDays());
        assertEquals(14, settings.palpationToPrepartumDays());
        assertEquals(28, settings.weaningDays());
        assertEquals(9, settings.postpartumRecoveryDays());
        assertEquals(88, settings.replacementDays());
    }

    /**
     * 缺失或非正的天数回落到内置默认值。若原样透传 0，加 0 天的到期日等于当天，
     * 摸胎提醒会在配种当天就跳出来，用户只能靠一次次推迟把它压回去。
     */
    @Test
    void aMissingOrNonPositiveDurationFallsBackToTheBuiltInDefault() {
        GlobalSetting broken = new GlobalSetting();
        broken.setAphrodisiacDays(null);
        broken.setPalpationDays(0);
        broken.setPrepartumDays(-5);

        when(settingService.getEffectiveSetting(USER_ID, HOUSE_ID)).thenReturn(broken);

        ReproSettings settings = resolver.resolve(USER_ID, HOUSE_ID);

        assertEquals(2, settings.estrusDurationDays());
        assertEquals(12, settings.palpationWaitDays());
        assertEquals(3, settings.palpationToPrepartumDays());
        assertEquals(30, settings.weaningDays());
        assertEquals(10, settings.postpartumRecoveryDays());
    }

    /**
     * 完全没有配置时明确失败，而不是造一份默认配置继续跑：一个查不到配置的兔舍
     * 说明数据初始化就没做完，此时算出来的每一个到期日都是猜的。
     */
    @Test
    void anAbsentSettingIsRejectedRatherThanSilentlyDefaulted() {
        when(settingService.getEffectiveSetting(USER_ID, HOUSE_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(USER_ID, HOUSE_ID));
    }

    private GlobalSetting setting() {
        GlobalSetting setting = new GlobalSetting();
        setting.setAphrodisiacDays(3);
        setting.setPalpationDays(11);
        setting.setPrepartumDays(14);
        setting.setWeaningDays(28);
        setting.setPostpartumDays(9);
        setting.setSaleDays(70);
        setting.setReplacementDays(88);
        return setting;
    }
}

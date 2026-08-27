package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 配种资格校验。
 *
 * <p>被测类的注释里写明：旧路径只校验了「体配必须选公兔」，性别、类型、在栏与否全都没查，
 * 于是可以拿母兔当公兔配、给已离场的兔子配种。下面把那批漏掉的判断逐条立成用例，
 * 免得哪天重构又把它们丢了。
 *
 * <p>类型编码：0 种兔、1 后备兔、2 商品兔；性别编码：0 母、1 公。
 */
class BreedingEligibilityValidatorTest {
    private static final Long HOUSE_ID = 1L;
    private static final Long DOE_ID = 10L;
    private static final Long BUCK_ID = 20L;

    private RabbitMapper rabbitMapper;
    private ReproCycleMapper reproCycleMapper;
    private BreedingEligibilityValidator validator;

    @BeforeEach
    void setUp() {
        rabbitMapper = mock(RabbitMapper.class);
        reproCycleMapper = mock(ReproCycleMapper.class);
        validator = new BreedingEligibilityValidator(rabbitMapper, reproCycleMapper);
    }

    // ---------- 母兔 ----------

    @Test
    void aBreedingDoeWithABreedingBuckIsAccepted() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null));
    }

    /**
     * 后备兔（类型 1）到龄就能配，是正常繁殖入口，不能被类型校验误伤。
     */
    @Test
    void aReplacementDoeIsAlsoEligible() {
        stubDoe("0", "1", true);
        stubBuck("1", "0", true);

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null));
    }

    @Test
    void aMissingDoeIsRejected() {
        when(rabbitMapper.selectById(HOUSE_ID, DOE_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null));
        assertEquals(400, error.getCode());
        assertEquals("母兔不存在", error.getMessage());
    }

    /**
     * 查出来的兔子挂在别的兔场，等同于不存在。少了这一步就是跨租户越权。
     */
    @Test
    void aDoeBelongingToAnotherHouseIsRejected() {
        Rabbit foreign = rabbit("0", "0", true);
        foreign.setHouseId(999L);
        when(rabbitMapper.selectById(HOUSE_ID, DOE_ID)).thenReturn(foreign);

        assertEquals("母兔不存在", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    @Test
    void aDepartedDoeIsRejected() {
        stubDoe("0", "0", false);

        assertEquals("母兔不在场", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    /**
     * {@code isActive} 为 null 按不在场处理。历史行可能没有这个字段，默认放行会让
     * 早于该字段上线的兔子全部可配。
     */
    @Test
    void aDoeWithAnUnknownPresenceFlagIsRejected() {
        stubDoe("0", "0", null);

        assertEquals("母兔不在场", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    @Test
    void aMaleCannotBeUsedAsTheDoe() {
        stubDoe("1", "0", true);

        BizException error = assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null));
        assertEquals("母兔性别不正确", error.getMessage());
    }

    @Test
    void aCommodityRabbitCannotBeUsedAsTheDoe() {
        stubDoe("0", "2", true);

        assertEquals("母兔类型不正确", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    // ---------- 公兔 ----------

    /**
     * 人工授精可以不指定公兔（混精或外购冻精），这时不该去查一只不存在的兔子。
     */
    @Test
    void artificialInseminationWithoutABuckSkipsTheBuckChecks() {
        stubDoe("0", "0", true);

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, null, null));

        verify(rabbitMapper, never()).selectById(HOUSE_ID, BUCK_ID);
    }

    @Test
    void aMissingBuckIsRejected() {
        stubDoe("0", "0", true);
        when(rabbitMapper.selectById(HOUSE_ID, BUCK_ID)).thenReturn(null);

        assertEquals("公兔不存在", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    @Test
    void aDepartedBuckIsRejected() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", false);

        assertEquals("公兔不在场", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    @Test
    void aFemaleCannotBeUsedAsTheBuck() {
        stubDoe("0", "0", true);
        stubBuck("0", "0", true);

        assertEquals("公兔性别不正确", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    /**
     * 公兔比母兔严一档：只有种兔（0）能配，后备公兔（1）不行。
     */
    @Test
    void aReplacementBuckIsNotEligibleEvenThoughAReplacementDoeIs() {
        stubDoe("0", "0", true);
        stubBuck("1", "1", true);

        assertEquals("仅种公兔可用于配种", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null)).getMessage());
    }

    // ---------- 血配日期 ----------

    @Test
    void rematingOnTheBirthDayItselfIsAllowed() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);
        Date birth = daysFromNow(-10);
        stubOpenCycles(cycle(ReproStage.AWAIT_WEANING.name(), birth));

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, birth));
    }

    @Test
    void rematingAfterTheBirthIsAllowed() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);
        stubOpenCycles(cycle(ReproStage.AWAIT_WEANING.name(), daysFromNow(-10)));

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, daysFromNow(-3)));
    }

    /**
     * 复配日早于上一窝产仔日说明录入错了。放过去会让「产后复配天数」变成负数，
     * 污染繁殖节律统计，而那类脏数据事后很难从报表倒查回来。
     */
    @Test
    void rematingBeforeTheLastBirthIsRejected() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);
        stubOpenCycles(cycle(ReproStage.AWAIT_WEANING.name(), daysFromNow(-3)));

        BizException error = assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, daysFromNow(-10)));
        assertEquals(400, error.getCode());
        assertEquals("二次配种日期不能早于上一窝产仔日期", error.getMessage());
    }

    /**
     * 只有哺乳期（待断奶）的周期才有「上一窝」可言，其他阶段不参与这条判断。
     */
    @Test
    void cyclesOutsideTheNursingStageDoNotConstrainTheMatingDate() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);
        stubOpenCycles(cycle(ReproStage.AWAIT_DELIVERY.name(), daysFromNow(-3)));

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, daysFromNow(-10)));
    }

    @Test
    void aNursingCycleWithoutABirthDateDoesNotConstrainTheMatingDate() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);
        stubOpenCycles(cycle(ReproStage.AWAIT_WEANING.name(), null));

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, daysFromNow(-10)));
    }

    @Test
    void anAbsentMatingDateSkipsTheDateCheckEntirely() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, null));

        verify(reproCycleMapper, never()).selectOpenByMother(anyLong(), anyLong());
    }

    @Test
    void aNullCycleListIsToleratedRatherThanThrowing() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);
        when(reproCycleMapper.selectOpenByMother(HOUSE_ID, DOE_ID)).thenReturn(null);

        assertDoesNotThrow(() -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, daysFromNow(-1)));
    }

    /**
     * 一只母兔可能同时挂着多个开放周期（并行二配）。只要其中任意一个哺乳周期的产仔日
     * 晚于复配日，就该拦下来，不能只看第一个。
     */
    @Test
    void everyNursingCycleIsCheckedNotJustTheFirst() {
        stubDoe("0", "0", true);
        stubBuck("1", "0", true);
        stubOpenCycles(
                cycle(ReproStage.AWAIT_WEANING.name(), daysFromNow(-30)),
                cycle(ReproStage.AWAIT_WEANING.name(), daysFromNow(-3))
        );

        assertEquals("二次配种日期不能早于上一窝产仔日期", assertThrows(BizException.class,
                () -> validator.validateMating(HOUSE_ID, DOE_ID, BUCK_ID, daysFromNow(-10))).getMessage());
    }

    // ---------- 夹具 ----------

    private void stubDoe(String gender, String type, Boolean active) {
        when(rabbitMapper.selectById(HOUSE_ID, DOE_ID)).thenReturn(rabbit(gender, type, active));
    }

    private void stubBuck(String gender, String type, Boolean active) {
        when(rabbitMapper.selectById(HOUSE_ID, BUCK_ID)).thenReturn(rabbit(gender, type, active));
    }

    private void stubOpenCycles(ReproCycle... cycles) {
        when(reproCycleMapper.selectOpenByMother(HOUSE_ID, DOE_ID)).thenReturn(List.of(cycles));
    }

    private Rabbit rabbit(String gender, String type, Boolean active) {
        Rabbit rabbit = new Rabbit();
        rabbit.setHouseId(HOUSE_ID);
        rabbit.setGender(gender);
        rabbit.setType(type);
        rabbit.setIsActive(active);
        return rabbit;
    }

    private ReproCycle cycle(String stage, Date birthDate) {
        ReproCycle cycle = new ReproCycle();
        cycle.setStage(stage);
        cycle.setBirthDate(birthDate);
        return cycle;
    }

    private Date daysFromNow(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
}

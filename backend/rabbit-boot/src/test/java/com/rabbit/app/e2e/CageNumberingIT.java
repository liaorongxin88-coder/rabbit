package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * 笼位编号统一成「排-位-层」。
 *
 * <p>此前建兔舍生成 {@code 1-3-2}、App 批量建笼生成 {@code 2(下)1}，同一个兔舍里两套写法并存，
 * 工人拿着笼上的签对不上系统。这里把「谁来命名」钉死：坐标齐全就由后端按同一套规则生成。
 */
class CageNumberingIT extends E2eTestSupport {

    @Test
    @DisplayName("建兔舍自动生成的编号就是 排-位-层")
    void houseCreationNumbersAreRowPositionLayer() {
        UserSession owner = register("cagenum");
        long houseId = createHouse(owner, "编号兔舍", 1, 2, 2);

        List<String> numbers = cageNumbers(owner, houseId);

        // 1 排 2 位 2 层：位在前、层在后，同一位的两层挨着
        Assertions.assertEquals(List.of("1-1-1", "1-1-2", "1-2-1", "1-2-2"), numbers);
    }

    @Test
    @DisplayName("建单个笼位不传编号时，后端按坐标生成，客户端不用自己拼")
    void createDerivesNumberFromCoordinates() {
        UserSession owner = register("cagenum");
        long houseId = createHouse(owner, "补排兔舍", 1, 1, 1);

        JsonNode created = api.postOk("/api/cages", owner.token, houseId,
                obj("rowCode", "R2", "positionIndex", 3, "layerIndex", 1));

        Assertions.assertEquals("2-3-1", created.get("cageNumber").asText());
        Assertions.assertEquals("R2", created.get("rowCode").asText());
    }

    @Test
    @DisplayName("自带编号仍然认：角落里加的零散笼位没有规整坐标，得让人自己起名")
    void explicitNumberStillWins() {
        UserSession owner = register("cagenum");
        long houseId = createHouse(owner, "零散兔舍", 1, 1, 1);

        JsonNode created = api.postOk("/api/cages", owner.token, houseId,
                obj("cageNumber", "隔离笼-A", "rowCode", "R9", "positionIndex", 1, "layerIndex", 1));

        Assertions.assertEquals("隔离笼-A", created.get("cageNumber").asText());
    }

    @Test
    @DisplayName("既不给编号又不给全坐标，直接拒掉，不能瞎编一个名字")
    void refusesWhenNeitherNumberNorCoordinates() {
        UserSession owner = register("cagenum");
        long houseId = createHouse(owner, "缺坐标兔舍", 1, 1, 1);

        api.expectError("/api/cages", HttpMethod.POST, owner.token, houseId,
                obj("rowCode", "R2"), 400, "笼位编号不能为空");
    }

    @Test
    @DisplayName("生成出来的编号撞上已有的，报编号已存在，不静悄悄覆盖")
    void derivedNumberStillHitsUniqueness() {
        UserSession owner = register("cagenum");
        long houseId = createHouse(owner, "撞号兔舍", 1, 1, 1);

        // 建兔舍已经铺了 1-1-1，再按同一坐标补一个必然撞号
        api.expectError("/api/cages", HttpMethod.POST, owner.token, houseId,
                obj("rowCode", "R1", "positionIndex", 1, "layerIndex", 1), 400, "笼位编号已存在");
    }

    private List<String> cageNumbers(UserSession user, long houseId) {
        JsonNode cages = api.getOk("/api/cages", user.token, houseId);
        List<String> numbers = new ArrayList<String>();
        for (JsonNode cage : cages) {
            numbers.add(cage.get("cageNumber").asText());
        }
        numbers.sort(String::compareTo);
        return numbers;
    }
}

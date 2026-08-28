package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AbnormalConditionIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void manualAbnormalRecordUsesAnOwnedImageAndRequestIdIsIdempotent() {
        UserSession owner = register("abnormal_owner");
        long houseId = createHouse(owner, "异常记录兔舍", 1, 2, 1);
        long rabbitId = createRabbit(owner, houseId, cageIds(owner, houseId).get(0), "2", "0", "abnormal");
        String imageFileId = uploadTestImage(owner, houseId, "manual-abnormal");
        String requestId = requestId("manual-abnormal");

        api.postOk("/api/abnormal", owner.token, houseId, obj(
                "rabbitId", rabbitId,
                "warningStatus", "外伤",
                "imageFileId", imageFileId,
                "remark", "右耳有擦伤",
                "requestId", requestId
        ));
        api.postOk("/api/abnormal", owner.token, houseId, obj(
                "rabbitId", rabbitId,
                "warningStatus", "外伤",
                "imageFileId", imageFileId,
                "remark", "右耳有擦伤",
                "requestId", requestId
        ));

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from rabbit_abnormal_conditions where house_id = ? and rabbit_id = ? and is_deal = false",
                Integer.class,
                houseId,
                rabbitId
        ));
        assertEquals(imageFileId, jdbcTemplate.queryForObject(
                "select img_url from rabbit_abnormal_conditions where house_id = ? and rabbit_id = ?",
                String.class,
                houseId,
                rabbitId
        ));
    }
}

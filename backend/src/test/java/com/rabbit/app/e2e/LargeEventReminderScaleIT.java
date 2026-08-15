package com.rabbit.app.e2e;

import com.rabbit.app.modules.event.dto.EventReminderScanResult;
import com.rabbit.app.modules.event.service.EventReminderScanService;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

public class LargeEventReminderScaleIT extends E2eTestSupport {
    private static final int CAGE_COUNT = 700;
    private static final int RABBITS_PER_CAGE = 10;
    private static final int RABBIT_COUNT = CAGE_COUNT * RABBITS_PER_CAGE;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventReminderScanService scanService;

    @Test
    void scansSevenThousandDueSaleEventsBelowTheWriteGuard() {
        UserSession owner = register("event_scale");
        long houseId = createHouse(owner, "event_scale_house", 1, 1, 1);
        String marker = requestId("event_scale_fixture");

        insertCommodityCages(houseId, marker);
        List<Long> cageIds = jdbc.queryForList(
            "select id from cages where house_id = ? and create_by = ? order by id",
            Long.class,
            houseId,
            marker
        );
        Assertions.assertEquals(CAGE_COUNT, cageIds.size());

        insertCommodityRabbits(houseId, cageIds, marker);
        List<Long> rabbitIds = jdbc.queryForList(
            "select id from rabbits where house_id = ? and create_by = ? order by id",
            Long.class,
            houseId,
            marker
        );
        Assertions.assertEquals(RABBIT_COUNT, rabbitIds.size());

        String batchRequestId = requestId("event_scale_batch");
        jdbc.update(
            "insert into batches (house_id, batch_code, status, start_date, request_id, create_by, update_by) " +
            "values (?, ?, '进行中', now(), ?, ?, ?)",
            houseId,
            "EVENT-SCALE-" + houseId,
            batchRequestId,
            marker,
            marker
        );
        long batchId = jdbc.queryForObject(
            "select id from batches where house_id = ? and request_id = ?",
            Long.class,
            houseId,
            batchRequestId
        );
        insertDueBatchRabbits(batchId, rabbitIds, marker);

        Date scanTime = new Date();
        EventReminderScanResult first = scanService.scanHouse(
            houseId,
            scanTime
        );

        Assertions.assertEquals(RABBIT_COUNT, first.getProdLogged());
        Assertions.assertEquals(RABBIT_COUNT, first.getProdMarked());
        Assertions.assertEquals(
            RABBIT_COUNT,
            jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and is_event_notified = true",
                Integer.class,
                batchId
            )
        );
        Assertions.assertEquals(
            RABBIT_COUNT,
            jdbc.queryForObject(
                "select count(*) from event_reminder_logs where house_id = ? and category = '生产'",
                Integer.class,
                houseId
            )
        );
        Assertions.assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from cages c where c.house_id = ? and " +
                "c.rabbit_count <> (select count(*) from rabbits r where r.cage_id = c.id and r.is_active = true)",
                Integer.class,
                houseId
            )
        );

        EventReminderScanResult second = scanService.scanHouse(
            houseId,
            new Date(scanTime.getTime() + 1000)
        );
        Assertions.assertEquals(0, second.getProdLogged());
        Assertions.assertEquals(0, second.getProdMarked());

        api.postOk(
            "/api/batches/" + batchId + "/complete",
            owner.token,
            houseId,
            obj(
                "force",
                true,
                "endDate",
                oneMinuteAgo(),
                "remark",
                "large house forced completion",
                "requestId",
                requestId("event_scale_complete")
            )
        );
        Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
            Integer.class,
            batchId
        ));
        Assertions.assertEquals(RABBIT_COUNT, jdbc.queryForObject(
            "select count(*) from rabbits where house_id = ? and create_by = ? and state_version = 1",
            Integer.class,
            houseId,
            marker
        ));
        Assertions.assertEquals("已完成", jdbc.queryForObject(
            "select status from batches where house_id = ? and id = ?",
            String.class,
            houseId,
            batchId
        ));
    }

    private void insertCommodityCages(long houseId, String marker) {
        jdbc.batchUpdate(
            "insert into cages (house_id, cage_number, row_code, layer_index, position_index, status, rabbit_count, create_by, update_by) " +
            "values (?, ?, ?, 1, ?, '2', ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index)
                    throws SQLException {
                    statement.setLong(1, houseId);
                    statement.setString(2, "S-" + (index + 1));
                    statement.setString(3, "S-" + (index / 50 + 1));
                    statement.setInt(4, index + 1);
                    statement.setInt(5, RABBITS_PER_CAGE);
                    statement.setString(6, marker);
                    statement.setString(7, marker);
                }

                @Override
                public int getBatchSize() {
                    return CAGE_COUNT;
                }
            }
        );
    }

    private void insertCommodityRabbits(
        long houseId,
        List<Long> cageIds,
        String marker
    ) {
        jdbc.batchUpdate(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, arrival_date, is_active, is_quarantined, create_by, update_by) " +
            "values (?, ?, '2', ?, '1', now(), true, false, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index)
                    throws SQLException {
                    statement.setLong(1, houseId);
                    statement.setLong(
                        2,
                        cageIds.get(index / RABBITS_PER_CAGE)
                    );
                    statement.setString(3, index % 2 == 0 ? "0" : "1");
                    statement.setString(4, marker);
                    statement.setString(5, marker);
                }

                @Override
                public int getBatchSize() {
                    return RABBIT_COUNT;
                }
            }
        );
    }

    private void insertDueBatchRabbits(
        long batchId,
        List<Long> rabbitIds,
        String marker
    ) {
        jdbc.batchUpdate(
            "insert into batch_rabbits (batch_id, rabbit_id, join_reason, batch_role, current_status, " +
            "last_event_date, next_event_date, next_event_type, is_event_notified, is_active, join_date, create_by, update_by) " +
            "values (?, ?, '断奶', 'fattening', '待出售', date_sub(now(), interval 30 day), " +
            "date_sub(now(), interval 1 day), '出售', false, true, date_sub(now(), interval 30 day), ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index)
                    throws SQLException {
                    statement.setLong(1, batchId);
                    statement.setLong(2, rabbitIds.get(index));
                    statement.setString(3, marker);
                    statement.setString(4, marker);
                }

                @Override
                public int getBatchSize() {
                    return rabbitIds.size();
                }
            }
        );
    }
}

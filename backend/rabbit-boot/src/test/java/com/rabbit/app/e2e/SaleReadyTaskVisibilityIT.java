package com.rabbit.app.e2e;

import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class SaleReadyTaskVisibilityIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkTaskMapper workTaskMapper;

    @Test
    void keepsSaleReadyVisibilityInSyncAcrossDueListCountAndBulkFilter() {
        UserSession owner = register("sale_ready_visibility");
        long houseId = createHouse(owner, "出售提醒查询兔舍", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long breeder = rabbit(houseId, cages.get(0), "0", null, "breeder");
        long immatureCommodity = rabbit(houseId, cages.get(1), "2", "FATTENING", "immature");
        long matureCommodity = rabbit(houseId, cages.get(2), "2", "MATURE", "mature");

        long breederSaleTask = task(houseId, breeder, "SALE_READY", "breeder-sale");
        long immatureSaleTask = task(houseId, immatureCommodity, "SALE_READY", "immature-sale");
        long matureSaleTask = task(houseId, matureCommodity, "SALE_READY", "mature-sale");
        long estrusTask = task(houseId, breeder, "ESTRUS", "breeder-estrus");

        Date dueBefore = new Date();
        List<WorkTask> dueTasks = workTaskMapper.selectPendingDue(
            houseId, dueBefore, null, null, null, null, 0, 20
        );
        long dueCount = workTaskMapper.countPendingDue(
            houseId, dueBefore, null, null, null, null
        );
        Set<Long> visibleIds = dueTasks.stream().map(WorkTask::getId).collect(Collectors.toSet());

        Assertions.assertEquals(2L, dueCount);
        Assertions.assertEquals(Set.of(matureSaleTask, estrusTask), visibleIds);
        Assertions.assertFalse(visibleIds.contains(breederSaleTask));
        Assertions.assertFalse(visibleIds.contains(immatureSaleTask));

        List<Long> filteredSaleReadyIds = workTaskMapper.selectPendingByFilter(
            houseId, "SALE_READY", null, null, 20
        ).stream().map(WorkTask::getId).toList();
        Assertions.assertEquals(List.of(matureSaleTask), filteredSaleReadyIds);
    }

    private long rabbit(Long houseId, Long cageId, String type, String growthStage, String suffix) {
        String requestId = requestId("sale_ready_" + suffix);
        jdbc.update(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, growth_stage,"
                + " state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, ?, '0', '1', ?, 0, true, false, ?, 'test', 'test')",
            houseId,
            cageId,
            type,
            growthStage,
            requestId
        );
        return jdbc.queryForObject(
            "select id from rabbits where house_id = ? and request_id = ?",
            Long.class,
            houseId,
            requestId
        );
    }

    private long task(Long houseId, Long rabbitId, String taskType, String suffix) {
        String dedupKey = "sale-ready-visibility:" + suffix;
        jdbc.update(
            "insert into work_tasks (house_id, task_type, subject_type, subject_id, rabbit_id,"
                + " due_date, due_time, status, dedup_key, create_by, update_by)"
                + " values (?, ?, 'RABBIT', ?, ?, date_sub(curdate(), interval 1 day),"
                + " date_sub(now(), interval 1 day), 'PENDING', ?, 'test', 'test')",
            houseId,
            taskType,
            rabbitId,
            rabbitId,
            dedupKey
        );
        return jdbc.queryForObject(
            "select id from work_tasks where house_id = ? and dedup_key = ?",
            Long.class,
            houseId,
            dedupKey
        );
    }
}

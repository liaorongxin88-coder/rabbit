package com.rabbit.app.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class DbSchemaMigrator {
    private final JdbcTemplate jdbcTemplate;

    public DbSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        ensureColumn("sys_user", "openid", "alter table sys_user add column openid varchar(128) null after password");
        ensureColumn("rabbit_houses", "is_deleted", "alter table rabbit_houses add column is_deleted boolean not null default false");
        ensureColumn("cages", "is_enabled", "alter table cages add column is_enabled boolean not null default true");
        ensureColumn("pregnancy_check_records", "house_id", "alter table pregnancy_check_records add column house_id bigint null after id");
        ensureColumn("parturition_records", "house_id", "alter table parturition_records add column house_id bigint null after id");
        ensureColumn("prepartum_records", "house_id", "alter table prepartum_records add column house_id bigint null after id");
        ensureColumn("weaning_records", "house_id", "alter table weaning_records add column house_id bigint null after id");
        ensureColumn("rabbit_status_history", "house_id", "alter table rabbit_status_history add column house_id bigint null after id");
        ensureColumn("weaning_records", "target_cage_id", "alter table weaning_records add column target_cage_id bigint");
        ensureColumn("weaning_records", "in_cage_id", "alter table weaning_records add column in_cage_id bigint");
        ensureColumn("batch_rabbits", "is_event_notified", "alter table batch_rabbits add column is_event_notified boolean not null default false");
        ensureColumn("batch_rabbits", "event_notify_date", "alter table batch_rabbits add column event_notify_date datetime");
        ensureColumn("global_setting", "user_id", "alter table global_setting add column user_id bigint null after house_id");
        backfillCoreIdentityColumns();
        jdbcTemplate.execute("create table if not exists feed_log_rabbits (" +
                "id bigint primary key auto_increment," +
                "house_id bigint not null," +
                "feed_log_id bigint not null," +
                "rabbit_id bigint not null," +
                "cage_id bigint," +
                "create_time datetime not null default current_timestamp," +
                "unique key uk_flr (feed_log_id, rabbit_id)," +
                "key idx_flr_house_cage (house_id, cage_id, feed_log_id)," +
                "key idx_flr_rabbit (rabbit_id)" +
                ") engine=InnoDB default charset=utf8mb4");
        jdbcTemplate.execute("create table if not exists audit_logs (" +
                "id bigint primary key auto_increment," +
                "trace_id varchar(64)," +
                "user_id bigint," +
                "house_id bigint," +
                "method varchar(10)," +
                "path varchar(255)," +
                "query_string varchar(1000)," +
                "status int," +
                "api_code int," +
                "api_message varchar(255)," +
                "cost_ms bigint," +
                "error_message varchar(500)," +
                "ip varchar(64)," +
                "user_agent varchar(255)," +
                "create_time datetime not null default current_timestamp," +
                "key idx_audit_house_time (house_id, create_time)," +
                "key idx_audit_user_time (user_id, create_time)," +
                "key idx_audit_trace (trace_id)" +
                ") engine=InnoDB default charset=utf8mb4");
        jdbcTemplate.execute("create table if not exists event_reminder_logs (" +
                "id bigint primary key auto_increment," +
                "house_id bigint not null," +
                "category varchar(32) not null," +
                "record_id bigint not null," +
                "event_date datetime," +
                "notify_date date not null," +
                "notify_time datetime not null," +
                "create_time datetime not null default current_timestamp," +
                "unique key uk_erl_house_cat_record_date (house_id, category, record_id, notify_date)," +
                "key idx_erl_house_date_id (house_id, notify_date, id)" +
                ") engine=InnoDB default charset=utf8mb4");
        jdbcTemplate.execute("create table if not exists weaning_record_allocations (" +
                "id bigint primary key auto_increment," +
                "weaning_record_id bigint not null," +
                "cage_id bigint not null," +
                "alloc_count int not null," +
                "create_time datetime not null default current_timestamp," +
                "unique key uk_wra_record_cage (weaning_record_id, cage_id)," +
                "key idx_wra_record (weaning_record_id)," +
                "key idx_wra_cage (cage_id)" +
                ") engine=InnoDB default charset=utf8mb4");
        ensureColumn("audit_logs", "api_code", "alter table audit_logs add column api_code int");
        ensureColumn("audit_logs", "api_message", "alter table audit_logs add column api_message varchar(255)");
        ensureIndex("sys_user", "uk_sys_user_openid", "alter table sys_user add unique key uk_sys_user_openid (openid)");
        ensureIndex("pregnancy_check_records", "idx_pcr_house_batch", "alter table pregnancy_check_records add index idx_pcr_house_batch (house_id, batch_id, id)");
        ensureIndex("pregnancy_check_records", "idx_pcr_house_rabbit", "alter table pregnancy_check_records add index idx_pcr_house_rabbit (house_id, rabbit_id, id)");
        ensureIndex("parturition_records", "idx_pr_house_batch", "alter table parturition_records add index idx_pr_house_batch (house_id, batch_id, id)");
        ensureIndex("parturition_records", "idx_pr_house_rabbit", "alter table parturition_records add index idx_pr_house_rabbit (house_id, rabbit_id, id)");
        ensureIndex("prepartum_records", "idx_ppr_house_batch", "alter table prepartum_records add index idx_ppr_house_batch (house_id, batch_id, id)");
        ensureIndex("prepartum_records", "idx_ppr_house_rabbit", "alter table prepartum_records add index idx_ppr_house_rabbit (house_id, rabbit_id, id)");
        ensureIndex("weaning_records", "idx_wr_house_batch", "alter table weaning_records add index idx_wr_house_batch (house_id, batch_id, id)");
        ensureIndex("weaning_records", "idx_wr_house_rabbit", "alter table weaning_records add index idx_wr_house_rabbit (house_id, rabbit_id, id)");
        ensureIndex("rabbit_status_history", "idx_rsh_house_rabbit_time", "alter table rabbit_status_history add index idx_rsh_house_rabbit_time (house_id, rabbit_id, change_time, id)");
        ensureIndex("rabbit_status_history", "idx_rsh_house_batch_time", "alter table rabbit_status_history add index idx_rsh_house_batch_time (house_id, batch_id, change_time, id)");
        ensureIndex("global_setting", "uk_setting_user", "alter table global_setting add unique key uk_setting_user (user_id)");
    }

    private void backfillCoreIdentityColumns() {
        executeQuietly("update sys_user set openid = substring(user_name, 4) where (openid is null or openid = '') and user_name like 'wx!_%' escape '!'");
        executeQuietly("update pregnancy_check_records p join batches b on b.id = p.batch_id set p.house_id = b.house_id where p.house_id is null");
        executeQuietly("update parturition_records p join batches b on b.id = p.batch_id set p.house_id = b.house_id where p.house_id is null");
        executeQuietly("update prepartum_records p join batches b on b.id = p.batch_id set p.house_id = b.house_id where p.house_id is null");
        executeQuietly("update weaning_records w join batches b on b.id = w.batch_id set w.house_id = b.house_id where w.house_id is null");
        executeQuietly("update rabbit_status_history h join batches b on b.id = h.batch_id set h.house_id = b.house_id where h.house_id is null and h.batch_id is not null");
        executeQuietly("update rabbit_status_history h join rabbits r on r.id = h.rabbit_id set h.house_id = r.house_id where h.house_id is null");
        executeQuietly("alter table global_setting modify column house_id bigint null");
        executeQuietly("insert into global_setting (house_id, user_id, aphrodisiac_days, palpation_days, prepartum_days, weaning_days, postpartum_days, sale_days, replacement_days, remark, create_by, update_by) " +
                "select null, picked.user_id, picked.aphrodisiac_days, picked.palpation_days, picked.prepartum_days, picked.weaning_days, picked.postpartum_days, picked.sale_days, picked.replacement_days, picked.remark, 'migration', 'migration' " +
                "from (select hu.user_id, gs.aphrodisiac_days, gs.palpation_days, gs.prepartum_days, gs.weaning_days, gs.postpartum_days, gs.sale_days, gs.replacement_days, gs.remark " +
                "from house_users hu join rabbit_houses h on h.id = hu.house_id and h.is_deleted = false join global_setting gs on gs.house_id = h.id " +
                "join (select hu2.user_id, min(h2.id) as house_id from house_users hu2 join rabbit_houses h2 on h2.id = hu2.house_id and h2.is_deleted = false join global_setting gs2 on gs2.house_id = h2.id group by hu2.user_id) first_house " +
                "on first_house.user_id = hu.user_id and first_house.house_id = h.id) picked " +
                "where not exists (select 1 from global_setting existing where existing.user_id = picked.user_id)");
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?",
                    Integer.class, table, column);
            if (cnt == null || cnt <= 0) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureIndex(String table, String index, String alterSql) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.statistics where table_schema = database() and table_name = ? and index_name = ?",
                    Integer.class, table, index);
            if (cnt == null || cnt <= 0) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ignored) {
        }
    }

    private void executeQuietly(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}

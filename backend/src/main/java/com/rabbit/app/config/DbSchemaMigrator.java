package com.rabbit.app.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class DbSchemaMigrator {
    private final JdbcTemplate jdbcTemplate;

    public DbSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        ensureColumn("rabbit_houses", "is_deleted", "alter table rabbit_houses add column is_deleted boolean not null default false");
        ensureColumn("cages", "is_enabled", "alter table cages add column is_enabled boolean not null default true");
        ensureColumn("weaning_records", "target_cage_id", "alter table weaning_records add column target_cage_id bigint");
        ensureColumn("weaning_records", "in_cage_id", "alter table weaning_records add column in_cage_id bigint");
        ensureColumn("batch_rabbits", "is_event_notified", "alter table batch_rabbits add column is_event_notified boolean not null default false");
        ensureColumn("batch_rabbits", "event_notify_date", "alter table batch_rabbits add column event_notify_date datetime");
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
}

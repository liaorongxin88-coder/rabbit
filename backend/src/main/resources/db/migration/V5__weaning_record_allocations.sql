create table if not exists weaning_record_allocations (
  id bigint primary key auto_increment,
  weaning_record_id bigint not null,
  cage_id bigint not null,
  alloc_count int not null,
  create_time datetime not null default current_timestamp,
  unique key uk_wra_record_cage (weaning_record_id, cage_id),
  key idx_wra_record (weaning_record_id),
  key idx_wra_cage (cage_id)
) engine=InnoDB default charset=utf8mb4;


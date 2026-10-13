DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

-- ----------------------------
-- 1、存储每一个已配置的 jobDetail 的详细信息
-- ----------------------------
create table QRTZ_JOB_DETAILS (
    sched_name           varchar(120)    not null,
    job_name             varchar(200)    not null,
    job_group            varchar(200)    not null,
    description          varchar(250)    null,
    job_class_name       varchar(250)    not null,
    is_durable           varchar(1)      not null,
    is_nonconcurrent     varchar(1)      not null,
    is_update_data       varchar(1)      not null,
    requests_recovery    varchar(1)      not null,
    job_data             bytea            null,
    primary key (sched_name, job_name, job_group)
);

comment on table QRTZ_JOB_DETAILS is '任务详细信息表';
comment on column QRTZ_JOB_DETAILS.sched_name is '调度名称';
comment on column QRTZ_JOB_DETAILS.job_name is '任务名称';
comment on column QRTZ_JOB_DETAILS.job_group is '任务组名';
comment on column QRTZ_JOB_DETAILS.description is '相关介绍';
comment on column QRTZ_JOB_DETAILS.job_class_name is '执行任务类名称';
comment on column QRTZ_JOB_DETAILS.is_durable is '是否持久化';
comment on column QRTZ_JOB_DETAILS.is_nonconcurrent is '是否并发';
comment on column QRTZ_JOB_DETAILS.is_update_data is '是否更新数据';
comment on column QRTZ_JOB_DETAILS.requests_recovery is '是否接受恢复执行';
comment on column QRTZ_JOB_DETAILS.job_data is '存放持久化job对象';


-- ----------------------------
-- 2、 存储已配置的 Trigger 的信息
-- ----------------------------
create table QRTZ_TRIGGERS (
    sched_name           varchar(120)    not null,
    trigger_name         varchar(200)    not null,
    trigger_group        varchar(200)    not null,
    job_name             varchar(200)    not null,
    job_group            varchar(200)    not null,
    description          varchar(250)    null,
    next_fire_time       bigint      null,
    prev_fire_time       bigint      null,
    priority             integer         null,
    trigger_state        varchar(16)     not null,
    trigger_type         varchar(8)      not null,
    start_time           bigint      not null,
    end_time             bigint      null,
    calendar_name        varchar(200)    null,
    misfire_instr        smallint     null,
    job_data             bytea            null,
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, job_name, job_group) references QRTZ_JOB_DETAILS(sched_name, job_name, job_group)
);

comment on table QRTZ_TRIGGERS is '触发器详细信息表';
comment on column QRTZ_TRIGGERS.sched_name is '调度名称';
comment on column QRTZ_TRIGGERS.trigger_name is '触发器的名字';
comment on column QRTZ_TRIGGERS.trigger_group is '触发器所属组的名字';
comment on column QRTZ_TRIGGERS.job_name is 'qrtz_job_details表job_name的外键';
comment on column QRTZ_TRIGGERS.job_group is 'qrtz_job_details表job_group的外键';
comment on column QRTZ_TRIGGERS.description is '相关介绍';
comment on column QRTZ_TRIGGERS.next_fire_time is '上一次触发时间（毫秒）';
comment on column QRTZ_TRIGGERS.prev_fire_time is '下一次触发时间（默认为-1表示不触发）';
comment on column QRTZ_TRIGGERS.priority is '优先级';
comment on column QRTZ_TRIGGERS.trigger_state is '触发器状态';
comment on column QRTZ_TRIGGERS.trigger_type is '触发器的类型';
comment on column QRTZ_TRIGGERS.start_time is '开始时间';
comment on column QRTZ_TRIGGERS.end_time is '结束时间';
comment on column QRTZ_TRIGGERS.calendar_name is '日程表名称';
comment on column QRTZ_TRIGGERS.misfire_instr is '补偿执行的策略';
comment on column QRTZ_TRIGGERS.job_data is '存放持久化job对象';


-- ----------------------------
-- 3、 存储简单的 Trigger，包括重复次数，间隔，以及已触发的次数
-- ----------------------------
create table QRTZ_SIMPLE_TRIGGERS (
    sched_name           varchar(120)    not null,
    trigger_name         varchar(200)    not null,
    trigger_group        varchar(200)    not null,
    repeat_count         bigint       not null,
    repeat_interval      bigint      not null,
    times_triggered      bigint      not null,
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
);

comment on table QRTZ_SIMPLE_TRIGGERS is '简单触发器的信息表';
comment on column QRTZ_SIMPLE_TRIGGERS.sched_name is '调度名称';
comment on column QRTZ_SIMPLE_TRIGGERS.trigger_name is 'qrtz_triggers表trigger_name的外键';
comment on column QRTZ_SIMPLE_TRIGGERS.trigger_group is 'qrtz_triggers表trigger_group的外键';
comment on column QRTZ_SIMPLE_TRIGGERS.repeat_count is '重复的次数统计';
comment on column QRTZ_SIMPLE_TRIGGERS.repeat_interval is '重复的间隔时间';
comment on column QRTZ_SIMPLE_TRIGGERS.times_triggered is '已经触发的次数';


-- ----------------------------
-- 4、 存储 Cron Trigger，包括 Cron 表达式和时区信息
-- ---------------------------- 
create table QRTZ_CRON_TRIGGERS (
    sched_name           varchar(120)    not null,
    trigger_name         varchar(200)    not null,
    trigger_group        varchar(200)    not null,
    cron_expression      varchar(200)    not null,
    time_zone_id         varchar(80),
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
);

comment on table QRTZ_CRON_TRIGGERS is 'Cron类型的触发器表';
comment on column QRTZ_CRON_TRIGGERS.sched_name is '调度名称';
comment on column QRTZ_CRON_TRIGGERS.trigger_name is 'qrtz_triggers表trigger_name的外键';
comment on column QRTZ_CRON_TRIGGERS.trigger_group is 'qrtz_triggers表trigger_group的外键';
comment on column QRTZ_CRON_TRIGGERS.cron_expression is 'cron表达式';
comment on column QRTZ_CRON_TRIGGERS.time_zone_id is '时区';


-- ----------------------------
-- 5、 Trigger 作为 Blob 类型存储(用于 Quartz 用户用 JDBC 创建他们自己定制的 Trigger 类型，JobStore 并不知道如何存储实例的时候)
-- ---------------------------- 
create table QRTZ_BLOB_TRIGGERS (
    sched_name           varchar(120)    not null,
    trigger_name         varchar(200)    not null,
    trigger_group        varchar(200)    not null,
    blob_data            bytea            null,
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
);

comment on table QRTZ_BLOB_TRIGGERS is 'Blob类型的触发器表';
comment on column QRTZ_BLOB_TRIGGERS.sched_name is '调度名称';
comment on column QRTZ_BLOB_TRIGGERS.trigger_name is 'qrtz_triggers表trigger_name的外键';
comment on column QRTZ_BLOB_TRIGGERS.trigger_group is 'qrtz_triggers表trigger_group的外键';
comment on column QRTZ_BLOB_TRIGGERS.blob_data is '存放持久化Trigger对象';


-- ----------------------------
-- 6、 以 Blob 类型存储存放日历信息， quartz可配置一个日历来指定一个时间范围
-- ---------------------------- 
create table QRTZ_CALENDARS (
    sched_name           varchar(120)    not null,
    calendar_name        varchar(200)    not null,
    calendar             bytea            not null,
    primary key (sched_name, calendar_name)
);

comment on table QRTZ_CALENDARS is '日历信息表';
comment on column QRTZ_CALENDARS.sched_name is '调度名称';
comment on column QRTZ_CALENDARS.calendar_name is '日历名称';
comment on column QRTZ_CALENDARS.calendar is '存放持久化calendar对象';


-- ----------------------------
-- 7、 存储已暂停的 Trigger 组的信息
-- ---------------------------- 
create table QRTZ_PAUSED_TRIGGER_GRPS (
    sched_name           varchar(120)    not null,
    trigger_group        varchar(200)    not null,
    primary key (sched_name, trigger_group)
);

comment on table QRTZ_PAUSED_TRIGGER_GRPS is '暂停的触发器表';
comment on column QRTZ_PAUSED_TRIGGER_GRPS.sched_name is '调度名称';
comment on column QRTZ_PAUSED_TRIGGER_GRPS.trigger_group is 'qrtz_triggers表trigger_group的外键';


-- ----------------------------
-- 8、 存储与已触发的 Trigger 相关的状态信息，以及相联 Job 的执行信息
-- ---------------------------- 
create table QRTZ_FIRED_TRIGGERS (
    sched_name           varchar(120)    not null,
    entry_id             varchar(95)     not null,
    trigger_name         varchar(200)    not null,
    trigger_group        varchar(200)    not null,
    instance_name        varchar(200)    not null,
    fired_time           bigint      not null,
    sched_time           bigint      not null,
    priority             integer         not null,
    state                varchar(16)     not null,
    job_name             varchar(200)    null,
    job_group            varchar(200)    null,
    is_nonconcurrent     varchar(1)      null,
    requests_recovery    varchar(1)      null,
    primary key (sched_name, entry_id)
);

comment on table QRTZ_FIRED_TRIGGERS is '已触发的触发器表';
comment on column QRTZ_FIRED_TRIGGERS.sched_name is '调度名称';
comment on column QRTZ_FIRED_TRIGGERS.entry_id is '调度器实例id';
comment on column QRTZ_FIRED_TRIGGERS.trigger_name is 'qrtz_triggers表trigger_name的外键';
comment on column QRTZ_FIRED_TRIGGERS.trigger_group is 'qrtz_triggers表trigger_group的外键';
comment on column QRTZ_FIRED_TRIGGERS.instance_name is '调度器实例名';
comment on column QRTZ_FIRED_TRIGGERS.fired_time is '触发的时间';
comment on column QRTZ_FIRED_TRIGGERS.sched_time is '定时器制定的时间';
comment on column QRTZ_FIRED_TRIGGERS.priority is '优先级';
comment on column QRTZ_FIRED_TRIGGERS.state is '状态';
comment on column QRTZ_FIRED_TRIGGERS.job_name is '任务名称';
comment on column QRTZ_FIRED_TRIGGERS.job_group is '任务组名';
comment on column QRTZ_FIRED_TRIGGERS.is_nonconcurrent is '是否并发';
comment on column QRTZ_FIRED_TRIGGERS.requests_recovery is '是否接受恢复执行';


-- ----------------------------
-- 9、 存储少量的有关 Scheduler 的状态信息，假如是用于集群中，可以看到其他的 Scheduler 实例
-- ---------------------------- 
create table QRTZ_SCHEDULER_STATE (
    sched_name           varchar(120)    not null,
    instance_name        varchar(200)    not null,
    last_checkin_time    bigint      not null,
    checkin_interval     bigint      not null,
    primary key (sched_name, instance_name)
);

comment on table QRTZ_SCHEDULER_STATE is '调度器状态表';
comment on column QRTZ_SCHEDULER_STATE.sched_name is '调度名称';
comment on column QRTZ_SCHEDULER_STATE.instance_name is '实例名称';
comment on column QRTZ_SCHEDULER_STATE.last_checkin_time is '上次检查时间';
comment on column QRTZ_SCHEDULER_STATE.checkin_interval is '检查间隔时间';


-- ----------------------------
-- 10、 存储程序的悲观锁的信息(假如使用了悲观锁)
-- ---------------------------- 
create table QRTZ_LOCKS (
    sched_name           varchar(120)    not null,
    lock_name            varchar(40)     not null,
    primary key (sched_name, lock_name)
);

comment on table QRTZ_LOCKS is '存储的悲观锁信息表';
comment on column QRTZ_LOCKS.sched_name is '调度名称';
comment on column QRTZ_LOCKS.lock_name is '悲观锁名称';


-- ----------------------------
-- 11、 Quartz集群实现同步机制的行锁表
-- ---------------------------- 
create table QRTZ_SIMPROP_TRIGGERS (
    sched_name           varchar(120)    not null,
    trigger_name         varchar(200)    not null,
    trigger_group        varchar(200)    not null,
    str_prop_1           varchar(512)    null,
    str_prop_2           varchar(512)    null,
    str_prop_3           varchar(512)    null,
    int_prop_1           int             null,
    int_prop_2           int             null,
    long_prop_1          bigint          null,
    long_prop_2          bigint          null,
    dec_prop_1           numeric(13,4)   null,
    dec_prop_2           numeric(13,4)   null,
    bool_prop_1          varchar(1)      null,
    bool_prop_2          varchar(1)      null,
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
);

comment on table QRTZ_SIMPROP_TRIGGERS is '同步机制的行锁表';
comment on column QRTZ_SIMPROP_TRIGGERS.sched_name is '调度名称';
comment on column QRTZ_SIMPROP_TRIGGERS.trigger_name is 'qrtz_triggers表trigger_name的外键';
comment on column QRTZ_SIMPROP_TRIGGERS.trigger_group is 'qrtz_triggers表trigger_group的外键';
comment on column QRTZ_SIMPROP_TRIGGERS.str_prop_1 is 'String类型的trigger的第一个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.str_prop_2 is 'String类型的trigger的第二个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.str_prop_3 is 'String类型的trigger的第三个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.int_prop_1 is 'int类型的trigger的第一个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.int_prop_2 is 'int类型的trigger的第二个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.long_prop_1 is 'long类型的trigger的第一个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.long_prop_2 is 'long类型的trigger的第二个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.dec_prop_1 is 'decimal类型的trigger的第一个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.dec_prop_2 is 'decimal类型的trigger的第二个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.bool_prop_1 is 'Boolean类型的trigger的第一个参数';
comment on column QRTZ_SIMPROP_TRIGGERS.bool_prop_2 is 'Boolean类型的trigger的第二个参数';


commit;
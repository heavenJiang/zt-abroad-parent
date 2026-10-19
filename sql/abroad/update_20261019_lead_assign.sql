-- ----------------------------------------------------------------------------
-- M5-03 线索自动分配（轮询 / 按国家 / 按负载）与 SLA 提醒、升级
--
-- - abr_consultant：参与分配的顾问（擅长国家 / 地区与学段、同时负责的线索上限、是否接单）。
-- - abr_lead_assign_setting：分配设置（单行）：按国家匹配、轮询或按负载、自动分配的意向度、各意向度的首次联系时限、超时多久升级、升级给谁。
-- - abr_lead：分配时间、SLA 到期时间、SLA 状态、首次联系时间。
-- - abr_staff_notice：员工站内提醒（新线索分配、SLA 超时、升级）；企业微信应用消息随 M0-01 接入（AbrStaffNotifier）。
-- - 定时任务 abrLeadTask.assign() 每分钟：分配未分配的线索 → 检查 SLA（超时提醒负责人，再超时升级给主管）。
-- 依赖：update_20261019_lead.sql（线索）、update_20261018_orchestrator.sql（获客运营菜单）
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、参与分配的顾问
-- ----------------------------
create table if not exists abr_consultant (
  consultant_id      bigserial      not null,
  user_id            bigint         not null,
  countries          jsonb          not null default '[]'::jsonb,
  degrees            jsonb          not null default '[]'::jsonb,
  max_active         integer        default 50,
  accepting          char(1)        default 'Y',
  last_assigned_time timestamp      default null,
  create_by          varchar(64)    default '',
  create_time        timestamp      default now(),
  update_by          varchar(64)    default '',
  update_time        timestamp      default now(),
  remark             varchar(500)   default null,
  primary key (consultant_id)
);
create unique index if not exists uk_abr_consultant_user on abr_consultant (user_id);

comment on table  abr_consultant                    is '参与线索分配的顾问';
comment on column abr_consultant.consultant_id      is 'ID';
comment on column abr_consultant.user_id            is '员工（sys_user）';
comment on column abr_consultant.countries          is '擅长国家 / 地区（abr_country 值数组；空为全部）';
comment on column abr_consultant.degrees            is '擅长学段（abr_degree 值数组；空为全部）';
comment on column abr_consultant.max_active         is '同时负责的进行中线索上限（未签约、未流失）';
comment on column abr_consultant.accepting          is '是否接单（N 时不参与自动分配，如休假）';
comment on column abr_consultant.last_assigned_time is '最近一次被自动分配的时间（轮询用）';
comment on column abr_consultant.create_by          is '创建者';
comment on column abr_consultant.create_time        is '创建时间';
comment on column abr_consultant.update_by          is '更新者';
comment on column abr_consultant.update_time        is '更新时间';
comment on column abr_consultant.remark             is '备注';

-- ----------------------------
-- 2、分配设置（单行）
-- ----------------------------
create table if not exists abr_lead_assign_setting (
  setting_id       integer        not null,
  auto_assign      char(1)        default 'Y',
  match_country    char(1)        default 'Y',
  pick_strategy    varchar(16)    default 'round_robin',
  assign_grades    jsonb          not null default '["A", "B"]'::jsonb,
  sla_hours        jsonb          not null default '{"A": 2, "B": 24}'::jsonb,
  escalate_hours   integer        default 2,
  escalate_user_id bigint         default null,
  update_by        varchar(64)    default '',
  update_time      timestamp      default now(),
  primary key (setting_id)
);

comment on table  abr_lead_assign_setting                  is '线索分配设置（单行，setting_id = 1）';
comment on column abr_lead_assign_setting.setting_id       is '固定为 1';
comment on column abr_lead_assign_setting.auto_assign      is '是否自动分配（N 时只能手动转给同事）';
comment on column abr_lead_assign_setting.match_country    is '是否按国家 / 地区与学段匹配顾问（匹配不到时在全部接单顾问中分配）';
comment on column abr_lead_assign_setting.pick_strategy    is '挑选方式（字典 abr_assign_strategy：round_robin 轮询 / load 按负载）';
comment on column abr_lead_assign_setting.assign_grades    is '自动分配的意向度（数组；其余等级留给培育序列）';
comment on column abr_lead_assign_setting.sla_hours        is '分配后首次联系时限（小时；意向度 → 小时，没有的不设时限）';
comment on column abr_lead_assign_setting.escalate_hours   is '超时后再过几小时仍未联系就升级';
comment on column abr_lead_assign_setting.escalate_user_id is '升级给谁（主管，sys_user；为空时提醒负责人）';
comment on column abr_lead_assign_setting.update_by        is '更新者';
comment on column abr_lead_assign_setting.update_time      is '更新时间';

insert into abr_lead_assign_setting (setting_id, update_by, update_time)
select 1, 'admin', now()
where not exists (select 1 from abr_lead_assign_setting where setting_id = 1);

-- ----------------------------
-- 3、线索：分配与 SLA
-- ----------------------------
alter table abr_lead add column if not exists assigned_time      timestamp   default null;
alter table abr_lead add column if not exists assign_type        varchar(16) default '';
alter table abr_lead add column if not exists sla_due_time       timestamp   default null;
alter table abr_lead add column if not exists sla_status         varchar(16) default 'none';
alter table abr_lead add column if not exists first_contact_time timestamp   default null;
create index if not exists idx_abr_lead_sla on abr_lead (sla_status, sla_due_time) where merged_into is null;

comment on column abr_lead.assigned_time      is '分配给当前负责人的时间';
comment on column abr_lead.assign_type        is '分配方式（auto 自动 / manual 转给同事）';
comment on column abr_lead.sla_due_time       is '首次联系到期时间（分配时间 + 该意向度的时限）';
comment on column abr_lead.sla_status         is 'SLA 状态（字典 abr_sla_status：none 无时限 / pending 待联系 / met 按时联系 / overdue 已超时 / escalated 已升级）';
comment on column abr_lead.first_contact_time is '分配后第一次跟进的时间';

-- ----------------------------
-- 4、员工站内提醒
-- ----------------------------
create table if not exists abr_staff_notice (
  notice_id     bigserial      not null,
  user_id       bigint         not null,
  notice_kind   varchar(32)    not null,
  title         varchar(200)   not null,
  content       varchar(1000)  default '',
  lead_id       bigint         default null,
  dedup_key     varchar(100)   default null,
  read_flag     char(1)        default 'N',
  read_time     timestamp      default null,
  channel_result varchar(200)  default '',
  create_time   timestamp      default now(),
  primary key (notice_id)
);
create index if not exists idx_abr_staff_notice_user on abr_staff_notice (user_id, read_flag, notice_id desc);
create unique index if not exists uk_abr_staff_notice_dedup on abr_staff_notice (user_id, dedup_key) where dedup_key is not null;

comment on table  abr_staff_notice                is '员工站内提醒（同时尝试企业微信应用消息，结果记在 channel_result）';
comment on column abr_staff_notice.notice_id      is '提醒ID';
comment on column abr_staff_notice.user_id        is '收件员工（sys_user）';
comment on column abr_staff_notice.notice_kind    is '类型（字典 abr_notice_kind：lead_assigned 新线索 / sla_overdue 超时 / sla_escalated 升级）';
comment on column abr_staff_notice.title          is '标题';
comment on column abr_staff_notice.content        is '内容（不含手机号）';
comment on column abr_staff_notice.lead_id        is '相关线索';
comment on column abr_staff_notice.dedup_key      is '去重键（同一员工同一事项只提醒一次）';
comment on column abr_staff_notice.read_flag      is '是否已读';
comment on column abr_staff_notice.read_time      is '已读时间';
comment on column abr_staff_notice.channel_result is '企业微信发送结果（未接入为 off）';
comment on column abr_staff_notice.create_time    is '创建时间';

-- ----------------------------
-- 5、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('分配挑选方式', 'abr_assign_strategy'), ('SLA 状态', 'abr_sla_status'), ('员工提醒类型', 'abr_notice_kind')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '轮询',     'round_robin', 'abr_assign_strategy', 'primary'),
  (2, '按负载',   'load',        'abr_assign_strategy', 'success'),
  (1, '无时限',   'none',        'abr_sla_status', 'info'),
  (2, '待联系',   'pending',     'abr_sla_status', 'warning'),
  (3, '按时联系', 'met',         'abr_sla_status', 'success'),
  (4, '已超时',   'overdue',     'abr_sla_status', 'danger'),
  (5, '已升级',   'escalated',   'abr_sla_status', 'danger'),
  (1, '新线索分配', 'lead_assigned', 'abr_notice_kind', 'primary'),
  (2, 'SLA 超时',   'sla_overdue',   'abr_notice_kind', 'warning'),
  (3, 'SLA 升级',   'sla_escalated', 'abr_notice_kind', 'danger')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 6、菜单：获客运营 → 线索分配 + 按钮；我的提醒（所有员工可见的接口不单独设菜单）
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '线索分配', p.menu_id, 21, 'leadAssign', 'abroad/leadAssign/index', '', '', 1, 0, 'C', '0', '0', 'abroad:leadAssign:list', 'tree', 'admin', now(), '线索分配与顾问设置菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:leadAssign:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '修改分配设置', c.menu_id, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'abroad:leadAssign:edit', '#', 'admin', now(), ''
from sys_menu c
where c.perms = 'abroad:leadAssign:list' and not exists (select 1 from sys_menu m where m.perms = 'abroad:leadAssign:edit');

-- ----------------------------
-- 7、定时任务
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '线索分配与 SLA', 'DEFAULT', 'abrLeadTask.assign()', '45 * * * * ?', '3', '1', '0', 'admin', now(),
       'M5-03：自动分配未分配的线索（按设置的意向度），检查首次联系时限，超时提醒负责人、再超时升级'
where not exists (select 1 from sys_job where invoke_target = 'abrLeadTask.assign()');

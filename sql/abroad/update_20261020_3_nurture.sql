-- ----------------------------------------------------------------------------
-- M8-03 nurture_agent 培育序列：事件触发入组、按天推进的多步触达、退出条件、频控与免打扰
--
-- - abr_nurture_sequence：序列定义（入组事件、无互动多少天退出）；abr_nurture_step：步骤（入组后第几天、几点、消息模板、条件、限定剧本）；
--   abr_nurture_enrollment：每个对象在每个序列的进度（下一步、到期时间、状态、最近结果）。序列与步骤都是配置。
-- - 定时任务（编排器触发规则 run，每 10 分钟）推进到期的入组：先判退出（签约 / 流失 / 已约首访 / 有进行中的预约 / 退订 / 未成年人 /
--   超过无互动天数），再按条件发步骤消息；消息只用引擎结果（选校单档位）与院校库事实（截止日），经统一发送接口（触达判断、营销类合规闸门）。
-- - nurture_agent 为“人工确认”时到期的步骤停在“待确认”，员工在管理端确认发送或跳过。
-- 依赖：update_20261020_1_message.sql（消息模板）、update_20261019_z_roles.sql（预置角色）
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_nurture_sequence (
  sequence_id     bigserial      not null,
  sequence_code   varchar(64)    not null,
  sequence_name   varchar(100)   not null,
  enroll_event    varchar(64)    not null,
  inactive_days   integer        default 30,
  status          char(1)        default '0',
  create_by       varchar(64)    default '',
  create_time     timestamp      default now(),
  update_by       varchar(64)    default '',
  update_time     timestamp      default null,
  remark          varchar(500)   default null,
  primary key (sequence_id)
);
create unique index if not exists uk_abr_nurture_sequence on abr_nurture_sequence (sequence_code);

comment on table  abr_nurture_sequence               is '培育序列';
comment on column abr_nurture_sequence.sequence_id   is '序列ID';
comment on column abr_nurture_sequence.sequence_code is '序列编码（触发规则参数 sequence 引用）';
comment on column abr_nurture_sequence.sequence_name is '名称';
comment on column abr_nurture_sequence.enroll_event  is '入组事件（说明用；实际由触发规则订阅事件并以动作 enroll 入组）';
comment on column abr_nurture_sequence.inactive_days is '无互动多少天退出（降级为社群与直播；0 不按此退出）';
comment on column abr_nurture_sequence.status        is '状态（0 正常 1 停用：停用后不再入组、不再推进）';
comment on column abr_nurture_sequence.create_by     is '创建者';
comment on column abr_nurture_sequence.create_time   is '创建时间';
comment on column abr_nurture_sequence.update_by     is '更新者';
comment on column abr_nurture_sequence.update_time   is '更新时间';
comment on column abr_nurture_sequence.remark        is '备注';

create table if not exists abr_nurture_step (
  step_id         bigserial      not null,
  sequence_id     bigint         not null,
  step_no         integer        not null,
  step_name       varchar(100)   not null,
  delay_days      integer        not null default 0,
  send_hour       integer        default null,
  template_code   varchar(64)    not null,
  condition_code  varchar(32)    default 'none',
  playbook_codes  jsonb          not null default '[]'::jsonb,
  status          char(1)        default '0',
  remark          varchar(500)   default null,
  primary key (step_id)
);
create unique index if not exists uk_abr_nurture_step on abr_nurture_step (sequence_id, step_no);

comment on table  abr_nurture_step                is '培育序列的步骤';
comment on column abr_nurture_step.step_id        is '步骤ID';
comment on column abr_nurture_step.sequence_id    is '序列ID';
comment on column abr_nurture_step.step_no        is '顺序（从 1 开始）';
comment on column abr_nurture_step.step_name      is '名称';
comment on column abr_nurture_step.delay_days     is '入组后第几天发（0 为入组当天）';
comment on column abr_nurture_step.send_hour      is '几点发（北京时间 0–23；空为到期即发）';
comment on column abr_nurture_step.template_code  is '消息模板业务编码（abr_message_template）';
comment on column abr_nurture_step.condition_code is '条件（字典 abr_nurture_condition：none 无 / has_plan 选校单有已分档的项目 / has_deadline 45 天内有截止）；不满足时跳过本步';
comment on column abr_nurture_step.playbook_codes is '只在这些剧本生效时发（剧本编码数组；空为不限）；不满足时跳过本步';
comment on column abr_nurture_step.status         is '状态（0 正常 1 停用：跳过）';
comment on column abr_nurture_step.remark         is '备注';

create table if not exists abr_nurture_enrollment (
  enrollment_id   bigserial      not null,
  sequence_id     bigint         not null,
  target_type     varchar(16)    not null,
  target_id       bigint         not null,
  next_step_no    integer        not null default 1,
  next_due_time   timestamp      default null,
  status          varchar(16)    not null default 'active',
  exit_reason     varchar(200)   default '',
  last_result     varchar(500)   default '',
  last_sent_time  timestamp      default null,
  enrolled_time   timestamp      default now(),
  update_time     timestamp      default now(),
  primary key (enrollment_id)
);
create unique index if not exists uk_abr_nurture_enrollment on abr_nurture_enrollment (sequence_id, target_type, target_id);
create index if not exists idx_abr_nurture_enrollment_due on abr_nurture_enrollment (status, next_due_time);

comment on table  abr_nurture_enrollment                is '培育序列入组（每个对象在每个序列一条）';
comment on column abr_nurture_enrollment.enrollment_id  is '入组ID';
comment on column abr_nurture_enrollment.sequence_id    is '序列ID';
comment on column abr_nurture_enrollment.target_type    is '对象类型（lead / app_user / visitor）';
comment on column abr_nurture_enrollment.target_id      is '对象ID';
comment on column abr_nurture_enrollment.next_step_no   is '下一步的顺序';
comment on column abr_nurture_enrollment.next_due_time  is '下一步到期时间';
comment on column abr_nurture_enrollment.status         is '状态（字典 abr_nurture_status：active 进行中 / waiting_confirm 待确认 / completed 已完成 / exited 已退出）';
comment on column abr_nurture_enrollment.exit_reason    is '退出原因';
comment on column abr_nurture_enrollment.last_result    is '最近一步的结果';
comment on column abr_nurture_enrollment.last_sent_time is '最近一次发出时间';
comment on column abr_nurture_enrollment.enrolled_time  is '入组时间';
comment on column abr_nurture_enrollment.update_time    is '更新时间';

-- ----------------------------
-- 种子：新线索培育（M0-10 剧本“培育动作”的落地草案，待创始人确认；同背景案例步骤随 M9-01 案例库加入）
-- ----------------------------
insert into abr_nurture_sequence (sequence_code, sequence_name, enroll_event, inactive_days, status, create_by, create_time, remark)
select 'new_lead', '新线索培育', 'lead_captured', 30, '0', 'admin', now(), '留资当天资料包 → 第 3 天选校单档位 → 第 7 天最近截止 → 第 14 天出分重测（语言冲刺剧本）；30 天无互动退出'
where not exists (select 1 from abr_nurture_sequence where sequence_code = 'new_lead');

insert into abr_nurture_step (sequence_id, step_no, step_name, delay_days, send_hour, template_code, condition_code, playbook_codes, status, remark)
select s.sequence_id, v.no, v.name, v.days, v.hour, v.tpl, v.cond, v.playbooks::jsonb, '0', v.remark
from abr_nurture_sequence s
cross join (values
  (1, '申请规划资料包', 0, null, 'nurture_welcome',     'none',         '[]', '留资当天：选校单、截止日历、材料清单入口'),
  (2, '选校单档位',     3, 10,   'nurture_plan_status', 'has_plan',     '[]', '冲 / 稳 / 保数量来自引擎分档'),
  (3, '最近截止',       7, 10,   'nurture_deadline',    'has_deadline', '[]', '选校单中 45 天内最近一轮截止（院校库）'),
  (4, '出分重测',       14, 10,  'nurture_retest',      'none',         '["language_sprint"]', '只在语言冲刺剧本生效时发')
) as v(no, name, days, hour, tpl, cond, playbooks, remark)
where s.sequence_code = 'new_lead'
  and not exists (select 1 from abr_nurture_step t where t.sequence_id = s.sequence_id and t.step_no = v.no);

-- 消息模板（营销类，停用；开通渠道并填好平台模板 ID 后启用）
insert into abr_message_template (template_code, channel, template_name, category, field_map, content, link, status, create_by, create_time, remark)
select v.code, 'service_account', v.name, 'marketing', v.field_map::jsonb, v.content, '{planUrl}', '1', 'admin', now(), v.remark
from (values
  ('nurture_welcome', '申请规划资料包', '{"thing1": "我的申请规划"}', '你的申请规划已保存：选校单、截止日历、材料清单都在这里，随时可以回来看。', '培育第 1 步'),
  ('nurture_plan_status', '选校单档位', '{"thing1": "冲 {reachCount} / 稳 {matchCount} / 保 {safetyCount}"}',
   '你的选校单：冲刺 {reachCount} 所、主攻 {matchCount} 所、保底 {safetyCount} 所，打开看看是否需要调整。', '数量来自引擎分档，不写概率'),
  ('nurture_deadline', '最近截止', '{"thing1": "{programName}", "time2": "{deadline}"}',
   '{programName} 最近一轮截止是 {deadline}（还有 {daysLeft} 天），以官网为准。', '日期来自院校库'),
  ('nurture_retest', '出分重测', '{"thing1": "雅思够不够"}', '出分了吗？用「雅思够不够」重测一下，看看能申的项目有没有变化。', '语言冲刺剧本')
) as v(code, name, field_map, content, remark)
where not exists (select 1 from abr_message_template t where t.template_code = v.code and t.channel = 'service_account');

-- 触发规则：新线索入组；每 10 分钟推进
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select v.name, v.type, v.event_type, '{}'::jsonb, v.cron, null, 'nurture_agent', v.action, v.params::jsonb, 0, v.ttl, 3, '0', 'admin', now(), 'M8-03'
from (values
  ('新线索 → 入组培育', 'event',    'lead_captured', null,             'enroll', '{"sequence": "new_lead"}', 24),
  ('培育序列推进',      'schedule', null,            '0 5/10 * * * ?', 'run',    '{}', 1)
) as v(name, type, event_type, cron, action, params, ttl)
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'nurture_agent' and t.action = v.action);

-- 字典
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('培育步骤条件', 'abr_nurture_condition'), ('培育入组状态', 'abr_nurture_status')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '无',                 'none',            'abr_nurture_condition', 'info'),
  (2, '选校单有已分档项目', 'has_plan',        'abr_nurture_condition', 'primary'),
  (3, '45 天内有截止',      'has_deadline',    'abr_nurture_condition', 'warning'),
  (1, '进行中',             'active',          'abr_nurture_status',    'primary'),
  (2, '待确认',             'waiting_confirm', 'abr_nurture_status',    'warning'),
  (3, '已完成',             'completed',       'abr_nurture_status',    'success'),
  (4, '已退出',             'exited',          'abr_nurture_status',    'info')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- 菜单：获客运营 → 培育序列 + 按钮；授予负责人
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '培育序列', p.menu_id, 23, 'nurture', 'abroad/nurture/index', '', '', 1, 0, 'C', '0', '0', 'abroad:nurture:list', 'cascader', 'admin', now(), '培育序列与入组'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:nurture:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from sys_menu c
cross join (values ('序列查询', 1, 'abroad:nurture:query'), ('序列新增', 2, 'abroad:nurture:add'), ('序列修改', 3, 'abroad:nurture:edit'),
                   ('序列删除', 4, 'abroad:nurture:remove'), ('入组处理', 5, 'abroad:nurture:handle')) as v(menu_name, order_num, perms)
where c.perms = 'abroad:nurture:list' and not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms like 'abroad:nurture:%'
where r.role_key = 'abr_owner' and r.del_flag = '0'
on conflict do nothing;

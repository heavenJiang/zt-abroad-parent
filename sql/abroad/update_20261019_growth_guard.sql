-- ----------------------------------------------------------------------------
-- M6-02 获客 Agent 执行保障：执行模式、频控与免打扰、预算闸、熔断、一键暂停
--
-- - abr_growth_setting：全局开关（一键暂停）、每周触达上限、免打扰时段、每天 AI 预算（单行）
-- - abr_growth_agent：每个获客 Agent 的执行模式、抽检比例、每天 AI 预算、暂停状态
-- - abr_touch_log / abr_touch_optout：每条触达记录（频控计数）与退订（立即生效）
-- - abr_growth_signal / abr_breaker_rule / abr_breaker_trip：熔断信号、规则与触发记录
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、全局设置（单行，setting_id = 1）
-- ----------------------------
create table if not exists abr_growth_setting (
  setting_id          integer        not null,
  paused              char(1)        default 'N',
  pause_reason        varchar(500)   default '',
  paused_by           varchar(64)    default '',
  paused_time         timestamp      default null,
  resume_time         timestamp      default null,
  weekly_touch_cap    integer        default 3,
  quiet_start         varchar(5)     default '22:00',
  quiet_end           varchar(5)     default '08:00',
  daily_cost_budget   numeric(12,2)  default null,
  daily_token_budget  bigint         default null,
  update_by           varchar(64)    default '',
  update_time         timestamp,
  remark              varchar(500)   default null,
  primary key (setting_id)
);

comment on table  abr_growth_setting                    is '获客自动化全局设置（单行：一键暂停、每周触达上限、免打扰、每天 AI 预算）';
comment on column abr_growth_setting.setting_id         is '设置ID（固定为 1）';
comment on column abr_growth_setting.paused             is '全局暂停（Y 暂停全部获客 Agent：不执行任务、不调用模型、不对外触达）';
comment on column abr_growth_setting.pause_reason       is '暂停原因';
comment on column abr_growth_setting.paused_by          is '暂停人（熔断为 breaker）';
comment on column abr_growth_setting.paused_time        is '暂停时间';
comment on column abr_growth_setting.resume_time        is '最近一次恢复时间（熔断只统计此后的信号）';
comment on column abr_growth_setting.weekly_touch_cap   is '每个对象每 7 天最多营销触达次数';
comment on column abr_growth_setting.quiet_start        is '免打扰开始（HH:mm，北京时间）';
comment on column abr_growth_setting.quiet_end          is '免打扰结束（HH:mm，北京时间；早于开始表示跨午夜）';
comment on column abr_growth_setting.daily_cost_budget  is '全部获客 Agent 每天 AI 费用上限（元，北京时间自然日；为空不限）';
comment on column abr_growth_setting.daily_token_budget is '全部获客 Agent 每天 AI token 上限（输入 + 输出；为空不限）';
comment on column abr_growth_setting.update_by          is '更新者';
comment on column abr_growth_setting.update_time        is '更新时间';
comment on column abr_growth_setting.remark             is '备注';

insert into abr_growth_setting (setting_id, paused, weekly_touch_cap, quiet_start, quiet_end, daily_cost_budget, daily_token_budget, update_by, update_time, remark)
select 1, 'N', 3, '22:00', '08:00', 50.00, null, 'admin', now(), '预算为草案（《获客系统设计》十五：AI 预算上限待创始人确定）'
where not exists (select 1 from abr_growth_setting where setting_id = 1);

-- ----------------------------
-- 2、获客 Agent 运行配置
-- ----------------------------
create table if not exists abr_growth_agent (
  agent_id            bigserial      not null,
  agent_code          varchar(64)    not null,
  agent_name          varchar(100)   not null,
  exec_mode           varchar(16)    not null,
  target_mode         varchar(16)    default null,
  sample_rate         numeric(5,4)   default 0,
  daily_cost_budget   numeric(12,2)  default null,
  daily_token_budget  bigint         default null,
  paused              char(1)        default 'N',
  pause_reason        varchar(500)   default '',
  paused_by           varchar(64)    default '',
  paused_time         timestamp      default null,
  resume_time         timestamp      default null,
  create_by           varchar(64)    default '',
  create_time         timestamp,
  update_by           varchar(64)    default '',
  update_time         timestamp,
  remark              varchar(500)   default null,
  primary key (agent_id)
);
create unique index if not exists uk_abr_growth_agent_code on abr_growth_agent (agent_code);

comment on table  abr_growth_agent                    is '获客 Agent 运行配置（执行模式、抽检、每天 AI 预算、暂停；Agent 在代码中定义，管理端只改配置）';
comment on column abr_growth_agent.agent_id           is 'ID';
comment on column abr_growth_agent.agent_code         is 'Agent 编码（字典 abr_growth_agent；调用模型时与 abr_ai_agent.agent_code 相同）';
comment on column abr_growth_agent.agent_name         is 'Agent 名称';
comment on column abr_growth_agent.exec_mode          is '执行模式（字典 abr_exec_mode：auto 全自动 / auto_sample 全自动 + 抽检 / confirm 人工确认后发出 / off 停用）';
comment on column abr_growth_agent.target_mode        is '试运行达标后切换到的执行模式（《获客系统设计》第六节的默认模式；仅提示）';
comment on column abr_growth_agent.sample_rate        is '抽检比例 0–1（全自动 + 抽检时生效）';
comment on column abr_growth_agent.daily_cost_budget  is '每天 AI 费用上限（元；为空不限，仍受全局上限约束）';
comment on column abr_growth_agent.daily_token_budget is '每天 AI token 上限（为空不限）';
comment on column abr_growth_agent.paused             is '暂停（Y 暂停：不执行任务、不调用模型、不对外触达；手动或熔断）';
comment on column abr_growth_agent.pause_reason       is '暂停原因';
comment on column abr_growth_agent.paused_by          is '暂停人（熔断为 breaker）';
comment on column abr_growth_agent.paused_time        is '暂停时间';
comment on column abr_growth_agent.resume_time        is '最近一次恢复时间（熔断只统计此后的信号）';
comment on column abr_growth_agent.create_by          is '创建者';
comment on column abr_growth_agent.create_time        is '创建时间';
comment on column abr_growth_agent.update_by          is '更新者';
comment on column abr_growth_agent.update_time        is '更新时间';
comment on column abr_growth_agent.remark             is '备注';

-- 对外发出内容或消息的 Agent 先“人工确认”试运行（《获客系统设计》十五：建议新 Agent 先人工确认跑一周），只在内部生效的直接全自动
insert into abr_growth_agent (agent_code, agent_name, exec_mode, target_mode, sample_rate, create_by, create_time, remark)
select v.code, v.name, v.mode, v.target, v.rate, 'admin', now(), v.remark
from (values
  ('topic_planner',      '选题',     'auto',    'auto',        0,    '内部：只排选题'),
  ('content_writer',     '内容创作', 'confirm', 'auto_sample', 0.2,  '对外内容，试运行期人工确认'),
  ('content_designer',   '图卡',     'auto',    'auto',        0,    '确定性渲染，随稿件一起过闸门'),
  ('compliance_checker', '合规审查', 'auto',    'auto',        0,    '闸门本身'),
  ('publisher',          '发布',     'confirm', 'auto',        0,    '对外发布，试运行期人工确认'),
  ('engagement_agent',   '互动',     'confirm', 'auto_sample', 0.2,  '对外回复，试运行期人工确认'),
  ('lead_scorer',        '线索评分', 'auto',    'auto',        0,    '内部'),
  ('lead_card',          '线索卡片', 'auto',    'auto',        0,    '仅内部可见'),
  ('nurture_agent',      '培育',     'confirm', 'auto_sample', 0.2,  '对外消息，试运行期人工确认'),
  ('booking_agent',      '邀约',     'confirm', 'auto',        0,    '对外消息，试运行期人工确认'),
  ('referral_agent',     '口碑',     'confirm', 'auto',        0,    '对外消息；奖励发放始终人工确认'),
  ('growth_analyst',     '增长分析', 'auto',    'auto',        0,    '内部；只在边界内调参'),
  ('intel_watcher',      '情报监测', 'auto',    'auto',        0,    '影响大的变化由顾问确认（M7-07）'),
  ('demand_miner',       '需求洞察', 'auto',    'auto',        0,    '仅内部可见')
) as v(code, name, mode, target, rate, remark)
where not exists (select 1 from abr_growth_agent a where a.agent_code = v.code);

-- ----------------------------
-- 3、触达记录与退订
-- ----------------------------
create table if not exists abr_touch_log (
  touch_id       bigserial      not null,
  task_id        bigint         default null,
  agent_code     varchar(64)    not null,
  target_type    varchar(32)    not null,
  target_id      bigint         not null,
  channel        varchar(32)    not null,
  category       varchar(16)    not null,
  status         varchar(16)    not null,
  reason         varchar(500)   default '',
  content_ref    varchar(128)   default null,
  create_time    timestamp      default now(),
  primary key (touch_id)
);
create index if not exists idx_abr_touch_log_target on abr_touch_log (target_type, target_id, create_time);
create index if not exists idx_abr_touch_log_agent on abr_touch_log (agent_code, create_time);

comment on table  abr_touch_log             is '触达记录（每条对外消息一行：发出、失败、被频控 / 免打扰 / 退订 / 暂停拦下；频控按此计数）';
comment on column abr_touch_log.touch_id    is '触达ID';
comment on column abr_touch_log.task_id     is '编排任务ID';
comment on column abr_touch_log.agent_code  is '发出的 Agent';
comment on column abr_touch_log.target_type is '对象类型（visitor / app_user / lead）';
comment on column abr_touch_log.target_id   is '对象ID';
comment on column abr_touch_log.channel     is '渠道（M6-05 渠道适配器：service_account / wecom / mp_subscribe / sms 等）';
comment on column abr_touch_log.category    is '类别（字典 abr_touch_category：marketing 营销 / service 服务提醒）';
comment on column abr_touch_log.status      is '状态（字典 abr_touch_status：sent 已发出 / failed 发送失败 / blocked 被拦下）';
comment on column abr_touch_log.reason      is '拦下或失败原因';
comment on column abr_touch_log.content_ref is '内容引用（模板编码、内容编号等）';
comment on column abr_touch_log.create_time is '时间';

create table if not exists abr_touch_optout (
  optout_id      bigserial      not null,
  target_type    varchar(32)    not null,
  target_id      bigint         not null,
  scope          varchar(16)    not null,
  source         varchar(16)    not null,
  reason         varchar(500)   default '',
  create_by      varchar(64)    default '',
  create_time    timestamp      default now(),
  primary key (optout_id)
);
create unique index if not exists uk_abr_touch_optout on abr_touch_optout (target_type, target_id, scope);

comment on table  abr_touch_optout             is '退订（立即生效；恢复订阅即删除该行）';
comment on column abr_touch_optout.optout_id   is '退订ID';
comment on column abr_touch_optout.target_type is '对象类型（visitor / app_user / lead）';
comment on column abr_touch_optout.target_id   is '对象ID';
comment on column abr_touch_optout.scope       is '范围（marketing 营销消息）';
comment on column abr_touch_optout.source      is '来源（user 本人 / admin 员工代为退订）';
comment on column abr_touch_optout.reason      is '原因';
comment on column abr_touch_optout.create_by   is '操作人';
comment on column abr_touch_optout.create_time is '退订时间';

-- ----------------------------
-- 4、熔断：信号、规则、触发记录
-- ----------------------------
create table if not exists abr_growth_signal (
  signal_id      bigserial      not null,
  signal_type    varchar(32)    not null,
  agent_code     varchar(64)    default null,
  account_code   varchar(64)    default null,
  target_type    varchar(32)    default null,
  target_id      bigint         default null,
  task_id        bigint         default null,
  detail         jsonb          default '{}'::jsonb,
  source         varchar(16)    default 'system',
  create_by      varchar(64)    default '',
  create_time    timestamp      default now(),
  primary key (signal_id)
);
create index if not exists idx_abr_growth_signal_type_time on abr_growth_signal (signal_type, create_time);
create index if not exists idx_abr_growth_signal_agent_time on abr_growth_signal (agent_code, create_time);

comment on table  abr_growth_signal              is '熔断信号（投诉、平台违规提示、差评回复、合规拦截 / 通过、退订；系统上报或员工录入）';
comment on column abr_growth_signal.signal_id    is '信号ID';
comment on column abr_growth_signal.signal_type  is '类型（字典 abr_signal_type）';
comment on column abr_growth_signal.agent_code   is '相关 Agent（退订记到最近一次触达该对象的 Agent）';
comment on column abr_growth_signal.account_code is '相关渠道账号（M7-01 账号矩阵）';
comment on column abr_growth_signal.target_type  is '相关对象类型';
comment on column abr_growth_signal.target_id    is '相关对象ID';
comment on column abr_growth_signal.task_id      is '相关编排任务ID';
comment on column abr_growth_signal.detail       is '详情（jsonb）';
comment on column abr_growth_signal.source       is '来源（system 系统 / manual 员工录入）';
comment on column abr_growth_signal.create_by    is '录入人';
comment on column abr_growth_signal.create_time  is '发生时间';

create table if not exists abr_breaker_rule (
  rule_id        bigserial      not null,
  rule_code      varchar(64)    not null,
  rule_name      varchar(100)   not null,
  metric         varchar(32)    not null,
  scope          varchar(16)    not null,
  agent_code     varchar(64)    default null,
  window_hours   integer        default 24,
  threshold      numeric(12,4)  not null,
  min_sample     integer        default 0,
  status         char(1)        default '0',
  create_by      varchar(64)    default '',
  create_time    timestamp,
  update_by      varchar(64)    default '',
  update_time    timestamp,
  remark         varchar(500)   default null,
  primary key (rule_id)
);
create unique index if not exists uk_abr_breaker_rule_code on abr_breaker_rule (rule_code);

comment on table  abr_breaker_rule              is '熔断规则（指标在时间窗内达到阈值时自动暂停：按 Agent 规则暂停该 Agent，全局规则暂停全部）';
comment on column abr_breaker_rule.rule_id      is '规则ID';
comment on column abr_breaker_rule.rule_code    is '规则编码';
comment on column abr_breaker_rule.rule_name    is '规则名称';
comment on column abr_breaker_rule.metric       is '指标（字典 abr_breaker_metric：计数类为信号条数，比率类为 0–1）';
comment on column abr_breaker_rule.scope        is '范围（agent 逐个 Agent 统计并暂停该 Agent / global 合计统计并全局暂停）';
comment on column abr_breaker_rule.agent_code   is '只统计该 Agent（范围为 agent 时可选；为空为全部 Agent 逐个统计）';
comment on column abr_breaker_rule.window_hours is '统计时间窗（小时；从暂停恢复的时刻起算不早于恢复时间）';
comment on column abr_breaker_rule.threshold    is '阈值（达到即触发）';
comment on column abr_breaker_rule.min_sample   is '最少样本数（比率类指标的分母不足时不触发）';
comment on column abr_breaker_rule.status       is '状态（0正常 1停用）';
comment on column abr_breaker_rule.create_by    is '创建者';
comment on column abr_breaker_rule.create_time  is '创建时间';
comment on column abr_breaker_rule.update_by    is '更新者';
comment on column abr_breaker_rule.update_time  is '更新时间';
comment on column abr_breaker_rule.remark       is '备注';

insert into abr_breaker_rule (rule_code, rule_name, metric, scope, agent_code, window_hours, threshold, min_sample, status, create_by, create_time, remark)
select v.code, v.name, v.metric, v.scope, null, v.hours, v.threshold, v.sample, '0', 'admin', now(), '阈值为草案，按试运行数据调整'
from (values
  ('agent_complaint',        '单个 Agent 24 小时内投诉 ≥ 2',         'complaint',             'agent',  24, 2,    0),
  ('global_complaint',       '全部 24 小时内投诉 ≥ 3',               'complaint',             'global', 24, 3,    0),
  ('global_violation',       '平台违规提示 ≥ 1（全局暂停）',         'platform_violation',    'global', 24, 1,    0),
  ('agent_negative_reply',   '单个 Agent 24 小时内差评回复 ≥ 5',     'negative_reply',        'agent',  24, 5,    0),
  ('agent_unsubscribe_rate', '单个 Agent 24 小时退订率 ≥ 5%',        'unsubscribe_rate',      'agent',  24, 0.05, 40),
  ('agent_compliance_block', '单个 Agent 24 小时合规拦截率 ≥ 30%',   'compliance_block_rate', 'agent',  24, 0.30, 10)
) as v(code, name, metric, scope, hours, threshold, sample)
where not exists (select 1 from abr_breaker_rule r where r.rule_code = v.code);

create table if not exists abr_breaker_trip (
  trip_id        bigserial      not null,
  rule_id        bigint         default null,
  rule_code      varchar(64)    not null,
  metric         varchar(32)    not null,
  scope          varchar(16)    not null,
  agent_code     varchar(64)    default null,
  metric_value   numeric(12,4)  default null,
  sample_size    integer        default null,
  threshold      numeric(12,4)  default null,
  window_start   timestamp      default null,
  action         varchar(200)   default '',
  dedup_key      varchar(128)   default null,
  resolved_by    varchar(64)    default null,
  resolved_time  timestamp      default null,
  create_time    timestamp      default now(),
  primary key (trip_id)
);
create unique index if not exists uk_abr_breaker_trip_dedup on abr_breaker_trip (dedup_key) where dedup_key is not null;
create index if not exists idx_abr_breaker_trip_time on abr_breaker_trip (create_time);

comment on table  abr_breaker_trip               is '熔断与预算闸触发记录（触发即暂停；恢复后记录处理人）';
comment on column abr_breaker_trip.trip_id       is '记录ID';
comment on column abr_breaker_trip.rule_id       is '熔断规则ID（预算闸为空）';
comment on column abr_breaker_trip.rule_code     is '规则编码（预算闸为 budget_agent / budget_global）';
comment on column abr_breaker_trip.metric        is '指标';
comment on column abr_breaker_trip.scope         is '范围（agent / global）';
comment on column abr_breaker_trip.agent_code    is '暂停的 Agent（全局为空）';
comment on column abr_breaker_trip.metric_value  is '触发时的指标值';
comment on column abr_breaker_trip.sample_size   is '样本数（比率类的分母）';
comment on column abr_breaker_trip.threshold     is '阈值';
comment on column abr_breaker_trip.window_start  is '统计起点';
comment on column abr_breaker_trip.action        is '动作（暂停对象；预算闸为当天停止调用，次日自动恢复）';
comment on column abr_breaker_trip.dedup_key     is '去重键（预算闸每个对象每天一条）';
comment on column abr_breaker_trip.resolved_by   is '恢复人';
comment on column abr_breaker_trip.resolved_time is '恢复时间';
comment on column abr_breaker_trip.create_time   is '触发时间';

-- ----------------------------
-- 5、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('Agent 执行模式', 'abr_exec_mode',      '获客 Agent 的执行模式'),
  ('触达类别',       'abr_touch_category', '营销消息 / 服务提醒'),
  ('触达状态',       'abr_touch_status',   '触达记录状态'),
  ('熔断信号类型',   'abr_signal_type',    '熔断统计用的信号'),
  ('熔断指标',       'abr_breaker_metric', '熔断规则的指标')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '全自动',           'auto',        'abr_exec_mode', 'success', ''),
  (2, '全自动 + 抽检',    'auto_sample', 'abr_exec_mode', 'primary', ''),
  (3, '人工确认后发出',   'confirm',     'abr_exec_mode', 'warning', ''),
  (4, '停用',             'off',         'abr_exec_mode', 'info',    '不生成、不执行任务'),
  (1, '营销',     'marketing', 'abr_touch_category', 'warning', '受退订、每周上限、未成年人限制'),
  (2, '服务提醒', 'service',   'abr_touch_category', 'primary', '本人订阅的提醒；只受免打扰与暂停限制'),
  (1, '已发出',   'sent',    'abr_touch_status', 'success', ''),
  (2, '发送失败', 'failed',  'abr_touch_status', 'danger',  ''),
  (3, '被拦下',   'blocked', 'abr_touch_status', 'info',    ''),
  (1, '投诉',         'complaint',          'abr_signal_type', 'danger',  ''),
  (2, '平台违规提示', 'platform_violation', 'abr_signal_type', 'danger',  ''),
  (3, '差评回复',     'negative_reply',     'abr_signal_type', 'warning', ''),
  (4, '合规拦截',     'compliance_block',   'abr_signal_type', 'warning', 'M6-03 上报'),
  (5, '合规通过',     'compliance_pass',    'abr_signal_type', 'success', 'M6-03 上报（拦截率分母）'),
  (6, '退订',         'unsubscribe',        'abr_signal_type', 'info',    ''),
  (1, '投诉数',         'complaint',             'abr_breaker_metric', 'danger',  '计数'),
  (2, '平台违规提示数', 'platform_violation',    'abr_breaker_metric', 'danger',  '计数'),
  (3, '差评回复数',     'negative_reply',        'abr_breaker_metric', 'warning', '计数'),
  (4, '退订率',         'unsubscribe_rate',      'abr_breaker_metric', 'warning', '退订 / 营销触达发出数'),
  (5, '合规拦截率',     'compliance_block_rate', 'abr_breaker_metric', 'warning', '拦截 / （拦截 + 通过）'),
  (7, '已拦截',         'blocked',               'abr_ai_call_status', 'info',    '暂停、停用或超出 AI 预算，未调用模型')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 6、菜单：获客运营 → Agent 运行、熔断记录、触达记录 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (values
  ('Agent 运行', 5, 'growthAgent', 'abroad/growthAgent/index', 'abroad:growthAgent:list', 'monitor', '获客 Agent 执行模式、预算、暂停与全局开关菜单'),
  ('熔断',       6, 'breaker',     'abroad/breaker/index',     'abroad:breaker:list',     'bug',     '熔断规则、信号与触发记录菜单'),
  ('触达记录',   7, 'touchLog',    'abroad/touchLog/index',    'abroad:touchLog:list',    'message', '触达记录与退订菜单')
) as v(menu_name, order_num, path, component, perms, icon, remark)
cross join (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:growthAgent:list', 'Agent 查询',   1, 'abroad:growthAgent:query'),
  ('abroad:growthAgent:list', 'Agent 修改',   2, 'abroad:growthAgent:edit'),
  ('abroad:growthAgent:list', '暂停与恢复',   3, 'abroad:growthAgent:pause'),
  ('abroad:growthAgent:list', '全局设置修改', 4, 'abroad:growthSetting:edit'),
  ('abroad:growthAgent:list', '全局暂停与恢复', 5, 'abroad:growthSetting:pause'),
  ('abroad:breaker:list',     '规则查询',     1, 'abroad:breaker:query'),
  ('abroad:breaker:list',     '规则新增',     2, 'abroad:breaker:add'),
  ('abroad:breaker:list',     '规则修改',     3, 'abroad:breaker:edit'),
  ('abroad:breaker:list',     '规则删除',     4, 'abroad:breaker:remove'),
  ('abroad:breaker:list',     '录入信号',     5, 'abroad:breaker:signal'),
  ('abroad:touchLog:list',    '代为退订',     1, 'abroad:touchLog:optout')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

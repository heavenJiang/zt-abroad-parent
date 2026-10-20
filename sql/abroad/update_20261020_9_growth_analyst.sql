-- ----------------------------------------------------------------------------
-- M9-04 growth_analyst：A/B 实验、边界内调参、周报
--
-- - abr_experiment / abr_experiment_unit：钩子文案（内容文末引导）A/B 实验。同一平台同时只运行一个；
--   content_writer 写稿时按选题确定性分组（同一选题总在同一组）并记录内容属于哪一组；
--   指标 click_rate（访客数 ÷ 回填阅读数）或 tool_rate（用过工具的访客 ÷ 访客），与对照组做双比例检验。
--   growth_analyst 每天评估：样本够且显著更好 → 胜出组；到最长天数仍无差异 → 保留对照。执行模式为自动时直接结束实验，
--   之后该平台的新内容一律用胜出文案；人工确认时标为“待确认”由员工结束。
-- - abr_growth_param_log：边界内调参记录。每周按近 28 天各内容类型“每条内容带来的工具使用人数”调整各剧本的内容配比：
--   表现明显好 / 差的类型每次最多 ±step 个百分点，每类不超出边界（abroad.growth-analyst.mix-bounds），合计保持 100；
--   样本不足、Agent 暂停或出错时保持上周参数；人工确认模式只写“建议”。员工可撤销已生效的调整。
-- - abr_growth_report：每周一 08:00 的周报（本周与上周漏斗、每周活跃选校单、最好和最差的内容、各类型表现、熔断记录、实验与调参）；
--   数字由系统计算，growth_analyst 只写摘要与建议（经 AI 网关，内部可见），摘要中的数字须在数据中出现，否则用模板文字。
-- - 发布时段实验随 M7-05（发布适配器）；季度变化报告素材随 M7-07（院校库变更记录）。
-- 依赖：update_20261020_7_attribution.sql、update_20261019_writer.sql（content_writer）、update_20261019_growth_guard.sql（获客 Agent）
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_experiment (
  experiment_id    bigserial      not null,
  experiment_name  varchar(100)   not null,
  experiment_type  varchar(32)    not null default 'hook_copy',
  platform         varchar(32)    not null,
  metric           varchar(32)    not null default 'click_rate',
  variants         jsonb          not null default '[]'::jsonb,
  min_sample       integer        not null default 1000,
  max_days         integer        not null default 28,
  alpha            numeric(4,3)   not null default 0.050,
  status           varchar(16)    not null default 'draft',
  winner_code      varchar(16)    default null,
  result           jsonb          default null,
  start_time       timestamp      default null,
  end_time         timestamp      default null,
  evaluated_time   timestamp      default null,
  conclude_reason  varchar(200)   default '',
  create_by        varchar(64)    default '',
  create_time      timestamp      default now(),
  update_by        varchar(64)    default '',
  update_time      timestamp      default null,
  remark           varchar(500)   default null,
  primary key (experiment_id)
);
create unique index if not exists uk_abr_experiment_running on abr_experiment (experiment_type, platform) where status in ('running', 'ready');

comment on table  abr_experiment                 is 'A/B 实验（M9-04：钩子文案）';
comment on column abr_experiment.experiment_id   is '实验ID';
comment on column abr_experiment.experiment_name is '名称';
comment on column abr_experiment.experiment_type is '类型（字典 abr_experiment_type：hook_copy 钩子文案）';
comment on column abr_experiment.platform        is '平台（字典 abr_platform；同一平台同时只运行一个同类实验）';
comment on column abr_experiment.metric          is '指标（字典 abr_experiment_metric：click_rate 进入率 = 访客 ÷ 阅读 / tool_rate 工具使用率 = 用过工具的访客 ÷ 访客）';
comment on column abr_experiment.variants        is '分组 [{code, name, template, weight, control}]：template 为文末引导（{hook} 工具名、{codeword} 私信口令），weight 为分流权重，恰好一个对照组';
comment on column abr_experiment.min_sample      is '每组最少样本（指标的分母）';
comment on column abr_experiment.max_days        is '最长运行天数（到期仍无显著差异则保留对照）';
comment on column abr_experiment.alpha           is '显著性水平';
comment on column abr_experiment.status          is '状态（字典 abr_experiment_status：draft 草稿 / running 运行中 / ready 待确认结论 / concluded 已结束 / stopped 已停止）';
comment on column abr_experiment.winner_code     is '胜出组（结束后该平台新内容一律用它；保留对照时为对照组）';
comment on column abr_experiment.result          is '最近一次评估结果（各组样本、转化、比率、p 值与建议）';
comment on column abr_experiment.start_time      is '开始时间';
comment on column abr_experiment.end_time        is '结束 / 停止时间';
comment on column abr_experiment.evaluated_time  is '最近评估时间';
comment on column abr_experiment.conclude_reason is '结论说明';
comment on column abr_experiment.create_by       is '创建者';
comment on column abr_experiment.create_time     is '创建时间';
comment on column abr_experiment.update_by       is '更新者';
comment on column abr_experiment.update_time     is '更新时间';
comment on column abr_experiment.remark          is '备注';

create table if not exists abr_experiment_unit (
  experiment_id  bigint        not null,
  unit_type      varchar(16)   not null,
  unit_id        bigint        not null,
  variant_code   varchar(16)   not null,
  create_time    timestamp     default now(),
  primary key (experiment_id, unit_type, unit_id)
);
create index if not exists idx_abr_experiment_unit_unit on abr_experiment_unit (unit_type, unit_id);

comment on table  abr_experiment_unit               is '实验分组记录（钩子文案：每条内容一行）';
comment on column abr_experiment_unit.experiment_id is '实验';
comment on column abr_experiment_unit.unit_type     is '单元类型（content 内容）';
comment on column abr_experiment_unit.unit_id       is '单元ID';
comment on column abr_experiment_unit.variant_code  is '分组';
comment on column abr_experiment_unit.create_time   is '分组时间';

create table if not exists abr_growth_report (
  report_id     bigserial     not null,
  week_start    date          not null,
  data          jsonb         not null default '{}'::jsonb,
  narrative     jsonb         default null,
  ai_status     varchar(16)   default '',
  ai_call_id    bigint        default null,
  create_by     varchar(64)   default '',
  create_time   timestamp     default now(),
  update_time   timestamp     default null,
  primary key (report_id)
);
create unique index if not exists uk_abr_growth_report_week on abr_growth_report (week_start);

comment on table  abr_growth_report             is '增长周报（每周一生成上一周；数字由系统计算，摘要与建议由 growth_analyst 撰写）';
comment on column abr_growth_report.report_id   is '周报ID';
comment on column abr_growth_report.week_start  is '所报告的周（周一）';
comment on column abr_growth_report.data        is '数据：漏斗、每周活跃、内容排行、类型表现、熔断、实验、调参';
comment on column abr_growth_report.narrative   is '摘要与建议 {summary, highlights[], suggestions[], source: ai / template}';
comment on column abr_growth_report.ai_status   is 'AI 调用状态（success / fallback 用模板 / blocked 等）';
comment on column abr_growth_report.ai_call_id  is 'AI 调用日志';
comment on column abr_growth_report.create_by   is '创建者';
comment on column abr_growth_report.create_time is '生成时间';
comment on column abr_growth_report.update_time is '重新生成时间';

create table if not exists abr_growth_param_log (
  log_id       bigserial      not null,
  param_key    varchar(32)    not null,
  scope        varchar(64)    not null,
  old_value    jsonb          not null,
  new_value    jsonb          not null,
  reason       varchar(500)   default '',
  status       varchar(16)    not null default 'applied',
  report_id    bigint         default null,
  create_by    varchar(64)    default '',
  create_time  timestamp      default now(),
  update_by    varchar(64)    default '',
  update_time  timestamp      default null,
  primary key (log_id)
);
create index if not exists idx_abr_growth_param_log_report on abr_growth_param_log (report_id);

comment on table  abr_growth_param_log             is 'growth_analyst 边界内调参记录';
comment on column abr_growth_param_log.log_id      is 'ID';
comment on column abr_growth_param_log.param_key   is '参数（content_mix 内容配比）';
comment on column abr_growth_param_log.scope       is '范围（剧本编码）';
comment on column abr_growth_param_log.old_value   is '调整前';
comment on column abr_growth_param_log.new_value   is '调整后';
comment on column abr_growth_param_log.reason      is '依据（各类型表现）';
comment on column abr_growth_param_log.status      is '状态（字典 abr_param_status：applied 已生效 / suggested 建议待确认 / reverted 已撤销 / dismissed 已忽略）';
comment on column abr_growth_param_log.report_id   is '所属周报';
comment on column abr_growth_param_log.create_by   is '创建者';
comment on column abr_growth_param_log.create_time is '创建时间';
comment on column abr_growth_param_log.update_by   is '处理人';
comment on column abr_growth_param_log.update_time is '处理时间';

-- ----------------------------
-- AI Agent：周报摘要（内部可见）
-- ----------------------------
insert into abr_ai_agent (agent_code, agent_name, description, review_policy, sample_rate, primary_model, backup_model,
                          prompt_version, timeout_ms, max_output_tokens, temperature, status, create_by, create_time, remark)
select 'growth_analyst', '增长分析', '按系统算好的周数据写周报摘要与建议（数字须来自数据；内部使用）',
       'internal_only', 0, 'mock:default', 'mock:default', 'v1', 45000, 1500, 0.30, '0', 'admin', now(), '失败时用模板文字'
where not exists (select 1 from abr_ai_agent where agent_code = 'growth_analyst');

-- ----------------------------
-- 触发规则：每天评估实验；每周一 08:00 周报与调参（启用编排器种子中预留的规则，只启用一次，之后由员工控制）
-- ----------------------------
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select '每天 06:30 评估 A/B 实验', 'schedule', null, '{}'::jsonb, '0 30 6 * * *', null, 'growth_analyst', 'evaluate', '{}'::jsonb, 0, 12, 3, '0', 'admin', now(), 'M9-04'
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'growth_analyst' and t.action = 'evaluate');

update abr_growth_trigger set status = '0', remark = 'M9-04：生成上一周的周报并在边界内调整内容配比', update_by = 'admin', update_time = now()
where agent_code = 'growth_analyst' and action = 'weekly_report' and remark = 'M9-04 上线时启用';

-- ----------------------------
-- 字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('实验类型', 'abr_experiment_type'), ('实验指标', 'abr_experiment_metric'), ('实验状态', 'abr_experiment_status'),
             ('调参状态', 'abr_param_status')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '钩子文案',     'hook_copy',  'abr_experiment_type',   'primary'),
  (1, '进入率',       'click_rate', 'abr_experiment_metric', 'primary'),
  (2, '工具使用率',   'tool_rate',  'abr_experiment_metric', 'success'),
  (1, '草稿',         'draft',      'abr_experiment_status', 'info'),
  (2, '运行中',       'running',    'abr_experiment_status', 'primary'),
  (3, '待确认结论',   'ready',      'abr_experiment_status', 'warning'),
  (4, '已结束',       'concluded',  'abr_experiment_status', 'success'),
  (5, '已停止',       'stopped',    'abr_experiment_status', 'danger'),
  (1, '已生效',       'applied',    'abr_param_status',      'success'),
  (2, '建议待确认',   'suggested',  'abr_param_status',      'warning'),
  (3, '已撤销',       'reverted',   'abr_param_status',      'info'),
  (4, '已忽略',       'dismissed',  'abr_param_status',      'info')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 菜单：获客运营 → A/B 实验、增长周报
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
cross join (values ('A/B 实验', 28, 'experiment', 'abroad/experiment/index', 'abroad:experiment:list', 'example', '钩子文案 A/B 实验'),
                   ('增长周报', 29, 'growthReport', 'abroad/growthReport/index', 'abroad:growthReport:list', 'documentation', 'growth_analyst 周报与调参记录')
) as v(menu_name, order_num, path, component, perms, icon, remark)
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), v.remark
from (values ('abroad:experiment:list', '实验新增', 1, 'abroad:experiment:add', ''),
             ('abroad:experiment:list', '实验修改', 2, 'abroad:experiment:edit', '含开始、停止、结束'),
             ('abroad:experiment:list', '实验删除', 3, 'abroad:experiment:remove', '只删草稿'),
             ('abroad:growthReport:list', '立即生成周报', 1, 'abroad:growthReport:run', ''),
             ('abroad:growthReport:list', '调参处理', 2, 'abroad:growthReport:param', '撤销、采纳、忽略')
) as v(parent_perms, menu_name, order_num, perms, remark)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms like 'abroad:experiment:%' or m.perms like 'abroad:growthReport:%'
where r.role_key = 'abr_owner' and r.del_flag = '0'
on conflict do nothing;

-- 员工提醒类型：周报
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select 20, '增长周报', 'growth_report', 'abr_notice_kind', 'primary', 'N', '0', 'admin', now(), ''
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_notice_kind' and d.dict_value = 'growth_report');

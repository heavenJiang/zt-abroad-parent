-- ----------------------------------------------------------------------------
-- M5-02 lead_scorer 线索评分：规则分 + 理由，顾问可改并记录原因
--
-- - 评分规则 abr_lead_score_rule：规则种类固定（联系方式、转化时刻、入学时间、工具与测评、选校单、家长参与、来源、活跃度、分级线），
--   分值与阈值是配置，管理端可改、可停用；不调用大模型（确定性，同一输入同一结果）。
-- - abr_lead：rule_grade 规则等级、grade 最终等级（顾问改过时以顾问为准，规则重算不覆盖）、score_reasons 每条得分的理由。
-- - abr_lead_score_log：等级变化与顾问修改的历史（顾问修改须填原因）。
-- - 触发：新线索（lead_captured）、转化时刻（plan_moment）、绑定手机号，及每天 06:00 重算（活跃度随时间变化）；
--   等级变化写 lead_grade_changed 事件（M5-03 分配与 SLA、M8-04 邀约订阅）。
-- 依赖：update_20261019_lead.sql、update_20261018_orchestrator.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、评分规则
-- ----------------------------
create table if not exists abr_lead_score_rule (
  rule_id      bigserial      not null,
  rule_code    varchar(32)    not null,
  rule_name    varchar(64)    not null,
  params       jsonb          not null default '{}'::jsonb,
  sort_order   integer        default 0,
  status       char(1)        default '0',
  create_by    varchar(64)    default '',
  create_time  timestamp,
  update_by    varchar(64)    default '',
  update_time  timestamp,
  remark       varchar(500)   default null,
  primary key (rule_id)
);
create unique index if not exists uk_abr_lead_score_rule_code on abr_lead_score_rule (rule_code);

comment on table  abr_lead_score_rule             is '线索评分规则（规则种类固定，分值与阈值可配置；停用的规则不计分，分级线不能停用）';
comment on column abr_lead_score_rule.rule_id     is '规则ID';
comment on column abr_lead_score_rule.rule_code   is '规则编码（contact / moment / intake / tools / plan / parent / source / recency / grade）';
comment on column abr_lead_score_rule.rule_name   is '规则名称';
comment on column abr_lead_score_rule.params      is '分值与阈值（jsonb：键固定，见备注；值为整数）';
comment on column abr_lead_score_rule.sort_order  is '显示顺序';
comment on column abr_lead_score_rule.status      is '状态（0正常 1停用）';
comment on column abr_lead_score_rule.create_by   is '创建者';
comment on column abr_lead_score_rule.create_time is '创建时间';
comment on column abr_lead_score_rule.update_by   is '更新者';
comment on column abr_lead_score_rule.update_time is '更新时间';
comment on column abr_lead_score_rule.remark      is '备注（参数说明）';

insert into abr_lead_score_rule (rule_code, rule_name, params, sort_order, status, create_by, create_time, remark)
select v.code, v.name, cast(v.params as jsonb), v.sort, '0', 'admin', now(), v.remark
from (values
  ('contact', '联系方式',   '{"phone": 15, "wechat": 5}', 1, 'phone 绑定或登记了手机号；wechat 微信登录'),
  ('moment',  '转化时刻',   '{"imbalance": 15, "requirement_unmet": 20, "materials_behind": 20, "parent_view": 15, "repeated_edits": 10, "cap": 40}', 2,
   '每类时刻的分值（每类只计一次），cap 为本项上限'),
  ('intake',  '入学时间',   '{"months_12": 20, "months_18": 10}', 3, '距入学 12 个月内 / 18 个月内（取最近一次测评或档案中的入学年份，按 9 月入学估算）'),
  ('tools',   '工具与测评', '{"assessment": 15, "runs_3": 15, "runs_1": 5, "days": 30}', 4, 'assessment 提交过完整测评；runs_3 / runs_1 近 days 天用过 3 次以上 / 1 次以上工具'),
  ('plan',    '选校单',     '{"items_3": 10, "items_1": 5}', 5, '选校单中有 3 个以上 / 1 个以上项目'),
  ('parent',  '家长参与',   '{"share": 10, "viewed": 10}', 6, 'share 开启了家长只读分享；viewed 家长查看过'),
  ('source',  '来源',       '{"referral": 15, "partner": 10, "lecture": 10, "wecom": 5, "dm": 5}', 7, '曾经从这些来源进入（每条线索取其中最高的一项）'),
  ('recency', '活跃度',     '{"days_7": 10, "stale_30": -10}', 8, '最近 7 天有触达加分；超过 30 天没有触达减分'),
  ('grade',   '分级线',     '{"a": 60, "b": 30}', 9, '总分 ≥ a 为 A，≥ b 为 B，其余为 C（草案，按试运行数据调整）')
) as v(code, name, params, sort, remark)
where not exists (select 1 from abr_lead_score_rule r where r.rule_code = v.code);

-- ----------------------------
-- 2、线索：规则等级、理由、顾问修改
-- ----------------------------
alter table abr_lead add column if not exists rule_grade      varchar(4)   default null;
alter table abr_lead add column if not exists score_reasons   jsonb        default null;
alter table abr_lead add column if not exists scored_time     timestamp    default null;
alter table abr_lead add column if not exists grade_override  char(1)      default 'N';
alter table abr_lead add column if not exists override_reason varchar(500) default '';
alter table abr_lead add column if not exists override_by     varchar(64)  default '';
alter table abr_lead add column if not exists override_time   timestamp    default null;
create index if not exists idx_abr_lead_grade on abr_lead (grade, last_touch_time desc) where merged_into is null;

comment on column abr_lead.grade           is '意向度 A / B / C（最终：顾问改过时以顾问为准）';
comment on column abr_lead.score           is '规则总分';
comment on column abr_lead.rule_grade      is '规则等级（按分级线）';
comment on column abr_lead.score_reasons   is '得分理由（jsonb 数组：[{"rule","label","points","detail"}]）';
comment on column abr_lead.scored_time     is '最近评分时间';
comment on column abr_lead.grade_override  is '是否顾问修改过等级（Y 时规则重算只更新分数与规则等级）';
comment on column abr_lead.override_reason is '顾问修改等级的原因';
comment on column abr_lead.override_by     is '修改人';
comment on column abr_lead.override_time   is '修改时间';

-- ----------------------------
-- 3、等级历史
-- ----------------------------
create table if not exists abr_lead_score_log (
  log_id        bigserial      not null,
  lead_id       bigint         not null,
  from_grade    varchar(4)     default null,
  to_grade      varchar(4)     default null,
  score         integer        default null,
  rule_grade    varchar(4)     default null,
  change_type   varchar(16)    not null,
  reason        varchar(500)   default '',
  operator      varchar(64)    default '',
  create_time   timestamp      default now(),
  primary key (log_id)
);
create index if not exists idx_abr_lead_score_log_lead on abr_lead_score_log (lead_id, log_id desc);

comment on table  abr_lead_score_log             is '线索等级历史（规则评分导致等级变化、顾问修改与撤销修改）';
comment on column abr_lead_score_log.log_id      is '记录ID';
comment on column abr_lead_score_log.lead_id     is '线索ID';
comment on column abr_lead_score_log.from_grade  is '原等级';
comment on column abr_lead_score_log.to_grade    is '新等级';
comment on column abr_lead_score_log.score       is '当时的规则总分';
comment on column abr_lead_score_log.rule_grade  is '当时的规则等级';
comment on column abr_lead_score_log.change_type is '类型（rule 规则评分 / manual 顾问修改 / reset 撤销修改、恢复规则等级）';
comment on column abr_lead_score_log.reason      is '原因（顾问修改必填）';
comment on column abr_lead_score_log.operator    is '操作人（规则评分为 lead_scorer）';
comment on column abr_lead_score_log.create_time is '时间';

-- ----------------------------
-- 4、触发规则：新线索、绑定手机号 → 评分；转化时刻 → 评分（M6-01 种子启用）；每天 06:00 重算
-- ----------------------------
update abr_growth_trigger
   set status = '0', remark = 'M5-02 已启用', update_by = 'admin', update_time = now()
 where agent_code = 'lead_scorer' and action = 'score' and event_type = 'plan_moment' and remark = 'M5-02 上线时启用';

insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select v.name, v.type, v.event_type, '{}'::jsonb, v.cron, null, 'lead_scorer', v.action, '{}'::jsonb, 0, 24, 3, '0', 'admin', now(), 'M5-02'
from (values
  ('新线索 → 线索评分',         'event',    'lead_captured',        null,          'score'),
  ('绑定手机号 → 线索评分',     'event',    'app_user_phone_bound', null,          'score'),
  ('每天 06:00 重算线索评分',   'schedule', null,                   '0 0 6 * * *', 'rescore_all')
) as v(name, type, event_type, cron, action)
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'lead_scorer' and t.action = v.action
                  and coalesce(t.event_type, '') = coalesce(v.event_type, ''));

-- ----------------------------
-- 5、字典与菜单
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '线索意向度', 'abr_lead_grade', '0', 'admin', now(), '线索 A / B / C'
where not exists (select 1 from sys_dict_type t where t.dict_type = 'abr_lead_grade');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_lead_grade', v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, 'A 高意向', 'A', 'danger',  '2 小时内必须有人联系（M5-03）'),
  (2, 'B 中意向', 'B', 'warning', ''),
  (3, 'C 低意向', 'C', 'info',    '')
) as v(dict_sort, dict_label, dict_value, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_lead_grade' and d.dict_value = v.dict_value);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '线索评分规则', p.menu_id, 17, 'leadScore', 'abroad/leadScore/index', '', '', 1, 0, 'C', '0', '0', 'abroad:leadScore:list', 'star', 'admin', now(), '线索评分规则菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:leadScore:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:leadScore:list', '规则修改', 1, 'abroad:leadScore:edit'),
  ('abroad:lead:list',      '修改意向度', 5, 'abroad:lead:grade'),
  ('abroad:lead:list',      '重新评分',   6, 'abroad:lead:score')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

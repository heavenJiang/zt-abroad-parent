-- ----------------------------------------------------------------------------
-- M6-04 例外队列与抽检（复用 AI 审核队列 abr_ai_review），不合格样本回流评测集
--
-- - abr_ai_review 增加 queue_type：ai 网关审核（原有）/ exception 例外（合规闸门拦截等需要人处理的）/ sample 抽检（自动发出的内容按比例事后抽查）；
--   例外与抽检可以没有 AI 调用（员工撰写、确定性 Agent），call_id 改为可空；同一业务对象同时只有一条未处理的例外或抽检。
-- - abr_content.sampled：执行模式为“全自动 + 抽检”的 Agent 生成内容时按比例抽中，过闸门后进抽检队列。
-- - abr_ai_eval_case：评测集（数据库中的样例，与 prompts/<agent>/<版本>/eval/*.json 一起参与评测命令）；
--   抽检不合格、确认拦截的 AI 内容自动生成样例（命中的词生成 notContains 断言），有模型输入时直接启用，否则待完善。
-- - 抽检合格 / 不合格上报信号，熔断指标增加“抽检不合格率”。
-- 依赖：update_20261014_ai.sql、update_20261019_growth_guard.sql、update_20261019_publish_content.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、审核队列：队列类型、原因、模型输入、评测样例
-- ----------------------------
alter table abr_ai_review alter column call_id drop not null;
alter table abr_ai_review add column if not exists queue_type   varchar(16) not null default 'ai';
alter table abr_ai_review add column if not exists reason_code  varchar(32) not null default '';
alter table abr_ai_review add column if not exists source_input jsonb       default null;
alter table abr_ai_review add column if not exists eval_case_id bigint      default null;

create index if not exists idx_abr_ai_review_queue on abr_ai_review (queue_type, status, create_time);
create unique index if not exists uk_abr_ai_review_open on abr_ai_review (queue_type, biz_type, biz_id)
  where queue_type <> 'ai' and status in ('pending', 'sampled');

comment on table  abr_ai_review              is 'AI 审核队列表（含获客例外队列与抽检，见 queue_type）';
comment on column abr_ai_review.call_id      is '调用ID（abr_ai_call_log；例外与抽检的内容不是 AI 生成时为空）';
comment on column abr_ai_review.content      is 'AI 原始输出（已还原占位符）；例外与抽检为业务对象快照（内容：标题、正文、平台、闸门结果）';
comment on column abr_ai_review.flags        is '输出过滤命中项（jsonb 数组：[{"type","label","word","path"}]）；例外为闸门拦截项';
comment on column abr_ai_review.queue_type   is '队列（字典 abr_review_queue：ai 网关审核 / exception 例外 / sample 抽检）';
comment on column abr_ai_review.reason_code  is '进入队列的原因（字典 abr_exception_reason）';
comment on column abr_ai_review.source_input is '产生该输出的模型输入（已去标识；生成评测样例用，可为空）';
comment on column abr_ai_review.eval_case_id is '回流生成的评测样例ID（abr_ai_eval_case）';

-- ----------------------------
-- 2、内容：是否被抽中
-- ----------------------------
alter table abr_content add column if not exists sampled char(1) default 'N';
comment on column abr_content.sampled is '是否被抽检（Agent 执行模式为全自动 + 抽检时按比例抽中；过闸门后进抽检队列）';

-- ----------------------------
-- 3、评测集
-- ----------------------------
create table if not exists abr_ai_eval_case (
  case_id      bigserial      not null,
  agent_code   varchar(64)    not null,
  case_name    varchar(200)   not null,
  source       varchar(16)    not null default 'manual',
  review_id    bigint         default null,
  spec         jsonb          not null default '{}'::jsonb,
  bad_output   jsonb          default null,
  status       char(1)        default '1',
  create_by    varchar(64)    default '',
  create_time  timestamp,
  update_by    varchar(64)    default '',
  update_time  timestamp,
  remark       varchar(500)   default null,
  primary key (case_id)
);
create index if not exists idx_abr_ai_eval_case_agent on abr_ai_eval_case (agent_code, status);
create unique index if not exists uk_abr_ai_eval_case_review on abr_ai_eval_case (review_id) where review_id is not null;

comment on table  abr_ai_eval_case             is '评测集（数据库样例，与提示词目录下的 eval/*.json 一起参与评测命令；抽检不合格的样本自动回流）';
comment on column abr_ai_eval_case.case_id     is '样例ID';
comment on column abr_ai_eval_case.agent_code  is 'Agent 编码';
comment on column abr_ai_eval_case.case_name   is '样例名称';
comment on column abr_ai_eval_case.source      is '来源（字典 abr_eval_case_source：manual 手工 / sample 抽检不合格 / exception 确认拦截）';
comment on column abr_ai_eval_case.review_id   is '来源的队列记录（abr_ai_review）';
comment on column abr_ai_eval_case.spec        is '样例定义（jsonb，与 eval/*.json 相同：input、knownNames、facts、expectStatus、expect 断言）';
comment on column abr_ai_eval_case.bad_output  is '回流时的不合格输出（对照用）';
comment on column abr_ai_eval_case.status      is '状态（字典 abr_eval_case_status：0 启用，参与评测 / 1 待完善，缺模型输入或断言）';
comment on column abr_ai_eval_case.create_by   is '创建者';
comment on column abr_ai_eval_case.create_time is '创建时间';
comment on column abr_ai_eval_case.update_by   is '更新者';
comment on column abr_ai_eval_case.update_time is '更新时间';
comment on column abr_ai_eval_case.remark      is '备注（不合格原因）';

-- ----------------------------
-- 4、熔断：抽检不合格率
-- ----------------------------
insert into abr_breaker_rule (rule_code, rule_name, metric, scope, agent_code, window_hours, threshold, min_sample, status, create_by, create_time, remark)
select 'agent_sample_fail', '单个 Agent 7 天抽检不合格率 ≥ 20%', 'sample_fail_rate', 'agent', null, 168, 0.20, 10, '0', 'admin', now(), '阈值为草案，按试运行数据调整'
where not exists (select 1 from abr_breaker_rule r where r.rule_code = 'agent_sample_fail');

-- ----------------------------
-- 5、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('审核队列',       'abr_review_queue',      'abr_ai_review 的队列类型'),
  ('例外原因',       'abr_exception_reason',  '进入例外队列或抽检的原因'),
  ('评测样例来源',   'abr_eval_case_source',  '评测集样例的来源'),
  ('评测样例状态',   'abr_eval_case_status',  '评测集样例是否参与评测')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '网关审核', 'ai',        'abr_review_queue', 'primary', 'AI 网关按审核策略写入'),
  (2, '例外',     'exception', 'abr_review_queue', 'danger',  '需要人处理'),
  (3, '抽检',     'sample',    'abr_review_queue', 'info',    '自动发出后事后抽查'),
  (1, '合规闸门拦截', 'gate_blocked',    'abr_exception_reason', 'danger',  'M6-03'),
  (2, '消息被拦截',   'message_blocked', 'abr_exception_reason', 'danger',  'M8 消息发出前'),
  (3, 'Agent 转人工', 'agent_handoff',   'abr_exception_reason', 'warning', '重写仍失败、要求真人等'),
  (4, '按比例抽检',   'auto_sample',     'abr_exception_reason', 'info',    '执行模式为全自动 + 抽检'),
  (1, '手工',       'manual',    'abr_eval_case_source', 'default', ''),
  (2, '抽检不合格', 'sample',    'abr_eval_case_source', 'danger',  ''),
  (3, '确认拦截',   'exception', 'abr_eval_case_source', 'warning', ''),
  (1, '启用',   '0', 'abr_eval_case_status', 'success', '参与评测命令'),
  (2, '待完善', '1', 'abr_eval_case_status', 'warning', '缺模型输入或断言'),
  (7, '抽检合格',   'sample_pass', 'abr_signal_type', 'success', 'M6-04 上报（不合格率分母）'),
  (8, '抽检不合格', 'sample_fail', 'abr_signal_type', 'danger',  'M6-04 上报'),
  (6, '抽检不合格率', 'sample_fail_rate', 'abr_breaker_metric', 'warning', '不合格 / （合格 + 不合格）')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 6、菜单：获客运营 → 例外队列；AI 管理 → 评测集 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '例外队列', p.menu_id, 13, 'exception', 'abroad/exception/index', '', '', 1, 0, 'C', '0', '0', 'abroad:exception:list', 'bug', 'admin', now(), '例外队列与抽检菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:exception:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '评测集', p.menu_id, 4, 'evalCase', 'abroad/evalCase/index', '', '', 1, 0, 'C', '0', '0', 'abroad:evalCase:list', 'list', 'admin', now(), '评测集菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'ai') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:evalCase:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:exception:list', '例外查询', 1, 'abroad:exception:query'),
  ('abroad:exception:list', '例外处理', 2, 'abroad:exception:handle'),
  ('abroad:exception:list', '抽检',     3, 'abroad:exception:sample'),
  ('abroad:evalCase:list',  '样例查询', 1, 'abroad:evalCase:query'),
  ('abroad:evalCase:list',  '样例新增', 2, 'abroad:evalCase:add'),
  ('abroad:evalCase:list',  '样例修改', 3, 'abroad:evalCase:edit'),
  ('abroad:evalCase:list',  '样例删除', 4, 'abroad:evalCase:remove')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

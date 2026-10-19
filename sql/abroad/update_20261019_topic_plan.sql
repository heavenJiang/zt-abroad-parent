-- ----------------------------------------------------------------------------
-- M7-02 topic_planner 选题与排期
--
-- 每个渠道账号未来 7 天、每天若干条选题（abroad.topic.*）：空位按生效剧本轮流分配，内容类型按剧本配比补齐；
-- 标题、切入点、文末工具、引用的项目由 topic_planner 经 AI 网关生成，逐条校验（工具、项目、快讯须引用项目、
-- 近期重复、合规词表与事实核对）；没填上的空位用剧本的备用选题。员工可随时插入、修改、取消选题。
-- 触发：每天 07:00、剧本切换（取消旧剧本未写稿的选题后重排）、日历节点。
-- 依赖：update_20261018_orchestrator.sql、update_20261019_publish_content.sql、update_20261019_compliance.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、选题排期
-- ----------------------------
create table if not exists abr_topic (
  topic_id       bigserial      not null,
  account_id     bigint         not null,
  plan_date      date           not null,
  slot_no        integer        not null default 1,
  content_kind   varchar(16)    not null,
  title          varchar(100)   not null,
  angle          varchar(500)   default '',
  hook_tool      varchar(32)    default null,
  playbook_code  varchar(64)    default null,
  program_ids    jsonb          not null default '[]'::jsonb,
  source         varchar(16)    not null default 'manual',
  status         varchar(16)    not null default 'planned',
  content_id     bigint         default null,
  ai_call_id     bigint         default null,
  create_by      varchar(64)    default '',
  create_time    timestamp,
  update_by      varchar(64)    default '',
  update_time    timestamp,
  remark         varchar(500)   default null,
  primary key (topic_id)
);
create unique index if not exists uk_abr_topic_slot on abr_topic (account_id, plan_date, slot_no) where status <> 'cancelled';
create index if not exists idx_abr_topic_date on abr_topic (plan_date, status);

comment on table  abr_topic               is '选题排期（topic_planner 生成或员工插入；content_writer 按选题写稿）';
comment on column abr_topic.topic_id      is '选题ID';
comment on column abr_topic.account_id    is '发布账号ID';
comment on column abr_topic.plan_date     is '计划发布日期';
comment on column abr_topic.slot_no       is '当天第几条（同一账号同一天内唯一，已取消的除外）';
comment on column abr_topic.content_kind  is '内容类型（字典 abr_content_kind）';
comment on column abr_topic.title         is '选题标题';
comment on column abr_topic.angle         is '切入点（写稿要点）';
comment on column abr_topic.hook_tool     is '文末工具（字典 abr_hook_tool）';
comment on column abr_topic.playbook_code is '所属剧本编码';
comment on column abr_topic.program_ids   is '引用的院校库项目ID（jsonb 数组；快讯必填）';
comment on column abr_topic.source        is '来源（字典 abr_topic_source：ai / backup 剧本备用选题 / manual 员工 / urgent 情报加急）';
comment on column abr_topic.status        is '状态（字典 abr_topic_status：planned 待写稿 / drafted 已写稿 / skipped 已跳过 / cancelled 已取消）';
comment on column abr_topic.content_id    is '写成的内容ID（abr_content）';
comment on column abr_topic.ai_call_id    is 'AI 调用ID（abr_ai_call_log）';
comment on column abr_topic.create_by     is '创建者（Agent 生成为 topic_planner）';
comment on column abr_topic.create_time   is '创建时间';
comment on column abr_topic.update_by     is '更新者';
comment on column abr_topic.update_time   is '更新时间';
comment on column abr_topic.remark        is '备注';

-- ----------------------------
-- 2、AI 网关 Agent（模型先用 mock，上线前在管理端改成真实模型）
-- ----------------------------
insert into abr_ai_agent (agent_code, agent_name, description, review_policy, sample_rate, primary_model, backup_model,
                          prompt_version, timeout_ms, max_output_tokens, temperature, status, create_by, create_time, remark)
values ('topic_planner', '选题', '按剧本、院校库截止与变化、日历节点为各账号排未来 7 天的选题（只写标题与切入点，内部使用）',
        'internal_only', 0, 'mock:default', 'mock:default', 'v1', 30000, 3000, 0.50, '0', 'admin', now(), '失败时用剧本备用选题')
on conflict (agent_code) do nothing;

-- ----------------------------
-- 3、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('选题来源', 'abr_topic_source', '选题排期的来源'),
  ('选题状态', 'abr_topic_status', '选题排期的状态')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, 'AI 排期',   'ai',        'abr_topic_source', 'primary', 'topic_planner'),
  (2, '备用选题',  'backup',    'abr_topic_source', 'info',    '剧本备用选题'),
  (3, '员工插入',  'manual',    'abr_topic_source', 'success', ''),
  (4, '情报加急',  'urgent',    'abr_topic_source', 'danger',  'M7-09'),
  (1, '待写稿',    'planned',   'abr_topic_status', 'primary', ''),
  (2, '已写稿',    'drafted',   'abr_topic_status', 'success', ''),
  (3, '已跳过',    'skipped',   'abr_topic_status', 'info',    ''),
  (4, '已取消',    'cancelled', 'abr_topic_status', 'info',    '')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 4、触发规则：M6-01 种子中 topic_planner 的三条（每天 07:00、剧本切换、日历节点）启用
-- ----------------------------
update abr_growth_trigger
   set status = '0', remark = 'M7-02 已启用', update_by = 'admin', update_time = now()
 where agent_code = 'topic_planner' and action in ('plan_topics', 'node_topics') and remark = 'M7-02 上线时启用';

-- ----------------------------
-- 5、菜单：获客运营 → 选题排期 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '选题排期', p.menu_id, 14, 'topic', 'abroad/topic/index', '', '', 1, 0, 'C', '0', '0', 'abroad:topic:list', 'date', 'admin', now(), '选题排期菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:topic:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:topic:list', '选题查询', 1, 'abroad:topic:query'),
  ('abroad:topic:list', '选题新增', 2, 'abroad:topic:add'),
  ('abroad:topic:list', '选题修改', 3, 'abroad:topic:edit'),
  ('abroad:topic:list', '选题删除', 4, 'abroad:topic:remove'),
  ('abroad:topic:list', '立即排期', 5, 'abroad:topic:plan')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

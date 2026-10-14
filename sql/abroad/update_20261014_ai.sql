-- ----------------------------------------------------------------------------
-- M2-04 / M2-05 AI 网关：Agent 配置、调用日志、审核队列 + 字典 + Agent 种子 + 菜单
-- 可重复执行（create if not exists / where not exists / on conflict）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、Agent 配置表（Agent 在代码中定义，管理端只能修改配置，不能新增、删除）
-- ----------------------------
create table if not exists abr_ai_agent (
  agent_id           bigserial      not null,
  agent_code         varchar(64)    not null,
  agent_name         varchar(100)   not null,
  description        varchar(500)   default '',
  review_policy      varchar(32)    not null,
  sample_rate        numeric(5,4)   default 0,
  primary_model      varchar(128)   not null,
  backup_model       varchar(128)   default '',
  prompt_version     varchar(32)    not null,
  timeout_ms         integer        default 30000,
  max_output_tokens  integer        default 2000,
  temperature        numeric(3,2)   default 0.2,
  status             char(1)        default '0',
  create_by          varchar(64)    default '',
  create_time        timestamp,
  update_by          varchar(64)    default '',
  update_time        timestamp,
  remark             varchar(500)   default null,
  primary key (agent_id),
  unique (agent_code)
);

comment on table  abr_ai_agent                   is 'AI Agent 配置表';
comment on column abr_ai_agent.agent_id          is 'Agent ID';
comment on column abr_ai_agent.agent_code        is 'Agent 编码（代码中引用，如 report_explainer）';
comment on column abr_ai_agent.agent_name        is 'Agent 名称';
comment on column abr_ai_agent.description       is '做什么';
comment on column abr_ai_agent.review_policy     is '审核策略（字典 abr_ai_review_policy）';
comment on column abr_ai_agent.sample_rate       is '抽检比例 0–1（审核策略为直接展示 + 抽检时生效）';
comment on column abr_ai_agent.primary_model     is '主模型（provider:model，provider 见 application.yml 的 abroad.ai.providers）';
comment on column abr_ai_agent.backup_model      is '备用模型（provider:model，空表示不切换）';
comment on column abr_ai_agent.prompt_version    is '生效提示词版本（目录 prompts/<agent_code>/<版本>/）';
comment on column abr_ai_agent.timeout_ms        is '单次模型调用超时（毫秒）';
comment on column abr_ai_agent.max_output_tokens is '单次输出 token 上限';
comment on column abr_ai_agent.temperature       is '采样温度 0–2';
comment on column abr_ai_agent.status            is '状态（0正常 1停用）';
comment on column abr_ai_agent.create_by         is '创建者';
comment on column abr_ai_agent.create_time       is '创建时间';
comment on column abr_ai_agent.update_by         is '更新者';
comment on column abr_ai_agent.update_time       is '更新时间';
comment on column abr_ai_agent.remark            is '备注（失败时的兜底方式）';

-- ----------------------------
-- 2、AI 调用日志表（每次调用一行，异常也记录；输入输出均为去标识化后的内容）
-- ----------------------------
create table if not exists abr_ai_call_log (
  call_id            bigserial      not null,
  agent_code         varchar(64)    not null,
  prompt_version     varchar(32)    default '',
  prompt_hash        varchar(64)    default '',
  model_used         varchar(128)   default '',
  fallback_used      char(1)        default 'N',
  attempts           integer        default 0,
  status             varchar(32)    not null,
  error_msg          varchar(2000)  default '',
  input_summary      varchar(2000)  default '',
  output             text           default null,
  prompt_tokens      integer        default 0,
  completion_tokens  integer        default 0,
  cost               numeric(12,6)  default 0,
  latency_ms         integer        default 0,
  biz_type           varchar(32)    default '',
  biz_id             varchar(64)    default '',
  review_id          bigint         default null,
  create_by          varchar(64)    default '',
  create_time        timestamp,
  primary key (call_id)
);
create index if not exists idx_abr_ai_call_log_time on abr_ai_call_log (create_time);
create index if not exists idx_abr_ai_call_log_agent_time on abr_ai_call_log (agent_code, create_time);

comment on table  abr_ai_call_log                   is 'AI 调用日志表';
comment on column abr_ai_call_log.call_id           is '调用ID';
comment on column abr_ai_call_log.agent_code        is 'Agent 编码';
comment on column abr_ai_call_log.prompt_version    is '提示词版本';
comment on column abr_ai_call_log.prompt_hash       is '渲染后提示词（system + user）的 SHA-256';
comment on column abr_ai_call_log.model_used        is '最终使用的模型（provider:model）';
comment on column abr_ai_call_log.fallback_used     is '是否切换到备用模型（Y是 N否）';
comment on column abr_ai_call_log.attempts          is '模型调用次数（含切换与格式重试）';
comment on column abr_ai_call_log.status            is '结果状态（字典 abr_ai_call_status）';
comment on column abr_ai_call_log.error_msg         is '错误、降级或过滤原因';
comment on column abr_ai_call_log.input_summary     is '输入摘要（去标识化，截断到 2000 字）';
comment on column abr_ai_call_log.output            is '模型输出（去标识化；格式不合法时为原文，截断）';
comment on column abr_ai_call_log.prompt_tokens     is '输入 token 合计';
comment on column abr_ai_call_log.completion_tokens is '输出 token 合计';
comment on column abr_ai_call_log.cost              is '费用（元，按 abroad.ai.providers 配置的单价计算）';
comment on column abr_ai_call_log.latency_ms        is '总耗时（毫秒）';
comment on column abr_ai_call_log.biz_type          is '业务类型（如 assessment；trial 为管理端试用，eval 为评测）';
comment on column abr_ai_call_log.biz_id            is '业务ID';
comment on column abr_ai_call_log.review_id         is '审核记录ID（进入审核队列或被抽检时）';
comment on column abr_ai_call_log.create_by         is '调用人';
comment on column abr_ai_call_log.create_time       is '调用时间';

-- ----------------------------
-- 3、AI 审核队列表（顾问确认后发出 / 抽检）
-- ----------------------------
create table if not exists abr_ai_review (
  review_id       bigserial      not null,
  call_id         bigint         not null,
  agent_code      varchar(64)    not null,
  prompt_version  varchar(32)    default '',
  biz_type        varchar(32)    default '',
  biz_id          varchar(64)    default '',
  content         jsonb          default null,
  edited_content  jsonb          default null,
  status          varchar(16)    not null,
  flags           jsonb          default null,
  reviewer        varchar(64)    default '',
  review_time     timestamp,
  reason          varchar(500)   default '',
  create_by       varchar(64)    default '',
  create_time     timestamp,
  update_by       varchar(64)    default '',
  update_time     timestamp,
  remark          varchar(500)   default null,
  primary key (review_id)
);
create index if not exists idx_abr_ai_review_status on abr_ai_review (status, create_time);
create index if not exists idx_abr_ai_review_biz on abr_ai_review (biz_type, biz_id);

comment on table  abr_ai_review                is 'AI 审核队列表';
comment on column abr_ai_review.review_id      is '审核ID';
comment on column abr_ai_review.call_id        is '调用ID（abr_ai_call_log）';
comment on column abr_ai_review.agent_code     is 'Agent 编码';
comment on column abr_ai_review.prompt_version is '提示词版本（编辑后按该版本的 Schema 校验）';
comment on column abr_ai_review.biz_type       is '业务类型';
comment on column abr_ai_review.biz_id         is '业务ID';
comment on column abr_ai_review.content        is 'AI 原始输出（已还原占位符）';
comment on column abr_ai_review.edited_content is '审核人修改后的内容';
comment on column abr_ai_review.status         is '审核状态（字典 abr_ai_review_status）';
comment on column abr_ai_review.flags          is '输出过滤命中项（jsonb 数组：[{"type","label","word","path"}]）';
comment on column abr_ai_review.reviewer       is '审核人';
comment on column abr_ai_review.review_time    is '审核时间';
comment on column abr_ai_review.reason         is '驳回或修改原因';
comment on column abr_ai_review.create_by      is '创建者（发起调用的人）';
comment on column abr_ai_review.create_time    is '创建时间';
comment on column abr_ai_review.update_by      is '更新者';
comment on column abr_ai_review.update_time    is '更新时间';
comment on column abr_ai_review.remark         is '备注';

-- ----------------------------
-- 4、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('AI 审核策略', 'abr_ai_review_policy', 'Agent 输出的审核方式'),
  ('AI 调用状态', 'abr_ai_call_status',   'AI 网关调用结果状态'),
  ('AI 审核状态', 'abr_ai_review_status', 'AI 审核队列记录状态')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now()
from (values
  (1, '直接发出',           'direct',             'abr_ai_review_policy', 'success'),
  (2, '直接展示 + 抽检',    'direct_sample',      'abr_ai_review_policy', 'primary'),
  (3, '顾问确认后发出',     'review_before_send', 'abr_ai_review_policy', 'warning'),
  (4, '使用者确认后写入',   'user_confirm',       'abr_ai_review_policy', 'info'),
  (5, '仅内部可见',         'internal_only',      'abr_ai_review_policy', 'default'),
  (6, '禁止 AI 生成',       'forbidden',          'abr_ai_review_policy', 'danger'),
  (1, '成功',     'success',        'abr_ai_call_status', 'success'),
  (2, '降级',     'degraded',       'abr_ai_call_status', 'warning'),
  (3, '已过滤',   'filtered',       'abr_ai_call_status', 'danger'),
  (4, '待审核',   'pending_review', 'abr_ai_call_status', 'primary'),
  (5, '错误',     'error',          'abr_ai_call_status', 'danger'),
  (1, '待审核',   'pending',  'abr_ai_review_status', 'warning'),
  (2, '抽检',     'sampled',  'abr_ai_review_status', 'info'),
  (3, '已通过',   'approved', 'abr_ai_review_status', 'success'),
  (4, '已驳回',   'rejected', 'abr_ai_review_status', 'danger'),
  (5, '已修改',   'edited',   'abr_ai_review_status', 'primary')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 5、Agent 种子（1a 四个业务 Agent + 网关自测 Agent；模型先用 mock，上线前在管理端改成真实模型）
-- ----------------------------
insert into abr_ai_agent (agent_code, agent_name, description, review_policy, sample_rate, primary_model, backup_model,
                          prompt_version, timeout_ms, max_output_tokens, temperature, status, create_by, create_time, remark)
values
  ('report_explainer',  '测评解读',     '把引擎结果写成报告里的定位、每所学校理由、短板说明',   'direct_sample', 0.1000, 'mock:default', 'mock:default', 'v1', 30000, 2000, 0.30, '0', 'admin', now(), '失败时用模板文字'),
  ('resume_parser',     '简历解析',     '从简历 / 成绩单抽取问卷字段',                           'user_confirm',  0,      'mock:default', 'mock:default', 'v1', 30000, 2000, 0.00, '0', 'admin', now(), '失败时提示手动填写'),
  ('lead_card',         '线索卡片',     '生成顾问看的线索卡片（摘要、顾虑、开场话题）',         'internal_only', 0,      'mock:default', 'mock:default', 'v1', 30000, 1500, 0.30, '0', 'admin', now(), '失败时只发基础信息'),
  ('catalog_extractor', '院校字段抽取', '从院校官网页面抽取项目字段 + 原文引句',                 'user_confirm',  0,      'mock:default', 'mock:default', 'v1', 60000, 3000, 0.00, '0', 'admin', now(), '失败时手动录入'),
  ('_selftest',         '网关自测',     '网关自测：mock 模型走完 8 步管道，供单元测试、评测命令与管理端试用', 'direct', 0, 'mock:default', 'mock:default', 'v1', 10000, 500, 0.00, '1', 'admin', now(), '业务调用停用；试用与评测可用')
on conflict (agent_code) do nothing;

-- ----------------------------
-- 6、菜单：AI 管理（目录）→ AI 调用日志 / AI 审核队列 / Agent 配置 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select 'AI 管理', 0, 30, 'ai', null, '', '', 1, 0, 'M', '0', '0', '', 'skill', 'admin', now(), '留学业务：AI 网关'
where not exists (select 1 from sys_menu where parent_id = 0 and path = 'ai');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (values
  ('AI 调用日志', 1, 'aiCallLog', 'abroad/aiCallLog/index', 'abroad:aiCallLog:list', 'log',       'AI 调用日志菜单'),
  ('AI 审核队列', 2, 'aiReview',  'abroad/aiReview/index',  'abroad:aiReview:list',  'checkbox',  'AI 审核队列菜单'),
  ('Agent 配置',  3, 'aiAgent',   'abroad/aiAgent/index',   'abroad:aiAgent:list',   'component', 'Agent 配置菜单')
) as v(menu_name, order_num, path, component, perms, icon, remark)
cross join (select menu_id from sys_menu where parent_id = 0 and path = 'ai') p
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:aiCallLog:list', '日志查询',   1, 'abroad:aiCallLog:query'),
  ('abroad:aiReview:list',  '审核查询',   1, 'abroad:aiReview:query'),
  ('abroad:aiReview:list',  '审核通过',   2, 'abroad:aiReview:approve'),
  ('abroad:aiReview:list',  '审核驳回',   3, 'abroad:aiReview:reject'),
  ('abroad:aiReview:list',  '修改后通过', 4, 'abroad:aiReview:edit'),
  ('abroad:aiAgent:list',   'Agent 查询', 1, 'abroad:aiAgent:query'),
  ('abroad:aiAgent:list',   'Agent 修改', 2, 'abroad:aiAgent:edit')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

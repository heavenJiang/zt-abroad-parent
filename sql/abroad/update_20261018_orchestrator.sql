-- ----------------------------------------------------------------------------
-- M6-01 营销编排器：剧本、营销日历、触发规则（事件订阅 / 定时）、编排任务队列（Agent 运行与对外触达）
--
-- 编排定时任务每分钟一轮：按北京时间的营销日历写入“剧本切换”“日历节点”事件 → 读取 abr_event 新事件匹配事件触发规则
-- → 到点的定时触发规则 → 生成任务（去重键保证不重复）→ 执行到期任务（交给对应 Agent 的处理器；Agent 未上线时任务等待至过期）。
-- 执行模式、频控、免打扰、预算、熔断、一键暂停在 M6-02。
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、剧本
-- ----------------------------
create table if not exists abr_playbook (
  playbook_id      bigserial      not null,
  playbook_code    varchar(64)    not null,
  playbook_name    varchar(100)   not null,
  degrees          jsonb          default '[]'::jsonb,
  countries        jsonb          default '[]'::jsonb,
  audience         varchar(300)   default '',
  content_focus    varchar(500)   default '',
  hook_tools       jsonb          default '[]'::jsonb,
  products         jsonb          default '[]'::jsonb,
  nurture_actions  varchar(500)   default '',
  content_mix      jsonb          default '{}'::jsonb,
  backup_topics    jsonb          default '[]'::jsonb,
  priority         integer        default 0,
  status           char(1)        default '0',
  create_by        varchar(64)    default '',
  create_time      timestamp,
  update_by        varchar(64)    default '',
  update_time      timestamp,
  remark           varchar(500)   default null,
  primary key (playbook_id)
);
create unique index if not exists uk_abr_playbook_code on abr_playbook (playbook_code);

comment on table  abr_playbook                 is '营销剧本（这段时间写什么、推什么工具、对谁触达；由营销日历决定何时生效）';
comment on column abr_playbook.playbook_id     is '剧本ID';
comment on column abr_playbook.playbook_code   is '剧本编码（Agent 与触发规则引用）';
comment on column abr_playbook.playbook_name   is '剧本名称';
comment on column abr_playbook.degrees         is '适用学段（字典 abr_degree 的值数组；空数组为全部学段）';
comment on column abr_playbook.countries       is '主推国家 / 地区（字典 abr_country 的值数组；空数组为全部）';
comment on column abr_playbook.audience        is '目标人群';
comment on column abr_playbook.content_focus   is '内容重点';
comment on column abr_playbook.hook_tools      is '主推钩子（字典 abr_hook_tool 的值数组，按主次排列）';
comment on column abr_playbook.products        is '主推产品编码数组（M5-06 产品与报价配置；不写价格）';
comment on column abr_playbook.nurture_actions is '培育动作';
comment on column abr_playbook.content_mix     is '内容配比（字典 abr_content_kind → 百分比，合计 100；空对象为沿用默认）';
comment on column abr_playbook.backup_topics   is '备用选题（topic_planner 失败时使用，字符串数组；不写具体数字）';
comment on column abr_playbook.priority        is '优先级（多个剧本同时生效时数值大的在前）';
comment on column abr_playbook.status          is '状态（0正常 1停用）';
comment on column abr_playbook.create_by       is '创建者';
comment on column abr_playbook.create_time     is '创建时间';
comment on column abr_playbook.update_by       is '更新者';
comment on column abr_playbook.update_time     is '更新时间';
comment on column abr_playbook.remark          is '备注';

-- ----------------------------
-- 2、营销日历（时间窗：剧本生效区间；节点：当天发出 calendar_node 事件）
-- ----------------------------
create table if not exists abr_calendar_entry (
  entry_id       bigserial      not null,
  entry_type     varchar(16)    not null,
  entry_name     varchar(100)   not null,
  playbook_id    bigint         default null,
  node_code      varchar(64)    default null,
  start_date     date           not null,
  end_date       date           not null,
  repeat_yearly  char(1)        default 'Y',
  status         char(1)        default '0',
  create_by      varchar(64)    default '',
  create_time    timestamp,
  update_by      varchar(64)    default '',
  update_time    timestamp,
  remark         varchar(500)   default null,
  primary key (entry_id)
);
create index if not exists idx_abr_calendar_entry_playbook on abr_calendar_entry (playbook_id);

comment on table  abr_calendar_entry               is '营销日历（时间窗决定剧本何时生效，按北京时间自动切换；节点当天发出事件触发 Agent）';
comment on column abr_calendar_entry.entry_id      is '日历条目ID';
comment on column abr_calendar_entry.entry_type    is '类型（字典 abr_calendar_entry_type：window 时间窗 / node 节点）';
comment on column abr_calendar_entry.entry_name    is '名称';
comment on column abr_calendar_entry.playbook_id   is '剧本ID（时间窗必填；节点可选，填写时事件带上剧本编码）';
comment on column abr_calendar_entry.node_code     is '节点编码（节点必填，如 kaoyan_score 考研初试出分；触发规则按它过滤）';
comment on column abr_calendar_entry.start_date    is '开始日期（节点为发生日）';
comment on column abr_calendar_entry.end_date      is '结束日期（含当天；节点与开始日期相同）';
comment on column abr_calendar_entry.repeat_yearly is '每年重复（Y 只比较月日，可跨年如 10-01 至 03-31；N 只在所填日期生效）';
comment on column abr_calendar_entry.status        is '状态（0正常 1停用）';
comment on column abr_calendar_entry.create_by     is '创建者';
comment on column abr_calendar_entry.create_time   is '创建时间';
comment on column abr_calendar_entry.update_by     is '更新者';
comment on column abr_calendar_entry.update_time   is '更新时间';
comment on column abr_calendar_entry.remark        is '备注';

-- ----------------------------
-- 3、触发规则（事件订阅 / 定时 → 让某个 Agent 执行某个动作）
-- ----------------------------
create table if not exists abr_growth_trigger (
  trigger_id      bigserial      not null,
  trigger_name    varchar(100)   not null,
  trigger_type    varchar(16)    not null,
  event_type      varchar(64)    default null,
  event_filter    jsonb          default '{}'::jsonb,
  cron_expression varchar(64)    default null,
  playbook_id     bigint         default null,
  agent_code      varchar(64)    not null,
  action          varchar(64)    not null,
  params          jsonb          default '{}'::jsonb,
  delay_minutes   integer        default 0,
  ttl_hours       integer        default 72,
  max_attempts    integer        default 3,
  last_fire_time  timestamp      default null,
  status          char(1)        default '0',
  create_by       varchar(64)    default '',
  create_time     timestamp,
  update_by       varchar(64)    default '',
  update_time     timestamp,
  remark          varchar(500)   default null,
  primary key (trigger_id)
);
create index if not exists idx_abr_growth_trigger_event on abr_growth_trigger (event_type) where status = '0';

comment on table  abr_growth_trigger                 is '编排触发规则（事件订阅或定时，生成交给某个 Agent 的任务）';
comment on column abr_growth_trigger.trigger_id      is '触发规则ID';
comment on column abr_growth_trigger.trigger_name    is '名称';
comment on column abr_growth_trigger.trigger_type    is '类型（字典 abr_trigger_type：event 事件 / schedule 定时）';
comment on column abr_growth_trigger.event_type      is '订阅的事件类型（abr_event.event_type，如 plan_moment、calendar_node、playbook_switched）';
comment on column abr_growth_trigger.event_filter    is '事件过滤（jsonb：键为事件内容的顶层字段，值为要求相等的值或可选值数组；空对象为不过滤）';
comment on column abr_growth_trigger.cron_expression is '定时表达式（6 位，秒 分 时 日 月 周，按北京时间；最小粒度 1 分钟）';
comment on column abr_growth_trigger.playbook_id     is '限定剧本（填写时只在该剧本生效期间触发）';
comment on column abr_growth_trigger.agent_code      is '执行的 Agent（字典 abr_growth_agent）';
comment on column abr_growth_trigger.action          is '动作（由 Agent 定义，如 plan_topics 排选题、score 评分）';
comment on column abr_growth_trigger.params          is '动作参数（jsonb，原样带入任务）';
comment on column abr_growth_trigger.delay_minutes   is '触发后延迟执行（分钟）';
comment on column abr_growth_trigger.ttl_hours       is '任务有效期（小时；超过仍未执行则过期不再执行，避免过时的触达）';
comment on column abr_growth_trigger.max_attempts    is '最多执行次数（含重试）';
comment on column abr_growth_trigger.last_fire_time  is '定时规则最近一次触发的计划时间';
comment on column abr_growth_trigger.status          is '状态（0正常 1停用）';
comment on column abr_growth_trigger.create_by       is '创建者';
comment on column abr_growth_trigger.create_time     is '创建时间';
comment on column abr_growth_trigger.update_by       is '更新者';
comment on column abr_growth_trigger.update_time     is '更新时间';
comment on column abr_growth_trigger.remark          is '备注';

-- ----------------------------
-- 4、编排任务队列（每条 = 让某个 Agent 对某个对象执行一次动作；对外触达任务带对象与渠道）
-- ----------------------------
create table if not exists abr_growth_task (
  task_id          bigserial      not null,
  trigger_id       bigint         default null,
  agent_code       varchar(64)    not null,
  action           varchar(64)    not null,
  source_event_id  bigint         default null,
  playbook_codes   jsonb          default '[]'::jsonb,
  target_type      varchar(32)    default null,
  target_id        bigint         default null,
  channel          varchar(32)    default null,
  dedup_key        varchar(128)   not null,
  payload          jsonb          default '{}'::jsonb,
  status           varchar(16)    not null,
  scheduled_time   timestamp      not null,
  expire_time      timestamp      default null,
  attempts         integer        default 0,
  max_attempts     integer        default 3,
  claim_token      varchar(64)    default null,
  started_time     timestamp      default null,
  finished_time    timestamp      default null,
  result           jsonb          default null,
  error_msg        varchar(2000)  default '',
  create_by        varchar(64)    default '',
  create_time      timestamp      default now(),
  update_by        varchar(64)    default '',
  update_time      timestamp      default now(),
  primary key (task_id)
);
create unique index if not exists uk_abr_growth_task_dedup on abr_growth_task (dedup_key);
create index if not exists idx_abr_growth_task_due on abr_growth_task (scheduled_time) where status = 'pending';
create index if not exists idx_abr_growth_task_agent on abr_growth_task (agent_code, create_time);
create index if not exists idx_abr_growth_task_target on abr_growth_task (target_type, target_id);

comment on table  abr_growth_task                 is '编排任务队列（Agent 运行与对外触达；由触发规则或手动生成，定时任务按计划时间执行）';
comment on column abr_growth_task.task_id         is '任务ID';
comment on column abr_growth_task.trigger_id      is '来源触发规则ID（手动生成为空）';
comment on column abr_growth_task.agent_code      is '执行的 Agent（字典 abr_growth_agent）';
comment on column abr_growth_task.action          is '动作';
comment on column abr_growth_task.source_event_id is '来源事件ID（abr_event；定时与手动为空）';
comment on column abr_growth_task.playbook_codes  is '生成时生效的剧本编码（按优先级排列）';
comment on column abr_growth_task.target_type     is '对象类型（visitor 访客 / app_user 学生账号 / lead 线索 / program 项目等；面向全体为空）';
comment on column abr_growth_task.target_id       is '对象ID';
comment on column abr_growth_task.channel         is '触达渠道（对外触达任务填写，M6-05 渠道适配器）';
comment on column abr_growth_task.dedup_key       is '去重键（触发规则 + 事件 / 计划时间；同一键只生成一条）';
comment on column abr_growth_task.payload         is '任务内容（事件内容、规则参数，jsonb）';
comment on column abr_growth_task.status          is '状态（字典 abr_growth_task_status）';
comment on column abr_growth_task.scheduled_time  is '计划执行时间（重试时顺延）';
comment on column abr_growth_task.expire_time     is '过期时间（之后不再执行）';
comment on column abr_growth_task.attempts        is '已执行次数';
comment on column abr_growth_task.max_attempts    is '最多执行次数';
comment on column abr_growth_task.claim_token     is '执行认领令牌（防止重复执行）';
comment on column abr_growth_task.started_time    is '最近一次开始执行时间';
comment on column abr_growth_task.finished_time   is '结束时间（完成、跳过、失败、过期、取消）';
comment on column abr_growth_task.result          is '执行结果（jsonb，由 Agent 处理器返回）';
comment on column abr_growth_task.error_msg       is '失败、跳过或过期原因';
comment on column abr_growth_task.create_by       is '创建者（规则生成为 system）';
comment on column abr_growth_task.create_time     is '创建时间';
comment on column abr_growth_task.update_by       is '更新者';
comment on column abr_growth_task.update_time     is '更新时间';

-- ----------------------------
-- 5、编排游标（事件读取位置、上次的生效剧本）
-- ----------------------------
create table if not exists abr_growth_cursor (
  cursor_name    varchar(32)    not null,
  last_event_id  bigint         default 0,
  state          jsonb          default '{}'::jsonb,
  update_time    timestamp      default now(),
  primary key (cursor_name)
);

comment on table  abr_growth_cursor               is '编排游标（编排定时任务的读取位置与状态，每轮加行锁防止并发）';
comment on column abr_growth_cursor.cursor_name   is '游标名（orchestrator）';
comment on column abr_growth_cursor.last_event_id is '已读取到的事件ID（abr_event）';
comment on column abr_growth_cursor.state         is '状态（jsonb：playbooks 上次生效的剧本编码）';
comment on column abr_growth_cursor.update_time   is '更新时间';

-- 游标从当前最大事件开始：上线前已有的历史事件不补触发
insert into abr_growth_cursor (cursor_name, last_event_id, state, update_time)
select 'orchestrator', coalesce((select max(event_id) from abr_event), 0), '{}'::jsonb, now()
where not exists (select 1 from abr_growth_cursor where cursor_name = 'orchestrator');

-- ----------------------------
-- 6、系统事件（无访客：剧本切换、日历节点等）去重：原唯一索引含 visitor_id，为空时不生效
-- ----------------------------
create unique index if not exists uk_abr_event_system_dedup on abr_event (event_type, dedup_key) where visitor_id is null and dedup_key is not null;

comment on column abr_event.event_type is '事件类型（plan_item_added / plan_item_removed / plan_material_updated / plan_share_created / plan_share_revoked / plan_moment；编排器：playbook_switched 剧本切换 / calendar_node 日历节点）';

-- ----------------------------
-- 7、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('获客 Agent',     'abr_growth_agent',         '营销编排器可调度的 Agent'),
  ('入口钩子',       'abr_hook_tool',            '剧本主推的入口工具与动作'),
  ('内容类型',       'abr_content_kind',         '剧本内容配比的类型'),
  ('日历条目类型',   'abr_calendar_entry_type',  '营销日历条目类型'),
  ('触发类型',       'abr_trigger_type',         '编排触发规则类型'),
  ('编排任务状态',   'abr_growth_task_status',   '编排任务队列状态')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1,  '选题 topic_planner',          'topic_planner',      'abr_growth_agent', 'default', 'M7-02'),
  (2,  '内容创作 content_writer',     'content_writer',     'abr_growth_agent', 'default', 'M7-03'),
  (3,  '图卡 content_designer',       'content_designer',   'abr_growth_agent', 'default', 'M7-04'),
  (4,  '合规审查 compliance_checker', 'compliance_checker', 'abr_growth_agent', 'default', 'M6-03'),
  (5,  '发布 publisher',              'publisher',          'abr_growth_agent', 'default', 'M7-05'),
  (6,  '互动 engagement_agent',       'engagement_agent',   'abr_growth_agent', 'default', 'M8-01'),
  (7,  '线索评分 lead_scorer',        'lead_scorer',        'abr_growth_agent', 'default', 'M5-02'),
  (8,  '线索卡片 lead_card',          'lead_card',          'abr_growth_agent', 'default', 'M5-05'),
  (9,  '培育 nurture_agent',          'nurture_agent',      'abr_growth_agent', 'default', 'M8-03'),
  (10, '邀约 booking_agent',          'booking_agent',      'abr_growth_agent', 'default', 'M8-04'),
  (11, '口碑 referral_agent',         'referral_agent',     'abr_growth_agent', 'default', 'M9-02'),
  (12, '增长分析 growth_analyst',     'growth_analyst',     'abr_growth_agent', 'default', 'M9-04'),
  (13, '情报监测 intel_watcher',      'intel_watcher',      'abr_growth_agent', 'default', 'M7-07'),
  (14, '需求洞察 demand_miner',       'demand_miner',       'abr_growth_agent', 'default', 'M7-09'),
  (1,  '30 秒快测',       'quick',          'abr_hook_tool', 'default', '已上线'),
  (2,  '本校认可度',      'recognition',    'abr_hook_tool', 'default', '已上线'),
  (3,  '雅思够不够',      'ielts',          'abr_hook_tool', 'default', '已上线'),
  (4,  '现在还能申',      'open',           'abr_hook_tool', 'default', '已上线'),
  (5,  '日本路径测评',    'japan_path',     'abr_hook_tool', 'default', '待开发'),
  (6,  '关注这个项目',    'follow',         'abr_hook_tool', 'default', 'M8-05'),
  (7,  '加入选校单',      'plan',           'abr_hook_tool', 'default', '已上线'),
  (8,  '预约咨询',        'booking',        'abr_hook_tool', 'default', 'M5-06'),
  (9,  '美本定位',        'us_ug_position', 'abr_hook_tool', 'default', '第 2 批'),
  (10, '美高择校初筛',    'us_hs_screen',   'abr_hook_tool', 'default', '第 3 批'),
  (11, '导师与项目检索',  'phd_search',     'abr_hook_tool', 'default', '第 4 批'),
  (1,  '案例 / offer / 学员故事', 'case',     'abr_content_kind', 'success', ''),
  (2,  '变化快讯与限时信息',      'flash',    'abr_content_kind', 'danger',  ''),
  (3,  '搜索型长尾问答',          'longtail', 'abr_content_kind', 'primary', ''),
  (4,  '活动与咨询邀约',          'invite',   'abr_content_kind', 'warning', ''),
  (1,  '时间窗', 'window', 'abr_calendar_entry_type', 'primary', '剧本生效区间'),
  (2,  '节点',   'node',   'abr_calendar_entry_type', 'warning', '当天发出 calendar_node 事件'),
  (1,  '事件',   'event',    'abr_trigger_type', 'primary', ''),
  (2,  '定时',   'schedule', 'abr_trigger_type', 'success', ''),
  (1,  '待执行', 'pending',   'abr_growth_task_status', 'primary', ''),
  (2,  '执行中', 'running',   'abr_growth_task_status', 'warning', ''),
  (3,  '已完成', 'done',      'abr_growth_task_status', 'success', ''),
  (4,  '已跳过', 'skipped',   'abr_growth_task_status', 'info',    ''),
  (5,  '失败',   'failed',    'abr_growth_task_status', 'danger',  ''),
  (6,  '已过期', 'expired',   'abr_growth_task_status', 'info',    ''),
  (7,  '已取消', 'cancelled', 'abr_growth_task_status', 'info',    '')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 8、剧本与营销日历草案（来自《获客系统设计》第九节；M0-10 创始人确认后在管理端修改）
-- ----------------------------
insert into abr_playbook (playbook_code, playbook_name, degrees, countries, audience, content_focus, hook_tools, products, nurture_actions,
                          content_mix, backup_topics, priority, status, create_by, create_time, remark)
select v.code, v.name, cast(v.degrees as jsonb), cast(v.countries as jsonb), v.audience, v.focus, cast(v.hooks as jsonb), '[]'::jsonb, v.nurture,
       cast(v.mix as jsonb), cast(v.topics as jsonb), v.priority, '0', 'admin', now(), '草案（M0-10 待创始人确认）'
from (values
  ('season_open', '申请季开启 / 保研结果', '["master"]', '["UK","HK","SG","AU","US"]', '大四定校党；保研失败或放弃保研',
   '新季要求变化、截止日历、“现在开始还来得及”', '["open","recognition","follow"]', '关注项目开放提醒',
   '{"case":35,"flash":30,"longtail":20,"invite":15}', '["新申请季各国家 / 地区硕士申请时间线","保研结果出来后转申留学的时间安排","本校认可度怎么查"]', 60),
  ('round_deadline', '港新英分轮截止', '["master"]', '["HK","SG","UK"]', '已开始准备申请的学生',
   '轮次截止倒计时、递交提醒', '["open","plan"]', '截止前 30 / 7 天提醒',
   '{"case":30,"flash":40,"longtail":15,"invite":15}', '["分轮次截止的项目怎么排递交顺序","递交前材料自查清单"]', 70),
  ('offer_season', '放榜季', '["master"]', '[]', '放榜季观望者',
   'offer 播报、同背景案例、学员故事', '["quick","plan"]', '观望线索再激活、邀约咨询',
   '{"case":45,"flash":20,"longtail":15,"invite":20}', '["同背景学生都去了哪里","收到 offer 后要做的几件事"]', 50),
  ('kaoyan_fail', '考研失利', '["master"]', '["UK","HK","AU"]', '考研初试出分后转留学的学生',
   '“考研失利还能申哪些”、紧急申请服务', '["open","booking"]', 'A 类当天邀约',
   '{"case":25,"flash":30,"longtail":20,"invite":25}', '["考研失利后还有哪些留学项目在开放","从考研转留学要补哪些材料"]', 90),
  ('junior_planning', '大三定校', '["master"]', '["UK","HK","SG","AU","US"]', '大三下到大四上的定校党',
   '选校清单、国家 / 地区对比、费用', '["quick","plan","booking"]', '定位咨询邀约',
   '{"case":35,"flash":20,"longtail":30,"invite":15}', '["大三开始准备留学的时间线","英港新澳硕士怎么选"]', 50),
  ('language_sprint', '语言冲刺、毕业', '["master"]', '[]', '准备语言考试的学生',
   '语言规划、背景提升', '["ielts","quick"]', '出分后重测提醒',
   '{"case":30,"flash":25,"longtail":30,"invite":15}', '["雅思出分后怎么看够不够","暑期背景提升怎么安排"]', 40),
  ('japan_us_master', '日本方向、美国硕士方向（全年）', '["master"]', '["JP","US"]', '日本方向、美国硕士方向的学生',
   '路径科普、标化规划', '["japan_path","quick"]', '按入学季倒排',
   '{"case":35,"flash":20,"longtail":30,"invite":15}', '["日本读研：预科与直申修士怎么选","美国硕士申请要准备哪些标化"]', 10),
  ('us_ug_summer', '美本：暑期规划与选校', '["bachelor"]', '["US"]', '高一到高三的美本家庭（营销只触达家长）',
   '活动与竞赛规划、选校清单、文书准备', '["us_ug_position"]', '家长：ED / EA 决策提醒',
   '{"case":35,"flash":20,"longtail":30,"invite":15}', '["美本暑期活动怎么规划","美本选校清单怎么排"]', 50),
  ('us_ug_ed', '美本 ED / EA 冲刺；美高看校与面试', '["bachelor","secondary"]', '["US"]', '美本、美高家庭（营销只触达家长）',
   'ED / EA 截止倒计时、美高面试准备', '["us_ug_position","us_hs_screen"]', '家长：截止前 30 / 7 天提醒',
   '{"case":30,"flash":35,"longtail":20,"invite":15}', '["ED 和 EA 有什么区别","美高面试常见问题"]', 60),
  ('us_ug_results', '美本放榜、美高 1 月截止与 3 月放榜', '["bachelor","secondary"]', '["US"]', '美本、美高观望家庭（营销只触达家长）',
   '录取播报、RD 补申、美高放榜解读', '["us_ug_position"]', '观望家庭再激活',
   '{"case":45,"flash":20,"longtail":15,"invite":20}', '["美本 RD 还来得及准备什么","美高放榜后怎么选校"]', 50),
  ('phd_outreach', '博士套磁季', '["phd"]', '["US","UK","HK","SG"]', '硕士在读、大三大四的博士申请人',
   '套磁方法、导师与项目、奖学金', '["phd_search","booking"]', '套磁节奏提醒',
   '{"case":30,"flash":20,"longtail":35,"invite":15}', '["博士套磁邮件怎么写","博士奖学金有哪些类型"]', 40)
) as v(code, name, degrees, countries, audience, focus, hooks, nurture, mix, topics, priority)
where not exists (select 1 from abr_playbook p where p.playbook_code = v.code);

insert into abr_calendar_entry (entry_type, entry_name, playbook_id, node_code, start_date, end_date, repeat_yearly, status, create_by, create_time, remark)
select 'window', v.name, p.playbook_id, null, cast(v.start_date as date), cast(v.end_date as date), 'Y', '0', 'admin', now(), '草案（M0-10 待创始人确认）'
from (values
  ('season_open',     '9–10 月 申请季开启 / 保研结果',          '2026-09-01', '2026-10-31'),
  ('round_deadline',  '10 月–次年 3 月 港新英分轮截止',         '2026-10-01', '2027-03-31'),
  ('offer_season',    '12 月–次年 4 月 放榜季',                 '2026-12-01', '2027-04-30'),
  ('kaoyan_fail',     '2–3 月 考研失利',                        '2026-02-01', '2026-03-31'),
  ('junior_planning', '3–6 月 大三定校',                        '2026-03-01', '2026-06-30'),
  ('language_sprint', '6–8 月 语言冲刺、毕业',                  '2026-06-01', '2026-08-31'),
  ('japan_us_master', '全年 日本方向、美国硕士方向',            '2026-01-01', '2026-12-31'),
  ('us_ug_summer',    '6–8 月 美本暑期规划与选校',              '2026-06-01', '2026-08-31'),
  ('us_ug_ed',        '9–11 月 美本 ED / EA 冲刺；美高看校',    '2026-09-01', '2026-11-30'),
  ('us_ug_results',   '12 月–次年 3 月 美本放榜、美高截止与放榜', '2026-12-01', '2027-03-31'),
  ('phd_outreach',    '6–12 月 博士套磁季',                     '2026-06-01', '2026-12-31')
) as v(code, name, start_date, end_date)
join abr_playbook p on p.playbook_code = v.code
where not exists (select 1 from abr_calendar_entry e where e.entry_type = 'window' and e.playbook_id = p.playbook_id);

-- ----------------------------
-- 9、触发规则草案：对应的 Agent 未上线，默认停用；各 Agent 的任务卡上线时启用
-- ----------------------------
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select v.name, v.type, v.event_type, cast(v.filter as jsonb), v.cron, null, v.agent, v.action, '{}'::jsonb,
       0, v.ttl, 3, '1', 'admin', now(), v.remark
from (values
  ('转化时刻 → 线索评分',   'event',    'plan_moment',       '{}', null,          'lead_scorer',   'score',            24,  'M5-02 上线时启用'),
  ('转化时刻 → 邀约推荐',   'event',    'plan_moment',       '{}', null,          'booking_agent', 'recommend',        48,  'M8-04 上线时启用'),
  ('每天 07:00 排未来 7 天选题', 'schedule', null,          '{}', '0 0 7 * * *', 'topic_planner', 'plan_topics',      12,  'M7-02 上线时启用'),
  ('剧本切换 → 重排选题',   'event',    'playbook_switched', '{}', null,          'topic_planner', 'plan_topics',      12,  'M7-02 上线时启用'),
  ('日历节点 → 节点选题',   'event',    'calendar_node',     '{}', null,          'topic_planner', 'node_topics',      12,  'M7-02 上线时启用'),
  ('每周一 08:00 增长周报', 'schedule', null,                '{}', '0 0 8 * * MON', 'growth_analyst', 'weekly_report', 24, 'M9-04 上线时启用')
) as v(name, type, event_type, filter, cron, agent, action, ttl, remark)
where not exists (select 1 from abr_growth_trigger t where t.agent_code = v.agent and t.action = v.action
                  and coalesce(t.event_type, '') = coalesce(v.event_type, ''));

-- ----------------------------
-- 10、菜单：获客运营 → 剧本、营销日历、触发规则、编排任务 + 按钮（编排器总览页为 ★ 页面，线框图确认后开发）
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '获客运营', 0, 40, 'growth', null, '', '', 1, 0, 'M', '0', '0', '', 'guide', 'admin', now(), '留学业务：营销编排器、内容、触达'
where not exists (select 1 from sys_menu where parent_id = 0 and path = 'growth');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (values
  ('营销剧本', 1, 'playbook',   'abroad/playbook/index',   'abroad:playbook:list',   'documentation', '营销剧本菜单'),
  ('营销日历', 2, 'calendar',   'abroad/calendar/index',   'abroad:calendar:list',   'date',          '营销日历菜单'),
  ('触发规则', 3, 'trigger',    'abroad/trigger/index',    'abroad:trigger:list',    'switch',        '编排触发规则菜单'),
  ('编排任务', 4, 'growthTask', 'abroad/growthTask/index', 'abroad:growthTask:list', 'job',           '编排任务队列菜单')
) as v(menu_name, order_num, path, component, perms, icon, remark)
cross join (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:playbook:list',   '剧本查询', 1, 'abroad:playbook:query'),
  ('abroad:playbook:list',   '剧本新增', 2, 'abroad:playbook:add'),
  ('abroad:playbook:list',   '剧本修改', 3, 'abroad:playbook:edit'),
  ('abroad:playbook:list',   '剧本删除', 4, 'abroad:playbook:remove'),
  ('abroad:calendar:list',   '日历查询', 1, 'abroad:calendar:query'),
  ('abroad:calendar:list',   '日历新增', 2, 'abroad:calendar:add'),
  ('abroad:calendar:list',   '日历修改', 3, 'abroad:calendar:edit'),
  ('abroad:calendar:list',   '日历删除', 4, 'abroad:calendar:remove'),
  ('abroad:trigger:list',    '规则查询', 1, 'abroad:trigger:query'),
  ('abroad:trigger:list',    '规则新增', 2, 'abroad:trigger:add'),
  ('abroad:trigger:list',    '规则修改', 3, 'abroad:trigger:edit'),
  ('abroad:trigger:list',    '规则删除', 4, 'abroad:trigger:remove'),
  ('abroad:trigger:list',    '手动触发', 5, 'abroad:trigger:fire'),
  ('abroad:growthTask:list', '任务查询', 1, 'abroad:growthTask:query'),
  ('abroad:growthTask:list', '任务重试', 2, 'abroad:growthTask:retry'),
  ('abroad:growthTask:list', '任务取消', 3, 'abroad:growthTask:cancel')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

-- ----------------------------
-- 11、定时任务：编排器每分钟一轮（默认启用；禁止并发）
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '营销编排器', 'DEFAULT', 'abrOrchestratorTask.dispatch()', '0 * * * * ?', '3', '1', '0', 'admin', now(),
       'M6-01：营销日历切换剧本、事件与定时触发、执行到期的编排任务'
where not exists (select 1 from sys_job where invoke_target = 'abrOrchestratorTask.dispatch()');

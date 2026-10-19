-- ----------------------------------------------------------------------------
-- M5-01 客户与线索：多来源收口、去重合并、首次 / 最近触达、时间线
--
-- - abr_lead：每个人一条（1a 不分客户与商机：签约后的学生域冻结）；按 学生账号 → unionid → 手机号哈希 去重；
--   合并时保留较早的一条，另一条记 merged_into，来源与触达记录移到保留的一条。
-- - abr_lead_touch：每次来源 / 触达（C 端注册、绑定手机号、转化时刻、分享、员工录入、表格导入、企业微信与私信接口）；
--   时间线 = 触达记录 + 该线索主访客的工具使用、测评、选校单等业务事件。
-- - 定时任务 abrLeadTask.ingest() 每分钟读取 abr_event（独立游标 lead_intake）：有学生账号的事件归到对应线索，
--   没有线索时新建（来源 h5，渠道取账号注册时的渠道码）；写 lead_captured 事件供评分（M5-02）与培育（M8-03）订阅。
-- 依赖：update_20261018_orchestrator.sql（abr_growth_cursor）、update_20261019_app_user.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、线索
-- ----------------------------
create table if not exists abr_lead (
  lead_id           bigserial      not null,
  display_name      varchar(64)    default '',
  phone             varchar(20)    default null,
  phone_hash        varchar(64)    default null,
  unionid           varchar(64)    default null,
  app_user_id       bigint         default null,
  visitor_id        bigint         default null,
  minor             char(1)        default 'N',
  stage             varchar(16)    default 'new',
  grade             varchar(4)     default null,
  score             integer        default null,
  owner_user_id     bigint         default null,
  first_source      varchar(16)    not null,
  first_channel     varchar(32)    default '',
  first_content     varchar(32)    default '',
  first_touch_time  timestamp      not null,
  last_source       varchar(16)    default '',
  last_channel      varchar(32)    default '',
  last_touch_time   timestamp      not null,
  touch_count       integer        default 0,
  merged_into       bigint         default null,
  del_flag          char(1)        default '0',
  create_by         varchar(64)    default '',
  create_time       timestamp,
  update_by         varchar(64)    default '',
  update_time       timestamp,
  remark            varchar(500)   default null,
  primary key (lead_id)
);
create unique index if not exists uk_abr_lead_app_user on abr_lead (app_user_id) where app_user_id is not null and merged_into is null and del_flag = '0';
create unique index if not exists uk_abr_lead_unionid on abr_lead (unionid) where unionid is not null and merged_into is null and del_flag = '0';
create unique index if not exists uk_abr_lead_phone on abr_lead (phone_hash) where phone_hash is not null and merged_into is null and del_flag = '0';
create index if not exists idx_abr_lead_last_touch on abr_lead (last_touch_time desc) where merged_into is null;

comment on table  abr_lead                  is '线索（每个人一条；按学生账号、unionid、手机号哈希去重合并）';
comment on column abr_lead.lead_id          is '线索ID';
comment on column abr_lead.display_name     is '称呼（员工录入或学生自填）';
comment on column abr_lead.phone            is '手机号（列表与详情脱敏；明文查看须单独权限并记操作日志）';
comment on column abr_lead.phone_hash       is '手机号 SHA-256（去重）';
comment on column abr_lead.unionid          is '微信 unionid';
comment on column abr_lead.app_user_id      is '学生账号ID（abr_app_user）';
comment on column abr_lead.visitor_id       is '主访客ID（工具使用、测评、选校单都在其下）';
comment on column abr_lead.minor            is '是否未成年（Y：营销只触达家长，M4-15 关联家长）';
comment on column abr_lead.stage            is '阶段（字典 abr_lead_stage）';
comment on column abr_lead.grade            is '意向度 A / B / C（lead_scorer，M5-02）';
comment on column abr_lead.score            is '意向分（M5-02）';
comment on column abr_lead.owner_user_id    is '负责顾问（sys_user，M5-03 分配）';
comment on column abr_lead.first_source     is '首次来源（字典 abr_lead_source）';
comment on column abr_lead.first_channel    is '首次渠道码';
comment on column abr_lead.first_content    is '首次内容编号';
comment on column abr_lead.first_touch_time is '首次触达时间';
comment on column abr_lead.last_source      is '最近来源';
comment on column abr_lead.last_channel     is '最近渠道码';
comment on column abr_lead.last_touch_time  is '最近触达时间';
comment on column abr_lead.touch_count      is '触达次数';
comment on column abr_lead.merged_into      is '合并进的线索ID（去重后保留较早的一条）';
comment on column abr_lead.del_flag         is '删除标志（0存在 2删除）';
comment on column abr_lead.create_by        is '创建者（自动收口为 system）';
comment on column abr_lead.create_time      is '创建时间';
comment on column abr_lead.update_by        is '更新者';
comment on column abr_lead.update_time      is '更新时间';
comment on column abr_lead.remark           is '备注';

-- ----------------------------
-- 2、来源与触达记录
-- ----------------------------
create table if not exists abr_lead_touch (
  touch_id       bigserial      not null,
  lead_id        bigint         not null,
  source         varchar(16)    not null,
  touch_type     varchar(32)    not null,
  channel_code   varchar(32)    default '',
  content_code   varchar(32)    default '',
  ref_type       varchar(32)    default '',
  ref_id         bigint         default null,
  event_id       bigint         default null,
  summary        varchar(500)   default '',
  occurred_time  timestamp      not null,
  create_by      varchar(64)    default '',
  create_time    timestamp      default now(),
  primary key (touch_id)
);
create index if not exists idx_abr_lead_touch_lead on abr_lead_touch (lead_id, occurred_time desc);
create unique index if not exists uk_abr_lead_touch_event on abr_lead_touch (event_id) where event_id is not null;

comment on table  abr_lead_touch               is '线索来源与触达记录（时间线；同一业务事件只记一次）';
comment on column abr_lead_touch.touch_id      is '记录ID';
comment on column abr_lead_touch.lead_id       is '线索ID';
comment on column abr_lead_touch.source        is '来源（字典 abr_lead_source）';
comment on column abr_lead_touch.touch_type    is '类型（字典 abr_lead_touch_type）';
comment on column abr_lead_touch.channel_code  is '渠道码';
comment on column abr_lead_touch.content_code  is '内容编号';
comment on column abr_lead_touch.ref_type      is '关联对象类型';
comment on column abr_lead_touch.ref_id        is '关联对象ID';
comment on column abr_lead_touch.event_id      is '来源业务事件（abr_event）';
comment on column abr_lead_touch.summary       is '摘要（不含手机号等个人信息）';
comment on column abr_lead_touch.occurred_time is '发生时间';
comment on column abr_lead_touch.create_by     is '创建者';
comment on column abr_lead_touch.create_time   is '创建时间';

-- ----------------------------
-- 3、游标：从第一条事件开始（上线前登录过的学生账号也收口为线索）
-- ----------------------------
insert into abr_growth_cursor (cursor_name, last_event_id, state, update_time)
select 'lead_intake', 0, '{}'::jsonb, now()
where not exists (select 1 from abr_growth_cursor where cursor_name = 'lead_intake');
comment on column abr_growth_cursor.cursor_name is '游标名（orchestrator 编排器 / lead_intake 线索收口）';

-- ----------------------------
-- 4、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('线索阶段',     'abr_lead_stage',      '线索从初联到签约 / 流失'),
  ('线索来源',     'abr_lead_source',     '线索的来源渠道类型'),
  ('触达记录类型', 'abr_lead_touch_type', '线索时间线中的记录类型')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '新线索',     'new',       'abr_lead_stage', 'primary', ''),
  (2, '已联系',     'contacted', 'abr_lead_stage', 'info',    ''),
  (3, '首访已约',   'booked',    'abr_lead_stage', 'warning', ''),
  (4, '方案已出',   'proposal',  'abr_lead_stage', 'warning', ''),
  (5, '报价中',     'quoting',   'abr_lead_stage', 'warning', ''),
  (6, '签约',       'won',       'abr_lead_stage', 'success', ''),
  (7, '流失',       'lost',      'abr_lead_stage', 'danger',  '须填原因（M5-04）'),
  (1, 'C 端（工具 / H5 / 小程序）', 'h5',       'abr_lead_source', 'primary', '学生登录或绑定手机号'),
  (2, '企业微信',   'wecom',     'abr_lead_source', 'success', 'M5-07'),
  (3, '平台私信',   'dm',        'abr_lead_source', 'warning', '接口接入或表格导入'),
  (4, '讲座报名',   'lecture',   'abr_lead_source', 'info',    ''),
  (5, '合作方',     'partner',   'abr_lead_source', 'info',    ''),
  (6, '转介绍',     'referral',  'abr_lead_source', 'success', ''),
  (7, '员工录入',   'manual',    'abr_lead_source', 'default', ''),
  (1, 'C 端注册',       'registered',  'abr_lead_touch_type', 'primary', ''),
  (2, '绑定手机号',     'phone_bound', 'abr_lead_touch_type', 'success', ''),
  (3, '转化时刻',       'plan_moment', 'abr_lead_touch_type', 'danger',  ''),
  (4, '分享选校单',     'plan_share',  'abr_lead_touch_type', 'info',    ''),
  (5, '员工录入',       'manual',      'abr_lead_touch_type', 'default', ''),
  (6, '表格导入',       'import',      'abr_lead_touch_type', 'default', ''),
  (7, '接口接入',       'api',         'abr_lead_touch_type', 'default', '企业微信、平台私信等'),
  (8, '线索合并',       'merged',      'abr_lead_touch_type', 'warning', '')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 5、菜单：获客运营 → 线索收件箱 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '线索收件箱', p.menu_id, 16, 'lead', 'abroad/lead/index', '', '', 1, 0, 'C', '0', '0', 'abroad:lead:list', 'people', 'admin', now(), '线索收件箱菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:lead:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:lead:list', '线索查询',   1, 'abroad:lead:query'),
  ('abroad:lead:list', '录入线索',   2, 'abroad:lead:add'),
  ('abroad:lead:list', '导入线索',   3, 'abroad:lead:import'),
  ('abroad:lead:list', '查看手机号', 4, 'abroad:lead:phone')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

-- ----------------------------
-- 6、定时任务：每分钟收口业务事件
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '线索收口', 'DEFAULT', 'abrLeadTask.ingest()', '30 * * * * ?', '3', '1', '0', 'admin', now(),
       'M5-01：读取有学生账号的业务事件，归到对应线索（没有时新建）、更新首次 / 最近触达与时间线'
where not exists (select 1 from sys_job where invoke_target = 'abrLeadTask.ingest()');

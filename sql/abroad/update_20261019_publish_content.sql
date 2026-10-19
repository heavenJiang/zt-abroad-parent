-- ----------------------------------------------------------------------------
-- M7-01 账号矩阵、内容与发布记录
--
-- - abr_channel_account：各平台账号、负责人、人设、授权状态、发布方式（接口直发 / 一键发布包）、每天发布上限、账号级暂停
-- - abr_content：每条内容一个内容编号（链接参数 ct，归因用）与私信口令；稿件、话题、图卡、主推工具链接、引用依据、
--   合规闸门结果、AI 生成标识；改动正文后须重新过闸门
-- - abr_publish_record：每次发布（计划时间、发布方式、发布后回填链接、效果数据由 M7-06 回填）；发布后写 content_published 事件
-- - 熔断规则新增 account 范围：按渠道账号统计计数类信号，达到阈值暂停该账号
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、渠道账号
-- ----------------------------
create table if not exists abr_channel_account (
  account_id         bigserial      not null,
  account_code       varchar(32)    not null,
  platform           varchar(32)    not null,
  account_name       varchar(100)   not null,
  owner_user_id      bigint         default null,
  owner_name         varchar(64)    default '',
  persona            varchar(16)    default 'studio',
  persona_desc       varchar(500)   default '',
  auth_status        varchar(16)    default 'none',
  publish_mode       varchar(16)    default 'package',
  daily_publish_cap  integer        default 2,
  paused             char(1)        default 'N',
  pause_reason       varchar(500)   default '',
  paused_by          varchar(64)    default '',
  paused_time        timestamp      default null,
  resume_time        timestamp      default null,
  status             char(1)        default '0',
  create_by          varchar(64)    default '',
  create_time        timestamp,
  update_by          varchar(64)    default '',
  update_time        timestamp,
  remark             varchar(500)   default null,
  primary key (account_id)
);
create unique index if not exists uk_abr_channel_account_code on abr_channel_account (account_code);

comment on table  abr_channel_account                   is '渠道账号（账号矩阵：各平台账号、负责人、人设、授权、发布方式与上限；密钥不存这里，见 M6-05）';
comment on column abr_channel_account.account_id        is '账号ID';
comment on column abr_channel_account.account_code      is '账号编码（作为链接渠道码 ch 与熔断信号的 account_code；字母、数字、_、-，最多 32 位）';
comment on column abr_channel_account.platform          is '平台（字典 abr_platform）';
comment on column abr_channel_account.account_name      is '账号名称';
comment on column abr_channel_account.owner_user_id     is '负责人（sys_user.user_id；一键发布包推送给他）';
comment on column abr_channel_account.owner_name        is '负责人姓名（保存时带出）';
comment on column abr_channel_account.persona           is '人设（字典 abr_persona：studio 工作室号 / consultant 顾问个人号）';
comment on column abr_channel_account.persona_desc      is '人设说明（语气、擅长方向，供 content_writer 参考）';
comment on column abr_channel_account.auth_status       is '接口授权状态（字典 abr_account_auth：none 未授权 / authorized 已授权 / expired 已过期 / unsupported 平台无接口）';
comment on column abr_channel_account.publish_mode      is '发布方式（字典 abr_publish_mode：api 接口直发 / package 一键发布包）';
comment on column abr_channel_account.daily_publish_cap is '每天发布上限（按平台规则配置；北京时间自然日，计划与已发布都计入）';
comment on column abr_channel_account.paused            is '暂停（Y：不能安排新的发布；手动或熔断）';
comment on column abr_channel_account.pause_reason      is '暂停原因';
comment on column abr_channel_account.paused_by         is '暂停人（熔断为 breaker）';
comment on column abr_channel_account.paused_time       is '暂停时间';
comment on column abr_channel_account.resume_time       is '最近一次恢复时间（熔断只统计此后的信号）';
comment on column abr_channel_account.status            is '状态（0正常 1停用）';
comment on column abr_channel_account.create_by         is '创建者';
comment on column abr_channel_account.create_time       is '创建时间';
comment on column abr_channel_account.update_by         is '更新者';
comment on column abr_channel_account.update_time       is '更新时间';
comment on column abr_channel_account.remark            is '备注';

-- ----------------------------
-- 2、内容
-- ----------------------------
create table if not exists abr_content (
  content_id       bigserial      not null,
  content_code     varchar(32)    not null,
  codeword         varchar(16)    default null,
  title            varchar(100)   not null,
  content_kind     varchar(16)    not null,
  account_id       bigint         not null,
  topic            varchar(300)   default '',
  topic_ref        varchar(64)    default null,
  playbook_code    varchar(64)    default null,
  degree           varchar(16)    default null,
  countries        jsonb          default '[]'::jsonb,
  body             text           default '',
  hashtags         jsonb          default '[]'::jsonb,
  cards            jsonb          default '[]'::jsonb,
  hook_tool        varchar(32)    default null,
  tool_link        varchar(500)   default '',
  citations        jsonb          default '[]'::jsonb,
  ai_generated     char(1)        default 'N',
  agent_code       varchar(64)    default null,
  ai_call_id       bigint         default null,
  requires_confirm char(1)        default 'N',
  gate_status      varchar(16)    default 'pending',
  gate_result      jsonb          default null,
  gate_by          varchar(64)    default '',
  gate_time        timestamp      default null,
  confirm_by       varchar(64)    default '',
  confirm_time     timestamp      default null,
  status           varchar(16)    default 'draft',
  create_by        varchar(64)    default '',
  create_time      timestamp,
  update_by        varchar(64)    default '',
  update_time      timestamp,
  remark           varchar(500)   default null,
  primary key (content_id)
);
create unique index if not exists uk_abr_content_code on abr_content (content_code);
create unique index if not exists uk_abr_content_codeword on abr_content (codeword) where codeword is not null and status <> 'archived';
create index if not exists idx_abr_content_account on abr_content (account_id, status);

comment on table  abr_content                  is '内容（每条对应一个账号的一篇稿件；内容编号用于链接归因，私信口令映射到内容编号）';
comment on column abr_content.content_id       is '内容ID';
comment on column abr_content.content_code     is '内容编号（链接参数 ct；系统生成）';
comment on column abr_content.codeword         is '私信口令（用户私信发送即可识别到这条内容；默认 4 位数字，可改为 2–16 位文字；未归档的内容中唯一）';
comment on column abr_content.title            is '标题';
comment on column abr_content.content_kind     is '内容类型（字典 abr_content_kind）';
comment on column abr_content.account_id       is '发布账号ID';
comment on column abr_content.topic            is '选题';
comment on column abr_content.topic_ref        is '选题引用（M7-02 选题排期）';
comment on column abr_content.playbook_code    is '所属剧本编码';
comment on column abr_content.degree           is '学段（字典 abr_degree）';
comment on column abr_content.countries        is '涉及国家 / 地区（字典 abr_country 的值数组）';
comment on column abr_content.body             is '正文（纯文本）';
comment on column abr_content.hashtags         is '话题标签（字符串数组）';
comment on column abr_content.cards            is '图卡（M7-04 确定性渲染：模板、数据、图片地址；jsonb 数组）';
comment on column abr_content.hook_tool        is '文末接的工具（字典 abr_hook_tool）';
comment on column abr_content.tool_link        is '工具链接（C 端地址 + ch=账号编码 + ct=内容编号，保存时生成）';
comment on column abr_content.citations        is '引用依据（数字与事实的来源：院校库记录、政策、案例；jsonb 数组，事实闸据此核对）';
comment on column abr_content.ai_generated     is '是否 AI 生成（Y 须带 AI 生成标识）';
comment on column abr_content.agent_code       is '生成的 Agent（员工撰写为空）';
comment on column abr_content.ai_call_id       is 'AI 调用ID（abr_ai_call_log，追溯提示词版本与模型）';
comment on column abr_content.requires_confirm is '是否须人工确认后发布（Agent 执行模式为人工确认时为 Y）';
comment on column abr_content.gate_status      is '合规闸门结果（字典 abr_gate_status：pending 未检查 / passed 通过 / manual_passed 人工审核通过 / blocked 拦截）';
comment on column abr_content.gate_result      is '闸门明细（命中的词、事实核对结果、修改建议；jsonb）';
comment on column abr_content.gate_by          is '闸门检查人（compliance_checker 或审核员工）';
comment on column abr_content.gate_time        is '闸门检查时间';
comment on column abr_content.confirm_by       is '人工确认人';
comment on column abr_content.confirm_time     is '人工确认时间';
comment on column abr_content.status           is '状态（字典 abr_content_status：draft 草稿 / pending_confirm 待人工确认 / ready 可发布 / published 已发布 / archived 已归档）';
comment on column abr_content.create_by        is '创建者（Agent 生成为 Agent 编码）';
comment on column abr_content.create_time      is '创建时间';
comment on column abr_content.update_by        is '更新者';
comment on column abr_content.update_time      is '更新时间';
comment on column abr_content.remark           is '备注';

-- ----------------------------
-- 3、发布记录
-- ----------------------------
create table if not exists abr_publish_record (
  record_id        bigserial      not null,
  content_id       bigint         not null,
  account_id       bigint         not null,
  publish_mode     varchar(16)    not null,
  status           varchar(16)    not null,
  planned_time     timestamp      not null,
  published_time   timestamp      default null,
  post_url         varchar(500)   default '',
  platform_post_id varchar(128)   default '',
  error_msg        varchar(500)   default '',
  metrics          jsonb          default null,
  metrics_time     timestamp      default null,
  create_by        varchar(64)    default '',
  create_time      timestamp,
  update_by        varchar(64)    default '',
  update_time      timestamp,
  primary key (record_id)
);
create index if not exists idx_abr_publish_record_account_time on abr_publish_record (account_id, planned_time);
create index if not exists idx_abr_publish_record_content on abr_publish_record (content_id);

comment on table  abr_publish_record                  is '发布记录（每次安排的发布；接口直发由 publisher 执行，一键发布包由账号负责人发布后回填链接）';
comment on column abr_publish_record.record_id        is '记录ID';
comment on column abr_publish_record.content_id       is '内容ID';
comment on column abr_publish_record.account_id       is '账号ID';
comment on column abr_publish_record.publish_mode     is '发布方式（字典 abr_publish_mode）';
comment on column abr_publish_record.status           is '状态（字典 abr_publish_status：scheduled 待发布 / published 已发布 / failed 失败 / withdrawn 已撤回）';
comment on column abr_publish_record.planned_time     is '计划发布时间';
comment on column abr_publish_record.published_time   is '实际发布时间';
comment on column abr_publish_record.post_url         is '发布后的链接（回填）';
comment on column abr_publish_record.platform_post_id is '平台内容ID';
comment on column abr_publish_record.error_msg        is '失败或撤回原因';
comment on column abr_publish_record.metrics          is '效果数据（阅读、点赞、收藏、私信等；M7-06 回填）';
comment on column abr_publish_record.metrics_time     is '效果数据更新时间';
comment on column abr_publish_record.create_by        is '创建者';
comment on column abr_publish_record.create_time      is '创建时间';
comment on column abr_publish_record.update_by        is '更新者';
comment on column abr_publish_record.update_time      is '更新时间';

-- ----------------------------
-- 4、熔断：账号范围
-- ----------------------------
alter table abr_breaker_trip add column if not exists account_code varchar(32) default null;
comment on column abr_breaker_trip.account_code is '暂停的渠道账号（账号范围）';
comment on column abr_breaker_trip.scope        is '范围（agent / account / global）';
comment on column abr_breaker_rule.scope        is '范围（agent 逐个 Agent 统计并暂停该 Agent / account 逐个渠道账号统计并暂停该账号（只支持计数类指标）/ global 合计统计并全局暂停）';

insert into abr_breaker_rule (rule_code, rule_name, metric, scope, agent_code, window_hours, threshold, min_sample, status, create_by, create_time, remark)
select 'account_violation', '单个账号 24 小时内平台违规提示 ≥ 1', 'platform_violation', 'account', null, 24, 1, 0, '0', 'admin', now(), '阈值为草案；与全局规则 global_violation 同时生效时以全局暂停为准'
where not exists (select 1 from abr_breaker_rule r where r.rule_code = 'account_violation');

-- ----------------------------
-- 5、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('内容平台',     'abr_platform',        '渠道账号所在平台'),
  ('账号人设',     'abr_persona',         '工作室号 / 顾问个人号'),
  ('账号授权状态', 'abr_account_auth',    '平台接口授权状态'),
  ('发布方式',     'abr_publish_mode',    '接口直发 / 一键发布包'),
  ('内容状态',     'abr_content_status',  '内容生命周期'),
  ('闸门结果',     'abr_gate_status',     '合规闸门检查结果'),
  ('发布状态',     'abr_publish_status',  '发布记录状态')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '微信公众号 / 服务号', 'wechat_oa',   'abr_platform', 'success', ''),
  (2, '小红书',             'xiaohongshu', 'abr_platform', 'danger',  ''),
  (3, '抖音',               'douyin',      'abr_platform', 'default', ''),
  (4, '视频号',             'channels',    'abr_platform', 'warning', ''),
  (5, '知乎',               'zhihu',       'abr_platform', 'primary', ''),
  (6, '企业微信',           'wecom',       'abr_platform', 'success', '社群消息'),
  (7, '自有站点',           'website',     'abr_platform', 'info',    '公开项目页'),
  (1, '工作室号',   'studio',     'abr_persona', 'primary', ''),
  (2, '顾问个人号', 'consultant', 'abr_persona', 'success', ''),
  (1, '未授权',     'none',        'abr_account_auth', 'info',    ''),
  (2, '已授权',     'authorized',  'abr_account_auth', 'success', ''),
  (3, '已过期',     'expired',     'abr_account_auth', 'danger',  ''),
  (4, '平台无接口', 'unsupported', 'abr_account_auth', 'default', '只能一键发布包'),
  (1, '接口直发',   'api',     'abr_publish_mode', 'success', ''),
  (2, '一键发布包', 'package', 'abr_publish_mode', 'warning', '推送给账号负责人，发布后回填链接'),
  (1, '草稿',       'draft',           'abr_content_status', 'info',    ''),
  (2, '待人工确认', 'pending_confirm', 'abr_content_status', 'warning', ''),
  (3, '可发布',     'ready',           'abr_content_status', 'primary', ''),
  (4, '已发布',     'published',       'abr_content_status', 'success', ''),
  (5, '已归档',     'archived',        'abr_content_status', 'default', ''),
  (1, '未检查',       'pending',       'abr_gate_status', 'info',    ''),
  (2, '通过',         'passed',        'abr_gate_status', 'success', 'compliance_checker（M6-03）'),
  (3, '人工审核通过', 'manual_passed', 'abr_gate_status', 'primary', 'M6-03 上线前由员工审核'),
  (4, '拦截',         'blocked',       'abr_gate_status', 'danger',  ''),
  (1, '待发布', 'scheduled', 'abr_publish_status', 'primary', ''),
  (2, '已发布', 'published', 'abr_publish_status', 'success', ''),
  (3, '失败',   'failed',    'abr_publish_status', 'danger',  ''),
  (4, '已撤回', 'withdrawn', 'abr_publish_status', 'info',    '')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 6、菜单：获客运营 → 渠道账号、内容库、发布记录 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (values
  ('渠道账号', 8,  'channelAccount', 'abroad/channelAccount/index', 'abroad:account:list', 'peoples', '渠道账号（账号矩阵）菜单'),
  ('内容库',   9,  'content',        'abroad/content/index',        'abroad:content:list', 'edit',    '内容库菜单'),
  ('发布记录', 10, 'publishRecord',  'abroad/publishRecord/index',  'abroad:publish:list', 'upload',  '发布记录菜单')
) as v(menu_name, order_num, path, component, perms, icon, remark)
cross join (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:account:list', '账号查询',     1, 'abroad:account:query'),
  ('abroad:account:list', '账号新增',     2, 'abroad:account:add'),
  ('abroad:account:list', '账号修改',     3, 'abroad:account:edit'),
  ('abroad:account:list', '账号删除',     4, 'abroad:account:remove'),
  ('abroad:account:list', '账号暂停恢复', 5, 'abroad:account:pause'),
  ('abroad:content:list', '内容查询',     1, 'abroad:content:query'),
  ('abroad:content:list', '内容新增',     2, 'abroad:content:add'),
  ('abroad:content:list', '内容修改',     3, 'abroad:content:edit'),
  ('abroad:content:list', '内容删除归档', 4, 'abroad:content:remove'),
  ('abroad:content:list', '闸门人工审核', 5, 'abroad:content:gate'),
  ('abroad:content:list', '人工确认',     6, 'abroad:content:confirm'),
  ('abroad:publish:list', '发布查询',     1, 'abroad:publish:query'),
  ('abroad:publish:list', '安排发布',     2, 'abroad:publish:add'),
  ('abroad:publish:list', '回填与撤回',   3, 'abroad:publish:edit')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

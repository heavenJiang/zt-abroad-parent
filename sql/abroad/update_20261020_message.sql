-- ----------------------------------------------------------------------------
-- M6-05 渠道适配器：消息模板、发送记录、统一发送接口（服务号模板消息、小程序订阅消息、企业微信应用消息、短信）
--
-- - abr_message_template：业务消息模板（业务编码 × 渠道）；平台模板 ID、字段映射、文字都是配置，不写在代码里。
--   种子模板为停用状态：服务号 / 小程序模板须在公众平台选用后填入模板 ID 与字段，短信须报备后填入模板编码，再启用。
-- - abr_message_send：每次发送（含被拦下、渠道未开通）的记录；只存脱敏后的接收方与渲染后的文字（不含手机号）。
-- - 凭据（AppID / Secret / 企业 ID / 应用 Secret / 短信密钥）只从环境变量注入，见 deploy/README.md；未配置的渠道发送结果为 off。
-- 依赖：update_20261019_growth_guard.sql（触达记录与字典）、update_20261019_z_roles.sql（预置角色）
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、消息模板
-- ----------------------------
create table if not exists abr_message_template (
  template_id          bigserial      not null,
  template_code        varchar(64)    not null,
  channel              varchar(32)    not null,
  template_name        varchar(100)   not null,
  category             varchar(16)    not null default 'service',
  platform_template_id varchar(128)   default '',
  field_map            jsonb          not null default '{}'::jsonb,
  content              varchar(1000)  default '',
  link                 varchar(500)   default '',
  status               char(1)        default '1',
  create_by            varchar(64)    default '',
  create_time          timestamp      default now(),
  update_by            varchar(64)    default '',
  update_time          timestamp      default null,
  remark               varchar(500)   default null,
  primary key (template_id)
);
create unique index if not exists uk_abr_message_template on abr_message_template (template_code, channel);

comment on table  abr_message_template                      is '消息模板（业务编码 × 渠道；平台模板 ID 与字段映射为配置）';
comment on column abr_message_template.template_id          is '模板ID';
comment on column abr_message_template.template_code        is '业务编码（如 booking_confirmed；发送方按业务编码发，渠道按学生可达的方式选）';
comment on column abr_message_template.channel              is '渠道（字典 abr_message_channel：service_account 服务号 / mp_subscribe 小程序订阅消息 / wecom 企业微信应用消息 / sms 短信）';
comment on column abr_message_template.template_name        is '名称';
comment on column abr_message_template.category             is '类别（字典 abr_touch_category：marketing 营销 / service 服务提醒；营销类须过合规闸门并受退订、频控约束）';
comment on column abr_message_template.platform_template_id is '平台模板 ID（服务号、小程序为模板 ID，短信为报备的模板编码；企业微信不需要）';
comment on column abr_message_template.field_map            is '字段映射（jsonb：平台字段名 → 文字，文字中 {变量} 由发送方提供；服务号、小程序、短信用）';
comment on column abr_message_template.content              is '消息文字（企业微信用；也作为发送记录与合规检查的文字，{变量} 同上）';
comment on column abr_message_template.link                 is '点击跳转（服务号为网页地址、小程序为页面路径，{变量} 同上；可空）';
comment on column abr_message_template.status               is '状态（0 启用 1 停用；种子模板填好平台模板 ID 后再启用）';
comment on column abr_message_template.create_by            is '创建者';
comment on column abr_message_template.create_time          is '创建时间';
comment on column abr_message_template.update_by            is '更新者';
comment on column abr_message_template.update_time          is '更新时间';
comment on column abr_message_template.remark               is '备注';

-- ----------------------------
-- 2、发送记录
-- ----------------------------
create table if not exists abr_message_send (
  send_id          bigserial      not null,
  template_code    varchar(64)    not null,
  channel          varchar(32)    default '',
  category         varchar(16)    default '',
  target_type      varchar(16)    not null,
  target_id        bigint         default null,
  recipient        varchar(64)    default '',
  agent_code       varchar(64)    default '',
  task_id          bigint         default null,
  status           varchar(16)    not null,
  reason           varchar(500)   default '',
  platform_msg_id  varchar(128)   default '',
  rendered         varchar(1000)  default '',
  create_by        varchar(64)    default '',
  create_time      timestamp      default now(),
  primary key (send_id)
);
create index if not exists idx_abr_message_send_target on abr_message_send (target_type, target_id, send_id desc);
create index if not exists idx_abr_message_send_time on abr_message_send (create_time);

comment on table  abr_message_send                 is '消息发送记录（每次发送一条，含被拦下与渠道未开通）';
comment on column abr_message_send.send_id         is '发送ID';
comment on column abr_message_send.template_code   is '业务编码';
comment on column abr_message_send.channel         is '渠道（未找到可达渠道时为空）';
comment on column abr_message_send.category        is '类别（marketing / service）';
comment on column abr_message_send.target_type     is '对象类型（app_user / lead / visitor / staff）';
comment on column abr_message_send.target_id       is '对象ID（staff 为 sys_user.user_id）';
comment on column abr_message_send.recipient       is '接收方（脱敏：openid、企业微信账号只留首尾，手机号只留后四位）';
comment on column abr_message_send.agent_code      is '发出的 Agent（员工测试为 manual）';
comment on column abr_message_send.task_id         is '编排任务ID';
comment on column abr_message_send.status          is '结果（字典 abr_message_status：sent 已发送 / mock 模拟发送 / failed 失败 / off 渠道未开通 / blocked 被拦下 / deferred 推迟 / no_channel 无可达渠道）';
comment on column abr_message_send.reason          is '原因或平台错误';
comment on column abr_message_send.platform_msg_id is '平台消息ID';
comment on column abr_message_send.rendered        is '渲染后的文字（不含手机号）';
comment on column abr_message_send.create_by       is '发送人（系统为 system）';
comment on column abr_message_send.create_time     is '发送时间';

-- ----------------------------
-- 3、种子模板（停用，待 M0-01 开通并在平台选用模板后填写、启用）
-- ----------------------------
insert into abr_message_template (template_code, channel, template_name, category, field_map, content, link, status, create_by, create_time, remark)
select v.code, v.channel, v.name, 'service', v.field_map::jsonb, v.content, v.link, '1', 'admin', now(), v.remark
from (values
  ('booking_confirmed', 'service_account', '预约成功', '{"thing1": "{productName}", "time2": "{startTime}", "thing3": "{consultantName}"}',
   '你已预约 {productName}，时间 {startTime}，顾问 {consultantName}。', '{appUrl}', '服务号类目模板“预约成功通知”；字段名以选用的模板为准'),
  ('booking_confirmed', 'sms', '预约成功', '{"product": "{productName}", "time": "{startTime}"}',
   '你已预约 {productName}，时间 {startTime}。', '', '短信模板须报备；变量名以报备的模板为准'),
  ('booking_reminder', 'service_account', '预约提醒', '{"thing1": "{productName}", "time2": "{startTime}"}',
   '你预约的 {productName} 将于 {startTime} 开始。', '{appUrl}', '开始前提醒（M8-04 发送）'),
  ('booking_reminder', 'sms', '预约提醒', '{"product": "{productName}", "time": "{startTime}"}',
   '你预约的 {productName} 将于 {startTime} 开始。', '', ''),
  ('booking_changed', 'service_account', '预约变更', '{"thing1": "{productName}", "time2": "{startTime}", "thing3": "{changeText}"}',
   '你预约的 {productName} {changeText}，新的时间 {startTime}。', '{appUrl}', '改约、取消'),
  ('deadline_reminder', 'service_account', '截止提醒', '{"thing1": "{programName}", "time2": "{deadline}"}',
   '你选校单中的 {programName} 将于 {deadline} 截止。', '{appUrl}', '项目动态订阅（M8-05）'),
  ('staff_alert', 'wecom', '员工提醒', '{}', '{title}\n{content}', '', '线索分配、SLA 超时与升级、熔断通知等员工提醒，经企业微信应用消息发送')
) as v(code, channel, name, field_map, content, link, remark)
where not exists (select 1 from abr_message_template t where t.template_code = v.code and t.channel = v.channel);

-- ----------------------------
-- 4、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('消息渠道', 'abr_message_channel'), ('消息发送结果', 'abr_message_status')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '服务号',         'service_account', 'abr_message_channel', 'success'),
  (2, '小程序订阅消息', 'mp_subscribe',    'abr_message_channel', 'primary'),
  (3, '企业微信',       'wecom',           'abr_message_channel', 'primary'),
  (4, '短信',           'sms',             'abr_message_channel', 'warning'),
  (1, '已发送',         'sent',            'abr_message_status', 'success'),
  (2, '模拟发送',       'mock',            'abr_message_status', 'info'),
  (3, '失败',           'failed',          'abr_message_status', 'danger'),
  (4, '渠道未开通',     'off',             'abr_message_status', 'info'),
  (5, '被拦下',         'blocked',         'abr_message_status', 'warning'),
  (6, '推迟',           'deferred',        'abr_message_status', 'warning'),
  (7, '无可达渠道',     'no_channel',      'abr_message_status', 'info')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 5、菜单：获客运营 → 消息与渠道 + 按钮；授予负责人
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '消息与渠道', p.menu_id, 22, 'message', 'abroad/message/index', '', '', 1, 0, 'C', '0', '0', 'abroad:message:list', 'message', 'admin', now(), '消息模板、发送记录、渠道开通状态'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:message:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from sys_menu c
cross join (values ('模板查询', 1, 'abroad:message:query'), ('模板新增', 2, 'abroad:message:add'), ('模板修改', 3, 'abroad:message:edit'),
                   ('模板删除', 4, 'abroad:message:remove'), ('测试发送', 5, 'abroad:message:test')) as v(menu_name, order_num, perms)
where c.perms = 'abroad:message:list' and not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms like 'abroad:message:%'
where r.role_key = 'abr_owner' and r.del_flag = '0'
on conflict do nothing;

-- ----------------------------
-- 6、员工提醒类型：熔断、预算闸（触发后通知有一键暂停权限的员工）
-- ----------------------------
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_notice_kind', 'danger', 'N', '0', 'admin', now(), ''
from (values (4, '熔断', 'breaker_tripped'), (5, 'AI 预算用完', 'budget_exceeded')) as v(dict_sort, dict_label, dict_value)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_notice_kind' and d.dict_value = v.dict_value);

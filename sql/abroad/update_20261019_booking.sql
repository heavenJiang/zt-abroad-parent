-- ----------------------------------------------------------------------------
-- M5-06 产品与报价配置、顾问时段、预约、订单（先人工确认收款）
--
-- - abr_product：任意数量的产品；价格或价格区间可为空（C 端显示“价格面议”）；剧本、转化时刻推荐、邀约 Agent 只引用产品编码，代码与提示词里不写价格。
-- - abr_consult_slot：顾问可预约时段（员工批量开放）；预约时用条件更新占用，同一时段只能被约一次。
-- - abr_booking：预约（C 端学生 / 家长自助，或员工代约）；预约后线索进入“首访已约”、写 booking_created 事件（线索卡片、邀约提醒 M8-04 订阅）。
-- - abr_order：订单；收款由员工人工确认（1a 不接支付）；服务类产品确认收款后线索进入“签约”。
-- 依赖：update_20261016_event_moment.sql（转化时刻规则）、update_20261018_orchestrator.sql（触发规则、获客运营菜单）；运行时用到线索（M5-01）
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、产品
-- ----------------------------
create table if not exists abr_product (
  product_id        bigserial      not null,
  product_code      varchar(64)    not null,
  product_name      varchar(100)   not null,
  category          varchar(16)    not null default 'consult',
  degrees           jsonb          not null default '[]'::jsonb,
  countries         jsonb          not null default '[]'::jsonb,
  description       varchar(1000)  default '',
  price_min         numeric(12,2)  default null,
  price_max         numeric(12,2)  default null,
  currency          varchar(3)     default 'CNY',
  price_note        varchar(200)   default '',
  bookable          char(1)        default 'N',
  duration_minutes  integer        default null,
  sort_order        integer        default 0,
  status            char(1)        default '0',
  create_by         varchar(64)    default '',
  create_time       timestamp,
  update_by         varchar(64)    default '',
  update_time       timestamp,
  remark            varchar(500)   default null,
  primary key (product_id)
);
create unique index if not exists uk_abr_product_code on abr_product (product_code);

comment on table  abr_product                  is '产品与报价（任意数量；价格或区间可为空；剧本、推荐、邀约只引用产品编码）';
comment on column abr_product.product_id       is '产品ID';
comment on column abr_product.product_code     is '产品编码（剧本 products、转化时刻推荐引用）';
comment on column abr_product.product_name     is '产品名称';
comment on column abr_product.category         is '类别（字典 abr_product_category：consult 咨询 / service 服务 / other 其他）';
comment on column abr_product.degrees          is '适用学段（abr_degree 值数组，空为全部）';
comment on column abr_product.countries        is '适用国家 / 地区（abr_country 值数组，空为全部）';
comment on column abr_product.description      is '介绍（对外展示，保存前过合规词表）';
comment on column abr_product.price_min        is '价格下限（元；为空表示未定价）';
comment on column abr_product.price_max        is '价格上限（元；与下限相同为固定价，为空表示只有下限或未定价）';
comment on column abr_product.currency         is '币种（ISO 4217）';
comment on column abr_product.price_note       is '价格说明（如“按国家数计价”）';
comment on column abr_product.bookable         is '是否可在线预约（Y：C 端可选顾问时段）';
comment on column abr_product.duration_minutes is '预约时长（分钟；可预约产品必填）';
comment on column abr_product.sort_order       is '显示顺序';
comment on column abr_product.status           is '状态（0正常 1停用）';
comment on column abr_product.create_by        is '创建者';
comment on column abr_product.create_time      is '创建时间';
comment on column abr_product.update_by        is '更新者';
comment on column abr_product.update_time      is '更新时间';
comment on column abr_product.remark           is '备注（内部）';

insert into abr_product (product_code, product_name, category, description, price_min, price_max, bookable, duration_minutes, sort_order, status, create_by, create_time, remark)
select v.code, v.name, v.category, v.descr, cast(v.pmin as numeric), cast(v.pmax as numeric), v.bookable, cast(v.minutes as integer), v.sort, '0', 'admin', now(),
       '草案：名称、时长与价格待创始人确认'
from (values
  ('consult_position', '定位咨询',           'consult', '顾问结合你的测评结果，一起看看目标国家 / 地区和项目怎么选。', '0', '0', 'Y', '20', 1),
  ('consult_plan',     '规划咨询',           'consult', '按你的背景和时间线，梳理申请准备的先后顺序与关键节点。',     null, null, 'Y', '45', 2),
  ('parent_meeting',   '家长会谈',           'consult', '和家长一起聊预算、地区和时间安排。',                         null, null, 'Y', '45', 3),
  ('service_master',   '硕士申请服务',       'service', '选校、文书、递交与后续跟进的完整服务。',                     null, null, 'N', null, 4),
  ('service_docs',     '文书与递交服务',     'service', '文书打磨与网申递交。',                                       null, null, 'N', null, 5)
) as v(code, name, category, descr, pmin, pmax, bookable, minutes, sort)
where not exists (select 1 from abr_product p where p.product_code = v.code);

-- 转化时刻推荐的服务对应到产品编码（原来为空的）
update abr_moment_rule r set recommend_product = v.product, update_by = 'admin', update_time = now()
from (values ('imbalance', 'consult_position'), ('repeated_edits', 'consult_position'), ('requirement_unmet', 'consult_plan'),
             ('parent_view', 'parent_meeting'), ('materials_behind', 'service_docs')) as v(moment, product)
where r.moment_code = v.moment and r.recommend_product is null;

-- ----------------------------
-- 2、顾问时段
-- ----------------------------
create table if not exists abr_consult_slot (
  slot_id       bigserial      not null,
  user_id       bigint         not null,
  start_time    timestamp      not null,
  end_time      timestamp      not null,
  mode          varchar(16)    not null default 'online',
  status        varchar(16)    not null default 'open',
  booking_id    bigint         default null,
  create_by     varchar(64)    default '',
  create_time   timestamp      default now(),
  update_by     varchar(64)    default '',
  update_time   timestamp      default now(),
  primary key (slot_id)
);
create unique index if not exists uk_abr_consult_slot on abr_consult_slot (user_id, start_time);
create index if not exists idx_abr_consult_slot_open on abr_consult_slot (start_time) where status = 'open';

comment on table  abr_consult_slot             is '顾问可预约时段（同一顾问同一开始时间一条；预约时条件更新占用）';
comment on column abr_consult_slot.slot_id     is '时段ID';
comment on column abr_consult_slot.user_id     is '顾问（sys_user）';
comment on column abr_consult_slot.start_time  is '开始时间（北京时间）';
comment on column abr_consult_slot.end_time    is '结束时间';
comment on column abr_consult_slot.mode        is '方式（字典 abr_booking_mode：online 线上 / offline 到店）';
comment on column abr_consult_slot.status      is '状态（open 可约 / booked 已约 / closed 已关闭）';
comment on column abr_consult_slot.booking_id  is '占用的预约';
comment on column abr_consult_slot.create_by   is '创建者';
comment on column abr_consult_slot.create_time is '创建时间';
comment on column abr_consult_slot.update_by   is '更新者';
comment on column abr_consult_slot.update_time is '更新时间';

-- ----------------------------
-- 3、预约
-- ----------------------------
create table if not exists abr_booking (
  booking_id      bigserial      not null,
  lead_id         bigint         default null,
  visitor_id      bigint         default null,
  app_user_id     bigint         default null,
  product_code    varchar(64)    not null,
  product_name    varchar(100)   not null,
  slot_id         bigint         default null,
  consultant_id   bigint         not null,
  start_time      timestamp      not null,
  end_time        timestamp      not null,
  mode            varchar(16)    not null default 'online',
  status          varchar(16)    not null default 'booked',
  source          varchar(16)    not null default 'app',
  note            varchar(500)   default '',
  cancel_reason   varchar(500)   default '',
  reschedule_count integer       default 0,
  create_by       varchar(64)    default '',
  create_time     timestamp      default now(),
  update_by       varchar(64)    default '',
  update_time     timestamp      default now(),
  primary key (booking_id)
);
create index if not exists idx_abr_booking_lead on abr_booking (lead_id);
create index if not exists idx_abr_booking_app_user on abr_booking (app_user_id, start_time desc);
create index if not exists idx_abr_booking_consultant on abr_booking (consultant_id, start_time);

comment on table  abr_booking                  is '预约（C 端自助或员工代约；首访等咨询）';
comment on column abr_booking.booking_id       is '预约ID';
comment on column abr_booking.lead_id          is '线索ID';
comment on column abr_booking.visitor_id       is '主访客ID';
comment on column abr_booking.app_user_id      is '学生账号ID（C 端自助预约时）';
comment on column abr_booking.product_code     is '产品编码';
comment on column abr_booking.product_name     is '产品名称（预约时的快照）';
comment on column abr_booking.slot_id          is '顾问时段';
comment on column abr_booking.consultant_id    is '顾问（sys_user）';
comment on column abr_booking.start_time       is '开始时间';
comment on column abr_booking.end_time         is '结束时间';
comment on column abr_booking.mode             is '方式（字典 abr_booking_mode）';
comment on column abr_booking.status           is '状态（字典 abr_booking_status：booked 已预约 / cancelled 已取消 / done 已完成 / no_show 未到）';
comment on column abr_booking.source           is '来源（app 学生自助 / staff 员工代约 / agent 邀约 Agent）';
comment on column abr_booking.note             is '学生留言或员工备注（不写手机号）';
comment on column abr_booking.cancel_reason    is '取消原因';
comment on column abr_booking.reschedule_count is '改约次数';
comment on column abr_booking.create_by        is '创建者';
comment on column abr_booking.create_time      is '创建时间';
comment on column abr_booking.update_by        is '更新者';
comment on column abr_booking.update_time      is '更新时间';

-- ----------------------------
-- 4、订单
-- ----------------------------
create table if not exists abr_order (
  order_id        bigserial      not null,
  order_no        varchar(32)    not null,
  lead_id         bigint         not null,
  product_code    varchar(64)    not null,
  product_name    varchar(100)   not null,
  amount          numeric(12,2)  not null,
  currency        varchar(3)     default 'CNY',
  status          varchar(16)    not null default 'pending',
  pay_method      varchar(32)    default '',
  pay_note        varchar(200)   default '',
  paid_time       timestamp      default null,
  confirmed_by    varchar(64)    default '',
  cancel_reason   varchar(500)   default '',
  create_by       varchar(64)    default '',
  create_time     timestamp      default now(),
  update_by       varchar(64)    default '',
  update_time     timestamp      default now(),
  remark          varchar(500)   default null,
  primary key (order_id)
);
create unique index if not exists uk_abr_order_no on abr_order (order_no);
create index if not exists idx_abr_order_lead on abr_order (lead_id);

comment on table  abr_order                is '订单（1a 不接支付：员工人工确认收款）';
comment on column abr_order.order_id       is '订单ID';
comment on column abr_order.order_no       is '订单号（系统生成）';
comment on column abr_order.lead_id        is '线索ID';
comment on column abr_order.product_code   is '产品编码';
comment on column abr_order.product_name   is '产品名称（下单时的快照）';
comment on column abr_order.amount         is '金额（元）';
comment on column abr_order.currency       is '币种';
comment on column abr_order.status         is '状态（字典 abr_order_status：pending 待确认收款 / paid 已确认收款 / cancelled 已取消 / refunded 已退款）';
comment on column abr_order.pay_method     is '收款方式（字典 abr_pay_method）';
comment on column abr_order.pay_note       is '收款说明（如转账流水尾号，不写银行卡号）';
comment on column abr_order.paid_time      is '确认收款时间';
comment on column abr_order.confirmed_by   is '确认收款人';
comment on column abr_order.cancel_reason  is '取消或退款原因';
comment on column abr_order.create_by      is '创建者';
comment on column abr_order.create_time    is '创建时间';
comment on column abr_order.update_by      is '更新者';
comment on column abr_order.update_time    is '更新时间';
comment on column abr_order.remark         is '备注';

-- ----------------------------
-- 5、触发：预约成功 → 线索卡片
-- ----------------------------
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select '预约成功 → 线索卡片', 'event', 'booking_created', '{}'::jsonb, null, null, 'lead_card', 'generate', '{"trigger": "booking"}'::jsonb,
       0, 24, 2, '0', 'admin', now(), 'M5-06'
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'lead_card' and t.event_type = 'booking_created');

-- ----------------------------
-- 6、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('产品类别', 'abr_product_category', ''),
  ('预约方式', 'abr_booking_mode',     ''),
  ('预约状态', 'abr_booking_status',   ''),
  ('订单状态', 'abr_order_status',     ''),
  ('收款方式', 'abr_pay_method',       '')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '咨询', 'consult', 'abr_product_category', 'primary'),
  (2, '服务', 'service', 'abr_product_category', 'success'),
  (3, '其他', 'other',   'abr_product_category', 'default'),
  (1, '线上', 'online',  'abr_booking_mode', 'primary'),
  (2, '到店', 'offline', 'abr_booking_mode', 'success'),
  (1, '已预约', 'booked',    'abr_booking_status', 'primary'),
  (2, '已取消', 'cancelled', 'abr_booking_status', 'info'),
  (3, '已完成', 'done',      'abr_booking_status', 'success'),
  (4, '未到',   'no_show',   'abr_booking_status', 'danger'),
  (1, '待确认收款', 'pending',   'abr_order_status', 'warning'),
  (2, '已确认收款', 'paid',      'abr_order_status', 'success'),
  (3, '已取消',     'cancelled', 'abr_order_status', 'info'),
  (4, '已退款',     'refunded',  'abr_order_status', 'danger'),
  (1, '对公转账', 'bank',   'abr_pay_method', 'primary'),
  (2, '微信收款', 'wechat', 'abr_pay_method', 'success'),
  (3, '支付宝',   'alipay', 'abr_pay_method', 'primary'),
  (4, '其他',     'other',  'abr_pay_method', 'default'),
  (9, '预约',     'booking', 'abr_lead_touch_type', 'success')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 7、菜单：获客运营 → 产品与报价、预约、订单 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (values
  ('产品与报价', 18, 'product', 'abroad/product/index', 'abroad:product:list', 'shopping', '产品与报价菜单'),
  ('预约',       19, 'booking', 'abroad/booking/index', 'abroad:booking:list', 'time-range', '预约与顾问时段菜单'),
  ('订单',       20, 'order',   'abroad/order/index',   'abroad:order:list',   'money', '订单菜单')
) as v(menu_name, order_num, path, component, perms, icon, remark)
cross join (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:product:list', '产品查询', 1, 'abroad:product:query'),
  ('abroad:product:list', '产品新增', 2, 'abroad:product:add'),
  ('abroad:product:list', '产品修改', 3, 'abroad:product:edit'),
  ('abroad:product:list', '产品删除', 4, 'abroad:product:remove'),
  ('abroad:booking:list', '时段管理', 1, 'abroad:booking:slot'),
  ('abroad:booking:list', '代约',     2, 'abroad:booking:add'),
  ('abroad:booking:list', '改约 / 取消 / 完成', 3, 'abroad:booking:edit'),
  ('abroad:order:list',   '新建订单', 1, 'abroad:order:add'),
  ('abroad:order:list',   '确认收款', 2, 'abroad:order:confirm'),
  ('abroad:order:list',   '取消 / 退款', 3, 'abroad:order:edit')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

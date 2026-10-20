-- ----------------------------------------------------------------------------
-- M9-05 合作方：专属渠道码与可嵌入工具链接、成交分成计算与对账（分成比例可配置，发放人工确认）
--
-- - 合作方是推荐人类型 partner（M9-02 abr_referrer）：推荐码即专属渠道码，链接为 C 端工具页 ?ch=partner&ct=推荐码，
--   归因与推荐码一致（首次进入为准，判为无效的不分成）；旧的 ?ch=ref 链接同样有效。
-- - abr_referrer 新增分成比例与归因有效天数（只对合作方生效；为空用配置 abroad.partner.default-rate / window-days）。
-- - abr_partner_commission：每个已确认收款的订单一条（订单 → 线索 → 被推荐人 → 合作方），分成 = 订单金额 × 比例（保存当时比例）。
--   定时任务 abrPartnerTask.reconcile() 每天对账：补算新收款的订单；订单取消或退款时未发放的分成作废，已发放的标记待追回。
--   状态：pending 待确认 → confirmed 已确认 → paid 已发放；作废 void。确认与发放都由员工操作，不预付、不自动打款。
-- 依赖：update_20261020_6_referral.sql（推荐人）、update_20261019_booking.sql（订单）、update_20261020_7_attribution.sql（渠道来源字典）
-- 可重复执行
-- ----------------------------------------------------------------------------

alter table abr_referrer add column if not exists commission_rate numeric(5,2) default null;
alter table abr_referrer add column if not exists window_days     integer      default null;
comment on column abr_referrer.commission_rate is '分成比例（%，只对合作方；为空用 abroad.partner.default-rate）';
comment on column abr_referrer.window_days     is '归因有效天数（被推荐人进入后多少天内收款的订单分成；为空用 abroad.partner.window-days）';

create table if not exists abr_partner_commission (
  commission_id     bigserial      not null,
  referrer_id       bigint         not null,
  referral_id       bigint         not null,
  lead_id           bigint         not null,
  order_id          bigint         not null,
  order_no          varchar(32)    not null,
  product_name      varchar(100)   default '',
  currency          varchar(3)     default 'CNY',
  order_amount      numeric(12,2)  not null,
  rate              numeric(5,2)   not null,
  commission_amount numeric(12,2)  not null,
  order_paid_time   timestamp      not null,
  settle_month      varchar(7)     not null,
  status            varchar(16)    not null default 'pending',
  refund_flag       char(1)        default 'N',
  void_reason       varchar(200)   default '',
  confirm_by        varchar(64)    default '',
  confirm_time      timestamp      default null,
  paid_by           varchar(64)    default '',
  paid_time         timestamp      default null,
  paid_note         varchar(200)   default '',
  create_by         varchar(64)    default '',
  create_time       timestamp      default now(),
  update_by         varchar(64)    default '',
  update_time       timestamp      default null,
  remark            varchar(500)   default null,
  primary key (commission_id)
);
create unique index if not exists uk_abr_partner_commission_order on abr_partner_commission (order_id);
create index if not exists idx_abr_partner_commission_month on abr_partner_commission (settle_month, referrer_id);

comment on table  abr_partner_commission                   is '合作方成交分成（每个已收款订单一条；确认与发放由员工操作）';
comment on column abr_partner_commission.commission_id     is '分成ID';
comment on column abr_partner_commission.referrer_id       is '合作方（abr_referrer，类型 partner）';
comment on column abr_partner_commission.referral_id       is '被推荐人记录';
comment on column abr_partner_commission.lead_id           is '线索';
comment on column abr_partner_commission.order_id          is '订单';
comment on column abr_partner_commission.order_no          is '订单号';
comment on column abr_partner_commission.product_name      is '产品名称';
comment on column abr_partner_commission.currency          is '币种';
comment on column abr_partner_commission.order_amount      is '订单金额';
comment on column abr_partner_commission.rate              is '分成比例（%，计算时的比例）';
comment on column abr_partner_commission.commission_amount is '分成金额（订单金额 × 比例，四舍五入到分）';
comment on column abr_partner_commission.order_paid_time   is '订单确认收款时间';
comment on column abr_partner_commission.settle_month      is '结算月份（按确认收款时间，yyyy-MM）';
comment on column abr_partner_commission.status            is '状态（字典 abr_commission_status：pending 待确认 / confirmed 已确认 / paid 已发放 / void 已作废）';
comment on column abr_partner_commission.refund_flag       is '已发放后订单退款（N 无 / Y 待追回 / D 已处理）';
comment on column abr_partner_commission.void_reason       is '作废原因';
comment on column abr_partner_commission.confirm_by        is '确认人';
comment on column abr_partner_commission.confirm_time      is '确认时间';
comment on column abr_partner_commission.paid_by           is '发放登记人';
comment on column abr_partner_commission.paid_time         is '发放时间';
comment on column abr_partner_commission.paid_note         is '发放说明（转账流水号等）';
comment on column abr_partner_commission.create_by         is '创建者';
comment on column abr_partner_commission.create_time       is '创建时间';
comment on column abr_partner_commission.update_by         is '更新者';
comment on column abr_partner_commission.update_time       is '更新时间';
comment on column abr_partner_commission.remark            is '备注';

-- ----------------------------
-- 字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '分成状态', 'abr_commission_status', '0', 'admin', now(), ''
where not exists (select 1 from sys_dict_type where dict_type = 'abr_commission_status');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '待确认', 'pending',   'abr_commission_status', 'warning', ''),
  (2, '已确认', 'confirmed', 'abr_commission_status', 'primary', ''),
  (3, '已发放', 'paid',      'abr_commission_status', 'success', ''),
  (4, '已作废', 'void',      'abr_commission_status', 'info',    ''),
  (6, '合作方', 'partner',   'abr_channel_source',    'warning', '合作方专属链接')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 菜单：获客运营 → 合作方分成 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '合作方分成', p.menu_id, 27, 'partner', 'abroad/partner/index', '', '', 1, 0, 'C', '0', '0', 'abroad:partner:list', 'money', 'admin', now(), '合作方链接、成交分成与对账'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:partner:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), v.remark
from sys_menu c
cross join (values ('对账', 1, 'abroad:partner:reconcile', '按订单补算分成、作废退款订单的分成'),
                   ('分成确认与发放', 2, 'abroad:partner:confirm', '确认、登记发放、作废'),
                   ('分成导出', 3, 'abroad:partner:export', '')) as v(menu_name, order_num, perms, remark)
where c.perms = 'abroad:partner:list' and not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms like 'abroad:partner:%'
where r.role_key = 'abr_owner' and r.del_flag = '0'
on conflict do nothing;

-- ----------------------------
-- 定时任务：每天对账
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '合作方分成对账', 'DEFAULT', 'abrPartnerTask.reconcile()', '0 40 3 * * ?', '3', '1', '0', 'admin', now(),
       'M9-05：为已确认收款、经合作方链接进入的订单生成待确认分成；订单取消或退款时未发放的分成作废，已发放的标记待追回'
where not exists (select 1 from sys_job where invoke_target = 'abrPartnerTask.reconcile()');

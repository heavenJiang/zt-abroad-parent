-- ----------------------------------------------------------------------------
-- M9-03 归因看板：内容 → 工具 → 选校单 / 登录 → 转化时刻 → 预约 → 签约；北极星“每周活跃选校单”与留存
--
-- - abr_plan_active：选校单打开记录（每个访客每天一行，北京时间日期）：打开选校单、申请日历、材料清单时记录，
--   同时记下当时选校单的项目数。每周活跃选校单 = 本周有一天打开过、且当时选校单里至少 1 个项目的访客数。
--   登录合并访客时随其他记录一起并入主访客。
-- - 归因按访客首次进入的渠道码与内容编号（abr_visitor.channel_code / content_code，主访客为准），
--   内容编号对应到内容、账号与剧本；看板只读，不建汇总表。
-- - 字典 abr_channel_source：非账号渠道码的名称（分享、推荐码、offer 卡、公开项目页）。
-- 依赖：update_20261020_6_referral.sql（推荐码渠道）、update_20261019_z_roles.sql（角色）
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_plan_active (
  visitor_id   bigint      not null,
  active_date  date        not null,
  item_count   integer     not null default 0,
  open_count   integer     not null default 1,
  first_time   timestamp   default now(),
  last_time    timestamp   default now(),
  primary key (visitor_id, active_date)
);
create index if not exists idx_abr_plan_active_date on abr_plan_active (active_date);

comment on table  abr_plan_active             is '选校单打开记录（每个访客每天一行，用于每周活跃选校单与留存）';
comment on column abr_plan_active.visitor_id  is '访客（登录后为主访客）';
comment on column abr_plan_active.active_date is '日期（北京时间）';
comment on column abr_plan_active.item_count  is '当天最后一次打开时选校单的项目数';
comment on column abr_plan_active.open_count  is '当天打开次数';
comment on column abr_plan_active.first_time  is '当天第一次打开时间';
comment on column abr_plan_active.last_time   is '当天最后一次打开时间';

-- ----------------------------
-- 字典：渠道来源（账号渠道码显示账号名称）
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '渠道来源', 'abr_channel_source', '0', 'admin', now(), '非渠道账号的渠道码；账号渠道码在看板中显示账号名称'
where not exists (select 1 from sys_dict_type where dict_type = 'abr_channel_source');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_channel_source', v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '直接访问',     'direct',   'info',    '没有渠道码'),
  (2, '分享',         'share',    'success', '定位海报、选校单分享卡、家长只读视图'),
  (3, '推荐码',       'ref',      'success', '推荐人专属链接'),
  (4, 'offer 卡',     'offer',    'success', 'offer 墙与 offer 卡二维码'),
  (5, '公开项目页',   'site',     'primary', '静态项目页与聚合页')
) as v(dict_sort, dict_label, dict_value, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_channel_source' and d.dict_value = v.dict_value);

-- ----------------------------
-- 菜单：获客运营 → 归因看板
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '归因看板', p.menu_id, 26, 'attribution', 'abroad/attribution/index', '', '', 1, 0, 'C', '0', '0', 'abroad:attribution:list', 'chart', 'admin', now(), '获客漏斗归因、每周活跃选校单与留存'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:attribution:list');

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms = 'abroad:attribution:list'
where r.role_key = 'abr_owner' and r.del_flag = '0'
on conflict do nothing;

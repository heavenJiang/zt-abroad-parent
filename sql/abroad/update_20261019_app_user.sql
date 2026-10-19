-- ----------------------------------------------------------------------------
-- M4-04 C 端身份：学生账号 abr_app_user、学生令牌、服务号网页授权登录、匿名访客合并、手机号单独同意、访客转线索事件
--
-- - 学生账号不进 sys_user；学生令牌只存在 Redis（abr_app_token:令牌哈希），请求头 X-App-Token，与员工令牌互不通用。
-- - 登录后 C 端接口的“本人”取账号的主访客（primary_visitor_id）；首次登录时当前访客成为主访客，
--   其他设备的访客登录时把测评、档案、选校单、材料、分享、工具记录、事件合并进主访客，原访客记 merged_into。
-- - 已关联账号的访客不能再匿名使用（防止退出登录后同一设备的下一个人看到账号数据），前端重新获取访客令牌即可。
-- - 手机号须单独同意（隐私政策当前版本）并短信验证后绑定；短信服务（M0-01）未开通时提示。
-- - 首次登录、绑定手机号写业务事件（app_user_registered / app_user_login / app_user_phone_bound），供线索收口（M5-01）订阅。
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、学生账号
-- ----------------------------
create table if not exists abr_app_user (
  app_user_id           bigserial      not null,
  nickname              varchar(64)    default '',
  oa_openid             varchar(64)    default null,
  mini_openid           varchar(64)    default null,
  unionid               varchar(64)    default null,
  phone                 varchar(20)    default null,
  phone_consent_time    timestamp      default null,
  phone_consent_version varchar(32)    default '',
  primary_visitor_id    bigint         default null,
  register_channel      varchar(32)    default 'direct',
  register_content      varchar(32)    default '',
  login_count           integer        default 0,
  last_login_time       timestamp      default null,
  status                char(1)        default '0',
  del_flag              char(1)        default '0',
  create_by             varchar(64)    default '',
  create_time           timestamp,
  update_by             varchar(64)    default '',
  update_time           timestamp,
  remark                varchar(500)   default null,
  primary key (app_user_id)
);
create unique index if not exists uk_abr_app_user_oa_openid on abr_app_user (oa_openid) where oa_openid is not null;
create unique index if not exists uk_abr_app_user_mini_openid on abr_app_user (mini_openid) where mini_openid is not null;
create unique index if not exists uk_abr_app_user_unionid on abr_app_user (unionid) where unionid is not null;
create unique index if not exists uk_abr_app_user_phone on abr_app_user (phone) where phone is not null and del_flag = '0';

comment on table  abr_app_user                       is '学生账号（C 端；不进 sys_user；登录后 C 端数据归到主访客）';
comment on column abr_app_user.app_user_id           is '学生账号ID';
comment on column abr_app_user.nickname              is '称呼（用户自填，可空）';
comment on column abr_app_user.oa_openid             is '服务号 openid（网页授权）';
comment on column abr_app_user.mini_openid           is '小程序 openid（M4-09）';
comment on column abr_app_user.unionid               is '微信 unionid（服务号与小程序绑定同一开放平台时有值，用于跨端识别同一人）';
comment on column abr_app_user.phone                 is '手机号（单独同意并短信验证后绑定；明文查看须单独权限并记操作日志）';
comment on column abr_app_user.phone_consent_time    is '手机号单独同意时间';
comment on column abr_app_user.phone_consent_version is '同意时的隐私政策版本';
comment on column abr_app_user.primary_visitor_id    is '主访客ID（登录后 C 端数据都归到该访客）';
comment on column abr_app_user.register_channel      is '注册时的渠道码（取自首次登录的访客）';
comment on column abr_app_user.register_content      is '注册时的内容编号';
comment on column abr_app_user.login_count           is '登录次数';
comment on column abr_app_user.last_login_time       is '最近登录时间';
comment on column abr_app_user.status                is '状态（0正常 1停用）';
comment on column abr_app_user.del_flag              is '删除标志（0存在 2删除）';
comment on column abr_app_user.create_by             is '创建者';
comment on column abr_app_user.create_time           is '创建时间';
comment on column abr_app_user.update_by             is '更新者';
comment on column abr_app_user.update_time           is '更新时间';
comment on column abr_app_user.remark                is '备注';

-- ----------------------------
-- 2、访客：合并去向
-- ----------------------------
alter table abr_visitor add column if not exists merged_into bigint default null;
comment on column abr_visitor.merged_into is '合并进的主访客ID（其他设备登录同一账号时，本访客的数据已移到主访客）';
comment on column abr_visitor.app_user_id is '学生账号ID（登录后关联；关联后只能凭该账号的学生令牌使用）';

-- ----------------------------
-- 3、菜单：获客运营 → C 端账号 + 按钮（查看手机号明文须单独权限）
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select 'C 端账号', p.menu_id, 15, 'appUser', 'abroad/appUser/index', '', '', 1, 0, 'C', '0', '0', 'abroad:appUser:list', 'peoples', 'admin', now(), 'C 端学生账号菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:appUser:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:appUser:list', '账号查询',     1, 'abroad:appUser:query'),
  ('abroad:appUser:list', '查看手机号',   2, 'abroad:appUser:phone'),
  ('abroad:appUser:list', '停用 / 启用',  3, 'abroad:appUser:edit')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

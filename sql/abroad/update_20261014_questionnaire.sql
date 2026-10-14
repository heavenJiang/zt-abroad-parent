-- ----------------------------------------------------------------------------
-- M3-01 问卷定义与版本管理：问卷版本表 + 字典 + 菜单（测评与选校 → 问卷版本）
-- 可重复执行（create if not exists / where not exists）
-- 问卷种子（seed/questionnaire/*.json）由 update_20261014_questionnaire_seed.sql 导入
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、问卷版本表
-- ----------------------------
create table if not exists abr_questionnaire (
  questionnaire_id   bigserial      not null,
  code               varchar(64)    not null,
  version            integer        not null,
  version_label      varchar(100)   default '',
  title              varchar(200)   default '',
  publish_status     varchar(16)    default 'draft',
  question_count     integer        default 0,
  definition         jsonb          not null,
  activated_by       varchar(64)    default '',
  activated_time     timestamp,
  del_flag           char(1)        default '0',
  create_by          varchar(64)    default '',
  create_time        timestamp,
  update_by          varchar(64)    default '',
  update_time        timestamp,
  remark             varchar(500)   default null,
  primary key (questionnaire_id)
);
create unique index if not exists uk_abr_questionnaire_code_version on abr_questionnaire (code, version) where del_flag = '0';
create unique index if not exists uk_abr_questionnaire_active on abr_questionnaire (code) where publish_status = 'active' and del_flag = '0';

comment on table  abr_questionnaire                  is '问卷版本表（题目定义 jsonb，版本发布后不可修改）';
comment on column abr_questionnaire.questionnaire_id is '问卷版本ID';
comment on column abr_questionnaire.code             is '问卷编码（JSON 顶层 code）';
comment on column abr_questionnaire.version          is '版本号（JSON 顶层 version，整数）';
comment on column abr_questionnaire.version_label    is '版本说明（JSON 顶层 version_label）';
comment on column abr_questionnaire.title            is '问卷标题';
comment on column abr_questionnaire.publish_status   is '发布状态（字典 abr_questionnaire_status：draft 草稿 active 启用 retired 已停用）；同一编码只能有一个启用版本';
comment on column abr_questionnaire.question_count   is '题目数';
comment on column abr_questionnaire.definition       is '问卷定义（完整 JSON，格式见 seed/questionnaire/README.md）';
comment on column abr_questionnaire.activated_by     is '启用人';
comment on column abr_questionnaire.activated_time   is '启用时间';
comment on column abr_questionnaire.del_flag         is '删除标志（0存在 2删除）；只有草稿可删除';
comment on column abr_questionnaire.create_by        is '创建者';
comment on column abr_questionnaire.create_time      is '创建时间';
comment on column abr_questionnaire.update_by        is '更新者';
comment on column abr_questionnaire.update_time      is '更新时间';
comment on column abr_questionnaire.remark           is '备注';

-- ----------------------------
-- 2、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '问卷发布状态', 'abr_questionnaire_status', '0', 'admin', now(), '问卷版本的发布状态'
where not exists (select 1 from sys_dict_type t where t.dict_type = 'abr_questionnaire_status');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_questionnaire_status', v.list_class, 'N', '0', 'admin', now()
from (values
  (1, '草稿',   'draft',   'info'),
  (2, '启用',   'active',  'success'),
  (3, '已停用', 'retired', 'default')
) as v(dict_sort, dict_label, dict_value, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_questionnaire_status' and d.dict_value = v.dict_value);

-- ----------------------------
-- 3、菜单：测评与选校（目录）→ 问卷版本 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '测评与选校', 0, 20, 'assessment', null, '', '', 1, 0, 'M', '0', '0', '', 'form', 'admin', now(), '留学业务：测评与选校'
where not exists (select 1 from sys_menu where parent_id = 0 and path = 'assessment');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '问卷版本', p.menu_id, 1, 'questionnaire', 'abroad/questionnaire/index', '', '', 1, 0, 'C', '0', '0', 'abroad:questionnaire:list', 'documentation', 'admin', now(), '问卷版本菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'assessment') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:questionnaire:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('问卷查询', 1, 'abroad:questionnaire:query'),
  ('问卷导入', 2, 'abroad:questionnaire:import'),
  ('问卷启用', 3, 'abroad:questionnaire:activate'),
  ('问卷删除', 4, 'abroad:questionnaire:remove')
) as v(menu_name, order_num, perms)
join sys_menu c on c.perms = 'abroad:questionnaire:list'
where not exists (select 1 from sys_menu m where m.perms = v.perms);

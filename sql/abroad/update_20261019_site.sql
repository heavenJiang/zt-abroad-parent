-- ----------------------------
-- M7-08 公开项目页与聚合页（后端生成静态 HTML，Nginx 提供）
-- 依赖：update_20261018_orchestrator.sql（获客运营菜单）
-- 可重复执行
-- ----------------------------

-- ----------------------------
-- 1、生成记录
-- ----------------------------
create table if not exists abr_site_build (
  build_id        bigserial     primary key,
  trigger_type    varchar(16)   not null default 'schedule',
  fingerprint     varchar(64)   not null default '',
  status          varchar(16)   not null default 'running',
  page_total      integer       not null default 0,
  written_count   integer       not null default 0,
  removed_count   integer       not null default 0,
  submitted_count integer       not null default 0,
  changes         jsonb         not null default '{}'::jsonb,
  submit_result   jsonb         not null default '{}'::jsonb,
  error_msg       varchar(2000) not null default '',
  start_time      timestamp     not null default now(),
  end_time        timestamp,
  create_by       varchar(64)   not null default '',
  create_time     timestamp     not null default now(),
  remark          varchar(500)  not null default ''
);

comment on table  abr_site_build                 is '公开项目页生成记录';
comment on column abr_site_build.build_id        is '记录ID';
comment on column abr_site_build.trigger_type    is '触发方式（字典 abr_site_trigger：schedule 定时检查 / manual 手动重新生成）';
comment on column abr_site_build.fingerprint     is '院校库与日期的指纹（与上次成功的相同时定时检查跳过）';
comment on column abr_site_build.status          is '状态（字典 abr_site_build_status：running 生成中 / success 成功 / failed 失败）';
comment on column abr_site_build.page_total      is '页面总数（HTML，不含 404）';
comment on column abr_site_build.written_count   is '新增或内容变化而重写的文件数';
comment on column abr_site_build.removed_count   is '删除的文件数（项目下线、聚合页不再有项目）';
comment on column abr_site_build.submitted_count is '提交收录的地址数';
comment on column abr_site_build.changes         is '变化的页面（jsonb：{"added":[],"updated":[],"removed":[]}，每类最多 200 条）';
comment on column abr_site_build.submit_result   is '收录提交结果（jsonb：{"baidu":{...},"indexnow":{...}}，未配置为 off）';
comment on column abr_site_build.error_msg       is '失败原因';
comment on column abr_site_build.start_time      is '开始时间';
comment on column abr_site_build.end_time        is '结束时间';
comment on column abr_site_build.create_by       is '创建者';
comment on column abr_site_build.create_time     is '创建时间';
comment on column abr_site_build.remark          is '备注';

create index if not exists idx_abr_site_build_status on abr_site_build (status, build_id);

-- ----------------------------
-- 2、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('项目页生成方式', 'abr_site_trigger',      '公开项目页生成的触发方式'),
  ('项目页生成状态', 'abr_site_build_status', '公开项目页生成记录的状态')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '定时检查', 'schedule', 'abr_site_trigger',      'info',    '院校库或日期变化时生成'),
  (2, '手动',     'manual',   'abr_site_trigger',      'primary', '员工在管理端重新生成'),
  (1, '生成中',   'running',  'abr_site_build_status', 'warning', ''),
  (2, '成功',     'success',  'abr_site_build_status', 'success', ''),
  (3, '失败',     'failed',   'abr_site_build_status', 'danger',  '')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 3、菜单：获客运营 → 公开项目页 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '公开项目页', p.menu_id, 11, 'site', 'abroad/site/index', '', '', 1, 0, 'C', '0', '0', 'abroad:site:list', 'international', 'admin', now(), '公开项目页生成记录菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:site:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:site:list', '页面预览', 1, 'abroad:site:query'),
  ('abroad:site:list', '重新生成', 2, 'abroad:site:build')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

-- ----------------------------
-- 4、定时任务：每 5 分钟检查院校库与日期，有变化时只重写变化的页面
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '公开项目页', 'DEFAULT', 'abrSiteTask.refresh()', '0 0/5 * * * ?', '3', '1', '0', 'admin', now(),
       'M7-08：院校库或日期变化时重新生成公开项目页、聚合页与站点地图，并提交收录（未配置时不提交）'
where not exists (select 1 from sys_job where invoke_target = 'abrSiteTask.refresh()');

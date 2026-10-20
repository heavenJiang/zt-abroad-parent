-- ----------------------------------------------------------------------------
-- M9-01 录取案例与 offer 库：结构化录取结果、核验等级、算法与宣传两项授权（可撤回）、offer 截图私有存储、offer 卡与 offer 墙
--
-- - abr_case：一条为一个学生一个项目一个申请季的结果（录取 / 拒绝 / 候补 / 撤申都记），背景与测评同一套维度；
--   身份只通过 lead_id 关联，只有 abroad:case:identity 权限能看到；对外只显示泛化后的背景（院校层次、均分区间）。
-- - 公开展示（offer 卡、offer 墙、内容引用）须：录取、核验等级 A / B、取得宣传授权且未撤回、状态正常；授权时写 offer_authorized 事件。
-- - 合规闸门：内容引用的 case / offer 须为可公开的记录，否则拦截（替换 M6-03 的“案例库未上线”）。
-- 依赖：update_20261019_lead.sql（线索）、update_20261019_z_roles.sql（预置角色）
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_case (
  case_id           bigserial      not null,
  lead_id           bigint         default null,
  season            varchar(16)    not null,
  country           varchar(8)     not null,
  degree            varchar(16)    not null default 'master',
  program_id        bigint         default null,
  school_name       varchar(200)   not null,
  program_name      varchar(200)   not null,
  result            varchar(16)    not null,
  scholarship       varchar(200)   default '',
  undergrad_tier    varchar(32)    default '',
  major_direction   varchar(64)    default '',
  gpa               numeric(5,2)   default null,
  ielts             numeric(3,1)   default null,
  test_scores       varchar(200)   default '',
  experience        varchar(500)   default '',
  verify_level      char(1)        not null default 'C',
  source            varchar(16)    not null default 'manual',
  algo_consent      char(1)        default 'N',
  publicity_consent char(1)        default 'N',
  consent_time      timestamp      default null,
  revoke_time       timestamp      default null,
  offer_file        varchar(200)   default '',
  status            char(1)        default '0',
  create_by         varchar(64)    default '',
  create_time       timestamp      default now(),
  update_by         varchar(64)    default '',
  update_time       timestamp      default null,
  remark            varchar(500)   default null,
  primary key (case_id)
);
create unique index if not exists uk_abr_case on abr_case (lead_id, program_id, season) where lead_id is not null and program_id is not null;
create index if not exists idx_abr_case_public on abr_case (result, publicity_consent, status);

comment on table  abr_case                   is '录取案例与 offer（真实、结构化、可追溯、对外不暴露个人）';
comment on column abr_case.case_id          is '案例ID';
comment on column abr_case.lead_id          is '关联线索（身份信息只留在线索与学生档案；只有 abroad:case:identity 权限可见）';
comment on column abr_case.season           is '申请季（如 2027-fall）';
comment on column abr_case.country          is '国家 / 地区（字典 abr_country）';
comment on column abr_case.degree           is '学段（字典 abr_degree）';
comment on column abr_case.program_id       is '院校库项目（匹配不到时为空，由人工归一）';
comment on column abr_case.school_name      is '院校名称（快照）';
comment on column abr_case.program_name     is '项目名称（快照或原始名称）';
comment on column abr_case.result           is '结果（字典 abr_case_result：admit 录取 / reject 拒绝 / waitlist 候补 / withdrawn 撤申）';
comment on column abr_case.scholarship      is '奖学金（可空）';
comment on column abr_case.undergrad_tier   is '本科院校层次（字典 abr_undergrad_tier）';
comment on column abr_case.major_direction  is '本科专业方向（字典 abr_major_direction）';
comment on column abr_case.gpa              is '均分（百分制；对外只显示区间）';
comment on column abr_case.ielts            is '雅思总分';
comment on column abr_case.test_scores      is '其他语言与标化（如 GRE 320）';
comment on column abr_case.experience       is '科研 / 实习 / 竞赛概要（不含可识别个人的信息）';
comment on column abr_case.verify_level     is '核验等级（字典 abr_case_verify：A 顾问核验 offer / B 有截图 / C 只有自报 / D 未核验）';
comment on column abr_case.source           is '来源（字典 abr_case_source：signed 签约学生 / self_report 用户回填 / manual 员工录入）';
comment on column abr_case.algo_consent     is '算法使用授权（Y / N）';
comment on column abr_case.publicity_consent is '宣传授权（Y：可生成 offer 卡、进 offer 墙与内容素材；可随时撤回）';
comment on column abr_case.consent_time     is '最近一次授权时间';
comment on column abr_case.revoke_time      is '撤回授权时间（撤回后两项授权都为 N）';
comment on column abr_case.offer_file       is 'offer 截图（私有目录相对路径，不对外提供）';
comment on column abr_case.status           is '状态（0 正常 1 停用：异常值复核中或作废）';
comment on column abr_case.create_by        is '创建者';
comment on column abr_case.create_time      is '创建时间';
comment on column abr_case.update_by        is '更新者';
comment on column abr_case.update_time      is '更新时间';
comment on column abr_case.remark           is '备注';

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('案例结果', 'abr_case_result'), ('案例核验等级', 'abr_case_verify'), ('案例来源', 'abr_case_source')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '录取',           'admit',       'abr_case_result', 'success'),
  (2, '拒绝',           'reject',      'abr_case_result', 'danger'),
  (3, '候补',           'waitlist',    'abr_case_result', 'warning'),
  (4, '撤申',           'withdrawn',   'abr_case_result', 'info'),
  (1, 'A 顾问核验 offer', 'A',         'abr_case_verify', 'success'),
  (2, 'B 有截图',       'B',           'abr_case_verify', 'primary'),
  (3, 'C 只有自报',     'C',           'abr_case_verify', 'warning'),
  (4, 'D 未核验',       'D',           'abr_case_verify', 'info'),
  (1, '签约学生',       'signed',      'abr_case_source', 'primary'),
  (2, '用户回填',       'self_report', 'abr_case_source', 'success'),
  (3, '员工录入',       'manual',      'abr_case_source', 'info')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- 菜单：获客运营 → offer 库 + 按钮；授予负责人
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select 'offer 库', p.menu_id, 24, 'case', 'abroad/case/index', '', '', 1, 0, 'C', '0', '0', 'abroad:case:list', 'star', 'admin', now(), '录取案例与 offer、授权、offer 卡'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:case:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), v.remark
from sys_menu c
cross join (values ('案例查询', 1, 'abroad:case:query', ''), ('案例新增', 2, 'abroad:case:add', ''), ('案例修改', 3, 'abroad:case:edit', '含授权与 offer 截图上传'),
                   ('案例删除', 4, 'abroad:case:remove', ''), ('关联学生', 5, 'abroad:case:identity', '查看与设置关联线索、查看 offer 截图')) as v(menu_name, order_num, perms, remark)
where c.perms = 'abroad:case:list' and not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms like 'abroad:case:%'
where r.role_key = 'abr_owner' and r.del_flag = '0'
on conflict do nothing;

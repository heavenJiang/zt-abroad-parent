-- ----------------------------------------------------------------------------
-- 预置 3 个角色（工作室模式）与“查看全部线索”权限（M1-03 剩余部分，并入 M5）
--
-- - 负责人 abr_owner：全部留学业务菜单与按钮、用户管理，可看全部线索（abroad:lead:all）。
-- - 顾问 abr_consultant：线索（只看分配给自己的）、预约与订单（只看自己线索的）、产品、院校库与测评记录只读。
-- - 数据录入 abr_editor：院校库（院校、项目、申请要求、国内高校、材料模板）的维护与导入。
-- 线索数据范围不用 RuoYi 部门数据权限，由 abroad:lead:all 控制（AbrLeadScope）：没有它的员工只能访问自己负责的线索。
-- 菜单按权限标识匹配授予，并补上上级目录；之后新增的业务菜单须在各自脚本中授予对应角色。
-- 文件名带 z_，保证排在同一天其他建菜单的脚本之后。
-- 依赖：update_20261019_lead.sql（线索菜单）及之前的全部菜单脚本
-- 可重复执行
-- ----------------------------------------------------------------------------

-- 1、查看全部线索
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '查看全部线索', c.menu_id, 20, '', '', '', '', 1, 0, 'F', '0', '0', 'abroad:lead:all', '#', 'admin', now(), '没有此权限只能看分配给自己的线索'
from sys_menu c
where c.perms = 'abroad:lead:list' and c.menu_type = 'C' and not exists (select 1 from sys_menu m where m.perms = 'abroad:lead:all');

-- 2、角色（data_scope 只影响 RuoYi 自带的部门数据权限）
insert into sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
select v.role_name, v.role_key, v.role_sort, v.data_scope, 1, 1, '0', '0', 'admin', now(), v.remark
from (values
  ('负责人',   'abr_owner',      3, '1', '工作室负责人：全部业务与全部线索、员工管理'),
  ('顾问',     'abr_consultant', 4, '5', '只看分配给自己的线索及其预约、订单；院校库只读'),
  ('数据录入', 'abr_editor',     5, '5', '院校库维护与导入')
) as v(role_name, role_key, role_sort, data_scope, remark)
where not exists (select 1 from sys_role r where r.role_key = v.role_key and r.del_flag = '0');

-- 3、角色菜单：按权限标识选出菜单，再补全上级目录
with recursive grant_perms(role_key, pattern) as (
  values
    ('abr_owner', 'abroad:%'),
    ('abr_owner', 'system:user:%'),
    ('abr_consultant', 'abroad:lead:list'), ('abr_consultant', 'abroad:lead:query'), ('abr_consultant', 'abroad:lead:add'),
    ('abr_consultant', 'abroad:lead:edit'), ('abr_consultant', 'abroad:lead:follow'), ('abr_consultant', 'abroad:lead:stage'),
    ('abr_consultant', 'abroad:lead:assign'), ('abr_consultant', 'abroad:lead:grade'), ('abr_consultant', 'abroad:lead:score'),
    ('abr_consultant', 'abroad:lead:card'), ('abr_consultant', 'abroad:lead:phone'),
    ('abr_consultant', 'abroad:booking:list'), ('abr_consultant', 'abroad:booking:add'), ('abr_consultant', 'abroad:booking:edit'),
    ('abr_consultant', 'abroad:order:list'), ('abr_consultant', 'abroad:order:add'),
    ('abr_consultant', 'abroad:product:list'), ('abr_consultant', 'abroad:product:query'),
    ('abr_consultant', 'abroad:school:list'), ('abr_consultant', 'abroad:school:query'),
    ('abr_consultant', 'abroad:program:list'), ('abr_consultant', 'abroad:program:query'),
    ('abr_consultant', 'abroad:cnUniversity:list'), ('abr_consultant', 'abroad:cnUniversity:query'),
    ('abr_consultant', 'abroad:assessment:list'), ('abr_consultant', 'abroad:assessment:query'),
    ('abr_editor', 'abroad:school:%'), ('abr_editor', 'abroad:program:%'), ('abr_editor', 'abroad:cnUniversity:%'),
    ('abr_editor', 'abroad:materialTemplate:%')
),
picked(role_key, menu_id) as (
  select distinct g.role_key, m.menu_id
  from grant_perms g join sys_menu m on m.perms like g.pattern
),
tree(role_key, menu_id) as (
  select role_key, menu_id from picked
  union
  select t.role_key, m.parent_id from tree t join sys_menu m on m.menu_id = t.menu_id where m.parent_id <> 0
)
insert into sys_role_menu (role_id, menu_id)
select r.role_id, t.menu_id
from tree t join sys_role r on r.role_key = t.role_key and r.del_flag = '0'
on conflict do nothing;

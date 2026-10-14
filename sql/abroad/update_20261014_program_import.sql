-- ----------------------------------------------------------------------------
-- M2-07 项目批量导入：按钮权限 abroad:program:import（可重复执行）
-- ----------------------------------------------------------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '项目导入', c.menu_id, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'abroad:program:import', '#', 'admin', now(), ''
from sys_menu c
where c.perms = 'abroad:program:list'
  and not exists (select 1 from sys_menu m where m.perms = 'abroad:program:import');

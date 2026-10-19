-- ----------------------------------------------------------------------------
-- 修复菜单路由名称重复：目录“测评与选校”（path assessment）与其下菜单“测评记录”（path 同为 assessment）
-- 都生成路由名 Assessment，vue-router 拒绝同名嵌套路由，管理后台登录后加载路由失败并被登出。
-- 给“测评记录”指定路由名 AssessmentRecord（与页面组件 name 一致，保证页签缓存生效）。
-- 依赖：update_20261014_visitor_profile.sql（测评记录菜单）
-- 可重复执行
-- ----------------------------------------------------------------------------

update sys_menu
set route_name = 'AssessmentRecord', update_by = 'admin', update_time = now()
where perms = 'abroad:assessment:list' and menu_type = 'C' and coalesce(route_name, '') = '';

-- ----------------------------------------------------------------------------
-- 管理后台菜单分组：获客运营原来 29 个页面平铺，按“谁在用、做什么”重新分组（见《模块拆分》1a 页面清单）
--
-- 线索与成交（新一级目录，顾问每天用，排在业务菜单最前）
--   线索收件箱 / 预约 / 订单 / C 端账号
--   推荐与合作：推荐与奖励、合作方分成（都涉及出款，须人工确认）
--   规则配置：线索分配、线索评分规则、产品与报价（一次性定参）
-- 获客运营
--   例外队列（单独放第一位：Agent 全自动，人只处理例外与抽检）
--   数据看板：归因看板、增长周报、A/B 实验（只读统计与实验评估）
--   内容运营：选题排期 → 内容库 → 发布记录，渠道账号、公开项目页、offer 库（内容素材）
--   自动化编排：营销剧本、营销日历、触发规则、培育序列、编排任务（任务运行记录）
--   消息触达：消息与渠道（模板与发送记录）、触达记录
--   闸门与监控：Agent 运行、熔断、合规词表
-- 只移动菜单（改 parent_id、order_num），不改页面、权限标识与路由名；页面地址随目录变化（如 /growth/lead → /sales/lead）。
-- 角色授权：新目录授予拥有其下页面的角色；不再有可见页面的目录从该角色收回（顾问不再看到空的获客运营）。
-- 之后新增的获客页面挂到对应子目录下（按 path 查找父目录）。
-- 依赖：之前全部建菜单的脚本（update_20261020_9_growth_analyst.sql 及之前）
-- 可重复执行
-- ----------------------------------------------------------------------------

-- 1、一级目录：线索与成交
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '线索与成交', 0, 5, 'sales', null, '', '', 1, 0, 'M', '0', '0', '', 'people', 'admin', now(), '线索、预约、订单、推荐与合作、成交规则'
where not exists (select 1 from sys_menu where parent_id = 0 and path = 'sales');

-- 2、子目录
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, null, '', '', 1, 0, 'M', '0', '0', '', v.icon, 'admin', now(), v.remark
from (values
  ('sales',  '推荐与合作', 5, 'referralOps', 'peoples',       '推荐奖励与合作方分成（出款须人工确认）'),
  ('sales',  '规则配置',   6, 'salesRule',   'system',        '线索分配、评分规则、产品与报价'),
  ('growth', '数据看板',   2, 'board',       'chart',         '归因、周报、A/B 实验'),
  ('growth', '内容运营',   3, 'contentOps',  'edit',          '选题、内容、发布、渠道账号、公开项目页、offer 库'),
  ('growth', '自动化编排', 4, 'automation',  'job',           '剧本、日历、触发规则、培育序列、编排任务'),
  ('growth', '消息触达',   5, 'touch',       'message',       '消息模板与发送记录、触达记录'),
  ('growth', '闸门与监控', 6, 'guard',       'validCode',     'Agent 运行、熔断、合规词表')
) as v(parent_path, menu_name, order_num, path, icon, remark)
join sys_menu p on p.parent_id = 0 and p.path = v.parent_path and p.menu_type = 'M'
where not exists (select 1 from sys_menu m where m.parent_id = p.menu_id and m.path = v.path);

-- 3、页面归位（按权限标识找页面，按目录路径找新父目录）
update sys_menu m
set parent_id = d.menu_id, order_num = v.order_num, update_by = 'admin', update_time = now()
from (values
  ('abroad:lead:list',          'sales',       null::varchar, 1),
  ('abroad:booking:list',       'sales',       null,          2),
  ('abroad:order:list',         'sales',       null,          3),
  ('abroad:appUser:list',       'sales',       null,          4),
  ('abroad:referral:list',      'sales',       'referralOps', 1),
  ('abroad:partner:list',       'sales',       'referralOps', 2),
  ('abroad:leadAssign:list',    'sales',       'salesRule',   1),
  ('abroad:leadScore:list',     'sales',       'salesRule',   2),
  ('abroad:product:list',       'sales',       'salesRule',   3),
  ('abroad:exception:list',     'growth',      null,          1),
  ('abroad:attribution:list',   'growth',      'board',       1),
  ('abroad:growthReport:list',  'growth',      'board',       2),
  ('abroad:experiment:list',    'growth',      'board',       3),
  ('abroad:topic:list',         'growth',      'contentOps',  1),
  ('abroad:content:list',       'growth',      'contentOps',  2),
  ('abroad:publish:list',       'growth',      'contentOps',  3),
  ('abroad:account:list',       'growth',      'contentOps',  4),
  ('abroad:site:list',          'growth',      'contentOps',  5),
  ('abroad:case:list',          'growth',      'contentOps',  6),
  ('abroad:playbook:list',      'growth',      'automation',  1),
  ('abroad:calendar:list',      'growth',      'automation',  2),
  ('abroad:trigger:list',       'growth',      'automation',  3),
  ('abroad:nurture:list',       'growth',      'automation',  4),
  ('abroad:growthTask:list',    'growth',      'automation',  5),
  ('abroad:message:list',       'growth',      'touch',       1),
  ('abroad:touchLog:list',      'growth',      'touch',       2),
  ('abroad:growthAgent:list',   'growth',      'guard',       1),
  ('abroad:breaker:list',       'growth',      'guard',       2),
  ('abroad:compliance:list',    'growth',      'guard',       3)
) as v(perms, top_path, sub_path, order_num)
join sys_menu t on t.parent_id = 0 and t.path = v.top_path and t.menu_type = 'M'
join sys_menu d on d.menu_type = 'M'
               and ((v.sub_path is null and d.menu_id = t.menu_id) or (d.parent_id = t.menu_id and d.path = v.sub_path))
where m.perms = v.perms and m.menu_type = 'C'
  and (m.parent_id <> d.menu_id or m.order_num <> v.order_num);

-- 4、角色授权：拥有页面或按钮的角色补上全部上级目录
with recursive up(role_id, menu_id) as (
  select rm.role_id, m.parent_id
  from sys_role_menu rm join sys_menu m on m.menu_id = rm.menu_id
  where m.parent_id <> 0 and m.menu_type in ('C', 'F')
  union
  select u.role_id, m.parent_id from up u join sys_menu m on m.menu_id = u.menu_id where m.parent_id <> 0
)
insert into sys_role_menu (role_id, menu_id)
select distinct role_id, menu_id from up
on conflict do nothing;

-- 5、收回空目录：线索与成交、获客运营及其子目录下，角色已没有任何页面的，收回该目录
with recursive dirs(menu_id) as (
  select menu_id from sys_menu where parent_id = 0 and path in ('sales', 'growth') and menu_type = 'M'
  union
  select m.menu_id from sys_menu m join dirs d on m.parent_id = d.menu_id where m.menu_type = 'M'
),
below(dir_id, menu_id) as (
  select d.menu_id, m.menu_id from dirs d join sys_menu m on m.parent_id = d.menu_id
  union
  select b.dir_id, m.menu_id from below b join sys_menu m on m.parent_id = b.menu_id
)
delete from sys_role_menu rm
where rm.menu_id in (select menu_id from dirs)
  and not exists (
    select 1 from below b join sys_menu m on m.menu_id = b.menu_id and m.menu_type = 'C'
    join sys_role_menu x on x.menu_id = m.menu_id and x.role_id = rm.role_id
    where b.dir_id = rm.menu_id);

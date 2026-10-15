-- ----------------------------------------------------------------------------
-- M4-12 材料清单：材料类型字典、各国默认材料模板、项目申请要求的材料字段、学生的材料完成情况 + 菜单
-- 可重复执行（create if not exists / add column if not exists / where not exists）
-- 默认模板为 AI 草拟，需顾问按各国惯例确认（院校库项目写了材料要求时以项目为准）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、项目申请要求：材料（jsonb 数组：[{code, count, note}]，code 取字典 abr_material；为空时按国家默认模板）
-- ----------------------------
alter table abr_requirement add column if not exists materials jsonb default null;
comment on column abr_requirement.materials is '申请材料（jsonb 数组 [{code 材料类型（字典 abr_material）, count 份数, note 说明}]；为空时按国家默认模板）';

-- ----------------------------
-- 2、各国默认材料模板（项目没有写材料要求时使用）
-- ----------------------------
create table if not exists abr_material_template (
  template_id    bigserial      not null,
  country        varchar(8)     not null,
  degree         varchar(16)    not null,
  items          jsonb          not null,
  status         char(1)        default '0',
  create_by      varchar(64)    default '',
  create_time    timestamp,
  update_by      varchar(64)    default '',
  update_time    timestamp,
  remark         varchar(500)   default null,
  primary key (template_id)
);
create unique index if not exists uk_abr_material_template on abr_material_template (country, degree);

comment on table  abr_material_template             is '默认材料模板（每个「国家 / 地区 × 学位」一份；项目没有写材料要求时使用）';
comment on column abr_material_template.template_id is '模板ID';
comment on column abr_material_template.country     is '国家 / 地区（字典 abr_country）';
comment on column abr_material_template.degree      is '学位（字典 abr_degree）';
comment on column abr_material_template.items       is '材料（jsonb 数组 [{code 材料类型（字典 abr_material）, count 份数, note 说明}]）';
comment on column abr_material_template.status      is '状态（0正常 1停用）';
comment on column abr_material_template.create_by   is '创建者';
comment on column abr_material_template.create_time is '创建时间';
comment on column abr_material_template.update_by   is '更新者';
comment on column abr_material_template.update_time is '更新时间';
comment on column abr_material_template.remark      is '备注';

-- ----------------------------
-- 3、学生的材料完成情况（匿名按访客保存，登录后关联学生账号）
-- ----------------------------
create table if not exists abr_plan_material (
  plan_material_id bigserial    not null,
  visitor_id       bigint       not null,
  app_user_id      bigint       default null,
  material_key     varchar(64)  not null,
  done             boolean      not null default false,
  done_time        timestamp    default null,
  create_time      timestamp    default now(),
  update_time      timestamp    default now(),
  primary key (plan_material_id)
);
create unique index if not exists uk_abr_plan_material on abr_plan_material (visitor_id, material_key);

comment on table  abr_plan_material                  is '材料完成情况（选校单材料清单中学生勾选的完成状态）';
comment on column abr_plan_material.plan_material_id is '记录ID';
comment on column abr_plan_material.visitor_id       is '访客ID';
comment on column abr_plan_material.app_user_id      is '学生账号ID（M4-04 登录后关联）';
comment on column abr_plan_material.material_key     is '材料键（共用材料为类型编码，如 transcript；每个项目单独准备的为 类型:项目ID，如 ps:12）';
comment on column abr_plan_material.done             is '是否已完成';
comment on column abr_plan_material.done_time        is '完成时间';
comment on column abr_plan_material.create_time      is '创建时间';
comment on column abr_plan_material.update_time      is '更新时间';

-- ----------------------------
-- 4、字典：材料类型（css_class 为准备方式：shared 各项目共用一份 / program 每个项目单独准备）
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '申请材料类型', 'abr_material', '0', 'admin', now(), '材料清单的材料类型；样式属性 css_class：shared 各项目共用 / program 每个项目单独准备'
where not exists (select 1 from sys_dict_type t where t.dict_type = 'abr_material');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_material', v.css_class, 'default', 'N', '0', 'admin', now(), v.remark
from (values
  (1,  '成绩单',             'transcript',     'shared',  '本科中英文成绩单（加盖学校公章）'),
  (2,  '学位 / 在读证明',    'degree_cert',    'shared',  '毕业证、学位证或在读证明'),
  (3,  '语言成绩',           'language',       'shared',  '雅思 / 托福 / PTE / 多邻国 / 日语 / 韩语等'),
  (4,  'GRE / GMAT 成绩',    'gre_gmat',       'shared',  '标化考试成绩单'),
  (5,  '简历',               'cv',             'shared',  '学术或求职简历'),
  (6,  '个人陈述 / 文书',    'ps',             'program', '各项目题目与字数不同，通常需分别准备'),
  (7,  '附加文书',           'essay',          'program', '项目指定的短文题'),
  (8,  '推荐信',             'reference',      'shared',  '推荐人可在多个项目复用'),
  (9,  '作品集',             'portfolio',      'shared',  '设计、艺术、建筑等专业'),
  (10, '写作样本',           'writing_sample', 'shared',  '人文社科等专业'),
  (11, '研究计划书',         'research_plan',  'program', '日本、韩国及研究型项目'),
  (12, '学历认证',           'wes',            'shared',  'WES / ECE 等第三方学历认证'),
  (13, 'APS 证书',           'aps',            'shared',  '德国留学审核部'),
  (14, '护照',               'passport',       'shared',  '有效期覆盖入学时间'),
  (15, '面试',               'interview',      'program', '视频面试或在线面试'),
  (16, '申请费',             'fee',            'program', '网申缴费')
) as v(dict_sort, dict_label, dict_value, css_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_material' and d.dict_value = v.dict_value);

-- ----------------------------
-- 5、默认模板种子（硕士；AI 草拟，需顾问确认）
-- ----------------------------
insert into abr_material_template (country, degree, items, status, create_by, create_time, remark)
select v.country, 'master', cast(v.items as jsonb), '0', 'admin', now(), 'AI 草拟，需顾问确认'
from (values
  ('UK', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"cv"},{"code":"ps"},{"code":"reference","count":2},{"code":"passport"}]'),
  ('US', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"cv"},{"code":"ps"},{"code":"reference","count":3},{"code":"fee"},{"code":"passport"}]'),
  ('HK', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"cv"},{"code":"ps"},{"code":"reference","count":2},{"code":"fee"},{"code":"passport"}]'),
  ('SG', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"cv"},{"code":"ps"},{"code":"reference","count":2},{"code":"fee"},{"code":"passport"}]'),
  ('AU', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"cv"},{"code":"passport"}]'),
  ('JP', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"research_plan"},{"code":"reference","count":1},{"code":"fee"},{"code":"passport"}]'),
  ('KR', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"ps"},{"code":"research_plan"},{"code":"reference","count":2},{"code":"fee"},{"code":"passport"}]'),
  ('DE', '[{"code":"transcript"},{"code":"degree_cert"},{"code":"language"},{"code":"aps"},{"code":"cv"},{"code":"ps","note":"动机信"},{"code":"reference","count":2},{"code":"passport"}]')
) as v(country, items)
where not exists (select 1 from abr_material_template t where t.country = v.country and t.degree = 'master');

-- ----------------------------
-- 6、菜单：院校库 → 材料模板 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '材料模板', p.menu_id, 6, 'materialTemplate', 'abroad/materialTemplate/index', '', '', 1, 0, 'C', '0', '0', 'abroad:materialTemplate:list', 'documentation', 'admin', now(), '默认材料模板菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'catalog') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:materialTemplate:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('模板查询', 1, 'abroad:materialTemplate:query'),
  ('模板新增', 2, 'abroad:materialTemplate:add'),
  ('模板修改', 3, 'abroad:materialTemplate:edit'),
  ('模板删除', 4, 'abroad:materialTemplate:remove')
) as v(menu_name, order_num, perms)
join sys_menu c on c.perms = 'abroad:materialTemplate:list'
where not exists (select 1 from sys_menu m where m.perms = v.perms);

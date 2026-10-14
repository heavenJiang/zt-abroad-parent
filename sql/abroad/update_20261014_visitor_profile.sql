-- ----------------------------------------------------------------------------
-- M3-03 归一化：背景档案表 + 菜单（测评与选校 → 测评记录）
-- 文件名排在 update_20261014_questionnaire.sql（建“测评与选校”目录）之后
-- 可重复执行（create if not exists / where not exists）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、背景档案表（每个测评一份，重算时覆盖）
-- ----------------------------
create table if not exists abr_profile (
  profile_id               bigserial      not null,
  assessment_id            bigint         not null,
  visitor_id               bigint         not null,
  questionnaire_id         bigint         not null,
  undergrad_tier           varchar(32)    default null,
  undergrad_university_id  bigint         default null,
  gpa_percent              numeric(4,1)   default null,
  class_rank               varchar(16)    default null,
  major_relevance          varchar(16)    default null,
  english_status           varchar(16)    default null,
  ielts_equiv_total        numeric(3,1)   default null,
  ielts_equiv_min_sub      numeric(3,1)   default null,
  std_test_type            varchar(16)    default null,
  std_test_total           integer        default null,
  target_degree            varchar(16)    default null,
  target_field             varchar(32)    default null,
  target_field_group       varchar(32)    default null,
  target_countries         varchar(100)   default '',
  months_to_intake         integer        default null,
  profile                  jsonb          not null,
  warnings                 jsonb          default '[]'::jsonb,
  normalized_time          timestamp,
  create_by                varchar(64)    default '',
  create_time              timestamp,
  update_by                varchar(64)    default '',
  update_time              timestamp,
  remark                   varchar(500)   default null,
  primary key (profile_id)
);
create unique index if not exists uk_abr_profile_assessment on abr_profile (assessment_id);
create index if not exists idx_abr_profile_visitor on abr_profile (visitor_id);

comment on table  abr_profile                         is '背景档案表（问卷答案归一化后的标准字段，选校引擎的输入；每个测评一份，重算覆盖）';
comment on column abr_profile.profile_id              is '档案ID';
comment on column abr_profile.assessment_id           is '测评ID';
comment on column abr_profile.visitor_id              is '访客ID';
comment on column abr_profile.questionnaire_id        is '问卷版本ID（换算表来自该版本）';
comment on column abr_profile.undergrad_tier          is '本科院校层次（字典 abr_undergrad_tier；unknown 待核定）';
comment on column abr_profile.undergrad_university_id is '国内高校ID（abr_cn_university）';
comment on column abr_profile.gpa_percent             is '百分制均分（1 位小数）';
comment on column abr_profile.class_rank              is '专业排名（top10 / top30 / other）';
comment on column abr_profile.major_relevance         is '专业相关度（same / related / cross）';
comment on column abr_profile.english_status          is '英语考试情况（taken / estimated / none）';
comment on column abr_profile.ielts_equiv_total       is '雅思等效总分';
comment on column abr_profile.ielts_equiv_min_sub     is '雅思等效最低小分（v1 只对雅思直接取值）';
comment on column abr_profile.std_test_type           is '标化类型（gre / gmat_focus / gmat_classic / none）';
comment on column abr_profile.std_test_total          is '标化总分';
comment on column abr_profile.target_degree           is '目标学位';
comment on column abr_profile.target_field            is '目标专业方向';
comment on column abr_profile.target_field_group      is '目标专业大类';
comment on column abr_profile.target_countries        is '目标国家 / 地区（逗号分隔）';
comment on column abr_profile.months_to_intake        is '距入学月数（按 9 月入学估算）';
comment on column abr_profile.profile                 is '完整背景档案（jsonb，engine.BackgroundProfile）';
comment on column abr_profile.warnings                is '需要顾问注意的提示码（jsonb 数组）';
comment on column abr_profile.normalized_time         is '归一化时间';
comment on column abr_profile.create_by               is '创建者';
comment on column abr_profile.create_time             is '创建时间';
comment on column abr_profile.update_by               is '更新者';
comment on column abr_profile.update_time             is '更新时间';
comment on column abr_profile.remark                  is '备注';

-- ----------------------------
-- 2、字典：本科院校层次补“待核定”
-- ----------------------------
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time)
select 7, '待核定', 'unknown', 'abr_undergrad_tier', 'warning', 'N', '0', 'admin', now()
where exists (select 1 from sys_dict_type t where t.dict_type = 'abr_undergrad_tier')
  and not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_undergrad_tier' and d.dict_value = 'unknown');

-- ----------------------------
-- 3、菜单：测评与选校 → 测评记录 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '测评记录', p.menu_id, 2, 'assessment', 'abroad/assessment/index', '', '', 1, 0, 'C', '0', '0', 'abroad:assessment:list', 'list', 'admin', now(), '测评记录菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'assessment') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:assessment:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('测评查询',   1, 'abroad:assessment:query'),
  ('重新归一化', 2, 'abroad:assessment:normalize')
) as v(menu_name, order_num, perms)
join sys_menu c on c.perms = 'abroad:assessment:list'
where not exists (select 1 from sys_menu m where m.perms = v.perms);

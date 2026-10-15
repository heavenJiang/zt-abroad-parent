-- ----------------------------------------------------------------------------
-- M3-07 引擎配置与参考线管理：引擎配置版本表、项目参考线表、测评结果表 + 字典 + 菜单
-- （测评与选校 → 引擎配置 / 参考线；测评记录 → 计算选校结果按钮）
-- 可重复执行（create if not exists / where not exists）
-- 引擎参数种子（seed/engine-config/*.json）由 update_20261015_engine_seed.sql 导入为草稿
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、引擎配置版本表（每个「国家 / 地区 × 学位」一份完整 JSON，专业大类权重在 JSON 的 weights.by_field 中）
-- ----------------------------
create table if not exists abr_engine_config (
  config_id          bigserial      not null,
  config_code        varchar(64)    not null,
  country            varchar(8)     not null,
  degree             varchar(16)    not null,
  version            varchar(64)    not null,
  file_status        varchar(16)    default 'draft',
  publish_status     varchar(16)    default 'draft',
  config             jsonb          not null,
  warnings           jsonb          default null,
  activated_by       varchar(64)    default '',
  activated_time     timestamp,
  del_flag           char(1)        default '0',
  create_by          varchar(64)    default '',
  create_time        timestamp,
  update_by          varchar(64)    default '',
  update_time        timestamp,
  remark             varchar(500)   default null,
  primary key (config_id)
);
create unique index if not exists uk_abr_engine_config_version on abr_engine_config (config_code, version) where del_flag = '0';
create unique index if not exists uk_abr_engine_config_active on abr_engine_config (country, degree) where publish_status = 'active' and del_flag = '0';

comment on table  abr_engine_config                is '选校引擎配置版本表（权重、子分、硬门槛、参考线默认值等，完整 JSON；版本导入后不可修改）';
comment on column abr_engine_config.config_id      is '配置ID';
comment on column abr_engine_config.config_code    is '配置编码（JSON config_code，如 UK_MASTER）';
comment on column abr_engine_config.country        is '国家 / 地区（字典 abr_country）';
comment on column abr_engine_config.degree         is '学位（字典 abr_degree）';
comment on column abr_engine_config.version        is '版本（JSON version，如 UK_MASTER-2026.11.1）';
comment on column abr_engine_config.file_status    is '文件状态（JSON status：draft 草案 confirmed 顾问已确认）；只有 confirmed 可启用';
comment on column abr_engine_config.publish_status is '发布状态（字典 abr_engine_status：draft 草稿 active 启用 retired 已停用）；同一国家 × 学位只能有一个启用版本';
comment on column abr_engine_config.config         is '引擎配置（完整 JSON，格式见 seed/engine-config/README.md）';
comment on column abr_engine_config.warnings       is '导入时的警告（jsonb 数组，如确认后仍有“AI 草拟”说明）';
comment on column abr_engine_config.activated_by   is '启用人';
comment on column abr_engine_config.activated_time is '启用时间';
comment on column abr_engine_config.del_flag       is '删除标志（0存在 2删除）；只有草稿可删除';
comment on column abr_engine_config.create_by      is '创建者';
comment on column abr_engine_config.create_time    is '创建时间';
comment on column abr_engine_config.update_by      is '更新者';
comment on column abr_engine_config.update_time    is '更新时间';
comment on column abr_engine_config.remark         is '备注';

-- ----------------------------
-- 2、项目参考线表（每个项目一条当前参考线；没有时引擎按项目难度档取默认值）
-- ----------------------------
create table if not exists abr_program_benchmark (
  benchmark_id       bigserial      not null,
  program_id         bigint         not null,
  ref_line           numeric(5,2)   not null,
  stability          numeric(6,4)   default null,
  source             varchar(32)    not null,
  sample_size        integer        default null,
  del_flag           char(1)        default '0',
  create_by          varchar(64)    default '',
  create_time        timestamp,
  update_by          varchar(64)    default '',
  update_time        timestamp,
  remark             varchar(500)   default null,
  primary key (benchmark_id)
);
create unique index if not exists uk_abr_program_benchmark_program on abr_program_benchmark (program_id) where del_flag = '0';

comment on table  abr_program_benchmark             is '项目录取参考线表（参考线 T、稳定度 a 与来源）';
comment on column abr_program_benchmark.benchmark_id is '参考线ID';
comment on column abr_program_benchmark.program_id   is '项目ID';
comment on column abr_program_benchmark.ref_line     is '参考线 T（录取者的典型综合分，0–100）';
comment on column abr_program_benchmark.stability    is '稳定度 a（为空时用国家默认值）';
comment on column abr_program_benchmark.source       is '来源（字典 abr_benchmark_source：own_cases 工作室案例 official_stats 官方统计 consultant_difficulty 顾问定档）';
comment on column abr_program_benchmark.sample_size  is '样本数（来自案例或统计时填写）';
comment on column abr_program_benchmark.del_flag     is '删除标志（0存在 2删除）';
comment on column abr_program_benchmark.create_by    is '创建者';
comment on column abr_program_benchmark.create_time  is '创建时间';
comment on column abr_program_benchmark.update_by    is '更新者';
comment on column abr_program_benchmark.update_time  is '更新时间';
comment on column abr_program_benchmark.remark       is '备注（如依据、调整原因）';

-- ----------------------------
-- 3、测评结果表（每份测评一条，重算时覆盖）
-- ----------------------------
create table if not exists abr_assessment_result (
  result_id               bigserial      not null,
  assessment_id           bigint         not null,
  mode                    varchar(16)    not null,
  engine_versions         jsonb          default null,
  skipped_countries       varchar(100)   default '',
  program_count           integer        default 0,
  reach_count             integer        default 0,
  match_count             integer        default 0,
  safety_count            integer        default 0,
  needs_improvement_count integer        default 0,
  programs                jsonb          default null,
  portfolio               jsonb          default null,
  gaps                    jsonb          default null,
  hints                   jsonb          default null,
  notices                 jsonb          default null,
  sub_scores              jsonb          default null,
  computed_time           timestamp,
  create_by               varchar(64)    default '',
  create_time             timestamp,
  update_by               varchar(64)    default '',
  update_time             timestamp,
  primary key (result_id)
);
create unique index if not exists uk_abr_assessment_result_assessment on abr_assessment_result (assessment_id);

comment on table  abr_assessment_result                         is '测评选校结果表（引擎输出留痕；录取可能性 P 只给管理端）';
comment on column abr_assessment_result.result_id               is '结果ID';
comment on column abr_assessment_result.assessment_id           is '测评ID';
comment on column abr_assessment_result.mode                    is '模式（full 完整测评 / quick 快测）';
comment on column abr_assessment_result.engine_versions         is '所用引擎配置版本（jsonb：国家 → 版本）';
comment on column abr_assessment_result.skipped_countries       is '学生选了但没有启用配置、未计算的国家 / 地区（逗号分隔）';
comment on column abr_assessment_result.program_count           is '参与计算的项目数';
comment on column abr_assessment_result.reach_count             is '冲刺项目数';
comment on column abr_assessment_result.match_count             is '主攻项目数';
comment on column abr_assessment_result.safety_count            is '保底项目数';
comment on column abr_assessment_result.needs_improvement_count is '补足后可申项目数';
comment on column abr_assessment_result.programs                is '各项目结果（jsonb 数组：分组、档位、C、P、T 及来源、硬门槛明细、偏好分）';
comment on column abr_assessment_result.portfolio               is '推荐组合（jsonb：档位 → 项目ID 数组）';
comment on column abr_assessment_result.gaps                    is '差距反算（jsonb 数组）';
comment on column abr_assessment_result.hints                   is '提示（jsonb 数组）';
comment on column abr_assessment_result.notices                 is '需要向学生展示的提示码（jsonb 数组）';
comment on column abr_assessment_result.sub_scores              is '各国子分（jsonb：国家 → 维度 → 子分）';
comment on column abr_assessment_result.computed_time           is '计算时间';
comment on column abr_assessment_result.create_by               is '创建者';
comment on column abr_assessment_result.create_time             is '创建时间';
comment on column abr_assessment_result.update_by               is '更新者';
comment on column abr_assessment_result.update_time             is '更新时间';

-- ----------------------------
-- 4、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('引擎配置发布状态', 'abr_engine_status',     '选校引擎配置版本的发布状态'),
  ('参考线来源',       'abr_benchmark_source',  '项目录取参考线 T 的来源（可信度从高到低）'),
  ('选校分组',         'abr_selection_group',   '选校引擎对项目的分组')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now()
from (values
  (1, '草稿',       'draft',                 'abr_engine_status',    'info'),
  (2, '启用',       'active',                'abr_engine_status',    'success'),
  (3, '已停用',     'retired',               'abr_engine_status',    'default'),
  (1, '工作室案例', 'own_cases',             'abr_benchmark_source', 'success'),
  (2, '官方统计',   'official_stats',        'abr_benchmark_source', 'primary'),
  (3, '顾问定档',   'consultant_difficulty', 'abr_benchmark_source', 'info'),
  (1, '冲刺',       'reach',                 'abr_selection_group',  'danger'),
  (2, '主攻',       'match',                 'abr_selection_group',  'primary'),
  (3, '保底',       'safety',                'abr_selection_group',  'success'),
  (4, '待定档',     'unrated',               'abr_selection_group',  'info'),
  (5, '补足后可申', 'needs_improvement',     'abr_selection_group',  'warning'),
  (6, '不在考虑范围', 'excluded',            'abr_selection_group',  'default')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 5、菜单：测评与选校 → 引擎配置、参考线；测评记录 → 计算选校结果
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (values
  ('引擎配置', 3, 'engine',    'abroad/engine/index',    'abroad:engine:list',    'tool',  '引擎配置菜单'),
  ('参考线',   4, 'benchmark', 'abroad/benchmark/index', 'abroad:benchmark:list', 'chart', '参考线菜单')
) as v(menu_name, order_num, path, component, perms, icon, remark)
cross join (select menu_id from sys_menu where parent_id = 0 and path = 'assessment') p
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('配置查询',     1, 'abroad:engine:query',      'abroad:engine:list'),
  ('配置导入',     2, 'abroad:engine:import',     'abroad:engine:list'),
  ('配置启用',     3, 'abroad:engine:activate',   'abroad:engine:list'),
  ('配置删除',     4, 'abroad:engine:remove',     'abroad:engine:list'),
  ('引擎测试台',   5, 'abroad:engine:bench',      'abroad:engine:list'),
  ('参考线新增',   1, 'abroad:benchmark:add',     'abroad:benchmark:list'),
  ('参考线修改',   2, 'abroad:benchmark:edit',    'abroad:benchmark:list'),
  ('参考线删除',   3, 'abroad:benchmark:remove',  'abroad:benchmark:list'),
  ('计算选校结果', 3, 'abroad:assessment:compute', 'abroad:assessment:list')
) as v(menu_name, order_num, perms, parent_perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

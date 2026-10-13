-- ----------------------------------------------------------------------------
-- M2-01 / M2-03 院校库：院校、项目、申请要求、字段来源、国内高校层次表 + 字典 + 菜单
-- 可重复执行（create if not exists / where not exists / on conflict）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、院校表
-- ----------------------------
create table if not exists abr_school (
  school_id        bigserial      not null,
  school_name      varchar(200)   not null,
  school_name_en   varchar(200)   not null,
  country          varchar(8)     not null,
  city             varchar(100)   default '',
  rankings         jsonb          default '[]'::jsonb,
  moe_recognized   char(1)        default 'Y',
  website          varchar(500)   default '',
  status           char(1)        default '0',
  del_flag         char(1)        default '0',
  create_by        varchar(64)    default '',
  create_time      timestamp,
  update_by        varchar(64)    default '',
  update_time      timestamp,
  remark           varchar(500)   default null,
  primary key (school_id)
);
create unique index if not exists uk_abr_school_country_name_en on abr_school (country, school_name_en) where del_flag = '0';

comment on table  abr_school                is '院校表';
comment on column abr_school.school_id      is '院校ID';
comment on column abr_school.school_name    is '中文名';
comment on column abr_school.school_name_en is '英文名';
comment on column abr_school.country        is '国家（字典 abr_country）';
comment on column abr_school.city           is '城市';
comment on column abr_school.rankings       is '榜单名次（jsonb 数组：[{"list":"QS","year":2026,"rank":40}]）';
comment on column abr_school.moe_recognized is '是否在教育部认可名单（Y是 N否）';
comment on column abr_school.website        is '官网';
comment on column abr_school.status         is '状态（0正常 1停用）';
comment on column abr_school.del_flag       is '删除标志（0存在 2删除）';
comment on column abr_school.create_by      is '创建者';
comment on column abr_school.create_time    is '创建时间';
comment on column abr_school.update_by      is '更新者';
comment on column abr_school.update_time    is '更新时间';
comment on column abr_school.remark         is '备注';

-- ----------------------------
-- 2、项目表
-- ----------------------------
create table if not exists abr_program (
  program_id         bigserial      not null,
  school_id          bigint         not null,
  program_name       varchar(200)   default '',
  program_name_en    varchar(300)   not null,
  degree             varchar(16)    not null,
  field_group        varchar(32)    not null,
  major_direction    varchar(32)    default '',
  duration_months    integer        default null,
  intakes            varchar(64)    default '',
  tuition            numeric(12,2)  default null,
  currency           varchar(3)     default '',
  teaching_language  varchar(16)    default 'en',
  website            varchar(500)   default '',
  aps_required       char(1)        default 'N',
  difficulty         varchar(2)     default '',
  verified_by        varchar(64)    default '',
  verified_time      timestamp,
  status             char(1)        default '0',
  del_flag           char(1)        default '0',
  create_by          varchar(64)    default '',
  create_time        timestamp,
  update_by          varchar(64)    default '',
  update_time        timestamp,
  remark             varchar(500)   default null,
  primary key (program_id)
);
create unique index if not exists uk_abr_program_school_name_degree on abr_program (school_id, program_name_en, degree) where del_flag = '0';
create index if not exists idx_abr_program_school on abr_program (school_id);

comment on table  abr_program                   is '项目表';
comment on column abr_program.program_id        is '项目ID';
comment on column abr_program.school_id         is '院校ID';
comment on column abr_program.program_name      is '中文名';
comment on column abr_program.program_name_en   is '英文名';
comment on column abr_program.degree            is '学位（字典 abr_degree）';
comment on column abr_program.field_group       is '学科大类（字典 abr_field_group）';
comment on column abr_program.major_direction   is '专业方向（字典 abr_major_direction）';
comment on column abr_program.duration_months   is '学制（月）';
comment on column abr_program.intakes           is '入学季（字典 abr_intake，逗号分隔）';
comment on column abr_program.tuition           is '学费（每年）';
comment on column abr_program.currency          is '学费币种（ISO 4217，如 GBP）';
comment on column abr_program.teaching_language is '授课语言（字典 abr_teaching_language）';
comment on column abr_program.website           is '项目官网链接';
comment on column abr_program.aps_required      is '是否需要 APS（Y是 N否）';
comment on column abr_program.difficulty        is '难度档 D1–D5（字典 abr_program_difficulty，选校引擎冷启动参考线用）';
comment on column abr_program.verified_by       is '最近核对人';
comment on column abr_program.verified_time     is '最近核对时间（超过 90 天列表标黄）';
comment on column abr_program.status            is '状态（0正常 1停用）';
comment on column abr_program.del_flag          is '删除标志（0存在 2删除）';
comment on column abr_program.create_by         is '创建者';
comment on column abr_program.create_time       is '创建时间';
comment on column abr_program.update_by         is '更新者';
comment on column abr_program.update_time       is '更新时间';
comment on column abr_program.remark            is '备注';

-- ----------------------------
-- 3、申请要求表（按申请季）
-- ----------------------------
create table if not exists abr_requirement (
  requirement_id          bigserial      not null,
  program_id              bigint         not null,
  season                  varchar(16)    not null,
  min_gpa                 jsonb          default null,
  tiers_not_accepted      varchar(200)   default '',
  ielts_overall           numeric(3,1)   default null,
  ielts_min_sub           numeric(3,1)   default null,
  other_language          jsonb          default null,
  test_requirement        varchar(16)    default 'none',
  test_detail             jsonb          default null,
  prerequisites           varchar(1000)  default '',
  prerequisites_required  char(1)        default 'N',
  work_years              integer        default null,
  accept_cross_major      char(1)        default '2',
  deadlines               jsonb          default null,
  status                  char(1)        default '0',
  del_flag                char(1)        default '0',
  create_by               varchar(64)    default '',
  create_time             timestamp,
  update_by               varchar(64)    default '',
  update_time             timestamp,
  remark                  varchar(500)   default null,
  primary key (requirement_id)
);
create unique index if not exists uk_abr_requirement_program_season on abr_requirement (program_id, season) where del_flag = '0';

comment on table  abr_requirement                         is '项目申请要求表（按申请季）';
comment on column abr_requirement.requirement_id          is '要求ID';
comment on column abr_requirement.program_id              is '项目ID';
comment on column abr_requirement.season                  is '申请季（如 2027-fall）';
comment on column abr_requirement.min_gpa                 is '按本科层次的最低均分（百分制，jsonb：{"c9":80,"985":82,"211":85,"double_first_class":85,"non_key":88,"overseas":75}）';
comment on column abr_requirement.tiers_not_accepted      is '不接受的本科层次（字典 abr_undergrad_tier，逗号分隔）';
comment on column abr_requirement.ielts_overall           is '雅思总分要求';
comment on column abr_requirement.ielts_min_sub           is '雅思最低小分要求';
comment on column abr_requirement.other_language          is '其他语言要求（jsonb：托福、PTE、多邻国、JLPT、TOPIK、德语等）';
comment on column abr_requirement.test_requirement        is 'GRE/GMAT 要求（字典 abr_test_requirement）';
comment on column abr_requirement.test_detail             is '标化分数要求（jsonb：{"gre":320,"gmat_focus":645}）';
comment on column abr_requirement.prerequisites           is '先修课要求';
comment on column abr_requirement.prerequisites_required  is '先修课是否硬性要求（Y是 N否）';
comment on column abr_requirement.work_years              is '工作年限要求（年）';
comment on column abr_requirement.accept_cross_major      is '是否接受跨专业（字典 abr_cross_major：0不接受 1接受 2未知）';
comment on column abr_requirement.deadlines               is '截止日（jsonb：{"rolling":false,"rounds":[{"name":"R1","date":"2027-01-15"}]}）';
comment on column abr_requirement.status                  is '状态（0正常 1停用）';
comment on column abr_requirement.del_flag                is '删除标志（0存在 2删除）';
comment on column abr_requirement.create_by               is '创建者';
comment on column abr_requirement.create_time             is '创建时间';
comment on column abr_requirement.update_by               is '更新者';
comment on column abr_requirement.update_time             is '更新时间';
comment on column abr_requirement.remark                  is '备注';

-- ----------------------------
-- 4、字段来源表（每条记录每个字段保留最新来源）
-- ----------------------------
create table if not exists abr_field_source (
  source_id      bigserial      not null,
  table_name     varchar(64)    not null,
  record_id      bigint         not null,
  field_name     varchar(64)    not null,
  source_url     varchar(1000)  not null,
  quote          text           default null,
  origin         char(1)        default '0',
  verified_by    varchar(64)    default '',
  verified_time  timestamp,
  create_by      varchar(64)    default '',
  create_time    timestamp,
  update_by      varchar(64)    default '',
  update_time    timestamp,
  primary key (source_id),
  unique (table_name, record_id, field_name)
);

comment on table  abr_field_source               is '字段来源表';
comment on column abr_field_source.source_id     is '来源ID';
comment on column abr_field_source.table_name    is '记录所在表（abr_program / abr_requirement）';
comment on column abr_field_source.record_id     is '记录ID';
comment on column abr_field_source.field_name    is '字段名（Java 属性名，如 minGpa）';
comment on column abr_field_source.source_url    is '来源 URL';
comment on column abr_field_source.quote         is '原文引句';
comment on column abr_field_source.origin        is '来源方式（字典 abr_source_origin：0人工 1AI预填）';
comment on column abr_field_source.verified_by   is '核对人';
comment on column abr_field_source.verified_time is '核对时间';
comment on column abr_field_source.create_by     is '创建者';
comment on column abr_field_source.create_time   is '创建时间';
comment on column abr_field_source.update_by     is '更新者';
comment on column abr_field_source.update_time   is '更新时间';

-- ----------------------------
-- 5、国内高校层次表（M2-03，种子数据见 update_20261013_cn_university_seed.sql）
-- ----------------------------
create table if not exists abr_cn_university (
  cn_university_id       bigserial      not null,
  university_name        varchar(100)   not null,
  aliases                varchar(500)   default '',
  province               varchar(32)    default '',
  city                   varchar(32)    default '',
  is_c9                  char(1)        default 'N',
  is_985                 char(1)        default 'N',
  is_211                 char(1)        default 'N',
  is_double_first_class  char(1)        default 'N',
  tier                   varchar(32)    not null,
  campus_type            char(1)        default '0',
  source                 varchar(500)   default '',
  note                   varchar(500)   default '',
  status                 char(1)        default '0',
  create_by              varchar(64)    default '',
  create_time            timestamp,
  update_by              varchar(64)    default '',
  update_time            timestamp,
  remark                 varchar(500)   default null,
  primary key (cn_university_id),
  unique (university_name)
);

comment on table  abr_cn_university                       is '国内高校层次表';
comment on column abr_cn_university.cn_university_id      is '高校ID';
comment on column abr_cn_university.university_name       is '高校全称';
comment on column abr_cn_university.aliases               is '别名（简称、曾用名，| 分隔）';
comment on column abr_cn_university.province              is '省份';
comment on column abr_cn_university.city                  is '城市';
comment on column abr_cn_university.is_c9                 is '是否 C9（Y是 N否）';
comment on column abr_cn_university.is_985                is '是否 985（Y是 N否）';
comment on column abr_cn_university.is_211                is '是否 211（Y是 N否）';
comment on column abr_cn_university.is_double_first_class is '是否双一流（Y是 N否）';
comment on column abr_cn_university.tier                  is '层次（字典 abr_undergrad_tier，取最高：c9 > 985 > 211 > double_first_class）';
comment on column abr_cn_university.campus_type           is '校区类型（0校本部 1分校区）';
comment on column abr_cn_university.source                is '数据来源';
comment on column abr_cn_university.note                  is '说明';
comment on column abr_cn_university.status                is '状态（0正常 1停用）';
comment on column abr_cn_university.create_by             is '创建者';
comment on column abr_cn_university.create_time           is '创建时间';
comment on column abr_cn_university.update_by             is '更新者';
comment on column abr_cn_university.update_time           is '更新时间';
comment on column abr_cn_university.remark                is '备注';

-- ----------------------------
-- 6、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('留学国家',     'abr_country',            '院校所在国家 / 地区'),
  ('学位',         'abr_degree',             '项目学位'),
  ('学科大类',     'abr_field_group',        '与选校引擎、问卷的学科大类一致'),
  ('专业方向',     'abr_major_direction',    '与问卷 target_field 选项一致'),
  ('授课语言',     'abr_teaching_language',  '项目授课语言'),
  ('入学季',       'abr_intake',             '项目入学季'),
  ('项目难度档',   'abr_program_difficulty', '选校引擎冷启动参考线 D1–D5'),
  ('标化要求',     'abr_test_requirement',   'GRE / GMAT 要求'),
  ('本科层次',     'abr_undergrad_tier',     '与问卷、选校引擎的本科层次编码一致'),
  ('跨专业',       'abr_cross_major',        '项目是否接受跨专业'),
  ('来源方式',     'abr_source_origin',      '字段来源由人工录入还是 AI 预填')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now()
from (values
  (1, '美国',     'US', 'abr_country', 'default'),
  (2, '英国',     'UK', 'abr_country', 'default'),
  (3, '澳大利亚', 'AU', 'abr_country', 'default'),
  (4, '新加坡',   'SG', 'abr_country', 'default'),
  (5, '日本',     'JP', 'abr_country', 'default'),
  (6, '韩国',     'KR', 'abr_country', 'default'),
  (7, '德国',     'DE', 'abr_country', 'default'),
  (1, '硕士', 'master',   'abr_degree', 'primary'),
  (2, '博士', 'phd',      'abr_degree', 'warning'),
  (3, '本科', 'bachelor', 'abr_degree', 'info'),
  (1, '商科',       'business',          'abr_field_group', 'default'),
  (2, '理工科',     'stem',              'abr_field_group', 'default'),
  (3, '医学健康',   'health',            'abr_field_group', 'default'),
  (4, '人文社科',   'humanities_social', 'abr_field_group', 'default'),
  (5, '艺术设计',   'arts',              'abr_field_group', 'default'),
  (1,  '金融 / 金融工程',                 'finance',            'abr_major_direction', 'default'),
  (2,  '会计 / 审计',                     'accounting',         'abr_major_direction', 'default'),
  (3,  '管理 / 市场营销',                 'management',         'abr_major_direction', 'default'),
  (4,  '商业分析',                        'business_analytics', 'abr_major_direction', 'default'),
  (5,  '经济学',                          'economics',          'abr_major_direction', 'default'),
  (6,  '计算机 / 软件',                   'cs',                 'abr_major_direction', 'default'),
  (7,  '数据科学 / 人工智能',             'data_ai',            'abr_major_direction', 'default'),
  (8,  '电子电气 / 通信',                 'ee',                 'abr_major_direction', 'default'),
  (9,  '机械 / 土木 / 化工等工科',        'engineering',        'abr_major_direction', 'default'),
  (10, '数学 / 统计 / 理科',              'science',            'abr_major_direction', 'default'),
  (11, '医学 / 公共卫生 / 药学',          'health',             'abr_major_direction', 'default'),
  (12, '社会科学 / 公共政策 / 国际关系',  'social_science',     'abr_major_direction', 'default'),
  (13, '教育',                            'education',          'abr_major_direction', 'default'),
  (14, '传媒 / 新闻 / 传播',              'media',              'abr_major_direction', 'default'),
  (15, '法律',                            'law',                'abr_major_direction', 'default'),
  (16, '语言 / 文学 / 翻译 / 历史哲学',   'humanities',         'abr_major_direction', 'default'),
  (17, '艺术 / 设计 / 建筑',              'arts_design',        'abr_major_direction', 'default'),
  (1, '英语',   'en',    'abr_teaching_language', 'default'),
  (2, '日语',   'ja',    'abr_teaching_language', 'default'),
  (3, '韩语',   'ko',    'abr_teaching_language', 'default'),
  (4, '德语',   'de',    'abr_teaching_language', 'default'),
  (5, '中文',   'zh',    'abr_teaching_language', 'default'),
  (6, '其他',   'other', 'abr_teaching_language', 'default'),
  (1, '秋季', 'fall',   'abr_intake', 'default'),
  (2, '春季', 'spring', 'abr_intake', 'default'),
  (3, '夏季', 'summer', 'abr_intake', 'default'),
  (4, '冬季', 'winter', 'abr_intake', 'default'),
  (1, 'D1 顶尖', 'D1', 'abr_program_difficulty', 'danger'),
  (2, 'D2 很难', 'D2', 'abr_program_difficulty', 'warning'),
  (3, 'D3 较难', 'D3', 'abr_program_difficulty', 'primary'),
  (4, 'D4 中等', 'D4', 'abr_program_difficulty', 'info'),
  (5, 'D5 较易', 'D5', 'abr_program_difficulty', 'success'),
  (1, '不要求', 'none',     'abr_test_requirement', 'info'),
  (2, '可选',   'optional', 'abr_test_requirement', 'primary'),
  (3, '必需',   'required', 'abr_test_requirement', 'warning'),
  (1, 'C9',     'c9',                 'abr_undergrad_tier', 'danger'),
  (2, '985',    '985',                'abr_undergrad_tier', 'warning'),
  (3, '211',    '211',                'abr_undergrad_tier', 'primary'),
  (4, '双一流', 'double_first_class', 'abr_undergrad_tier', 'success'),
  (5, '双非',   'non_key',            'abr_undergrad_tier', 'info'),
  (6, '海本',   'overseas',           'abr_undergrad_tier', 'default'),
  (1, '不接受', '0', 'abr_cross_major', 'danger'),
  (2, '接受',   '1', 'abr_cross_major', 'success'),
  (3, '未知',   '2', 'abr_cross_major', 'info'),
  (1, '人工',    '0', 'abr_source_origin', 'default'),
  (2, 'AI 预填', '1', 'abr_source_origin', 'warning')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 7、菜单：院校库（目录）→ 院校 / 项目 / 国内高校 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '院校库', 0, 10, 'catalog', null, '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', now(), '留学业务：院校库'
where not exists (select 1 from sys_menu where parent_id = 0 and path = 'catalog');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, p.menu_id, v.order_num, v.path, v.component, '', '', 1, 0, 'C', '0', '0', v.perms, v.icon, 'admin', now(), v.remark
from (values
  ('院校',     1, 'school',       'abroad/school/index',       'abroad:school:list',       'build',   '院校菜单'),
  ('项目',     2, 'program',      'abroad/program/index',      'abroad:program:list',      'list',    '项目菜单'),
  ('国内高校', 3, 'cnUniversity', 'abroad/cnUniversity/index', 'abroad:cnUniversity:list', 'tree',    '国内高校层次菜单')
) as v(menu_name, order_num, path, component, perms, icon, remark)
cross join (select menu_id from sys_menu where parent_id = 0 and path = 'catalog') p
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:school:list',       '院校查询', 1, 'abroad:school:query'),
  ('abroad:school:list',       '院校新增', 2, 'abroad:school:add'),
  ('abroad:school:list',       '院校修改', 3, 'abroad:school:edit'),
  ('abroad:school:list',       '院校删除', 4, 'abroad:school:remove'),
  ('abroad:school:list',       '院校导出', 5, 'abroad:school:export'),
  ('abroad:program:list',      '项目查询', 1, 'abroad:program:query'),
  ('abroad:program:list',      '项目新增', 2, 'abroad:program:add'),
  ('abroad:program:list',      '项目修改', 3, 'abroad:program:edit'),
  ('abroad:program:list',      '项目删除', 4, 'abroad:program:remove'),
  ('abroad:program:list',      '项目导出', 5, 'abroad:program:export'),
  ('abroad:cnUniversity:list', '高校查询', 1, 'abroad:cnUniversity:query'),
  ('abroad:cnUniversity:list', '高校新增', 2, 'abroad:cnUniversity:add'),
  ('abroad:cnUniversity:list', '高校修改', 3, 'abroad:cnUniversity:edit'),
  ('abroad:cnUniversity:list', '高校删除', 4, 'abroad:cnUniversity:remove'),
  ('abroad:cnUniversity:list', '高校导出', 5, 'abroad:cnUniversity:export')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

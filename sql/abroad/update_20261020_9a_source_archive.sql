-- ----------------------------------------------------------------------------
-- M2-09 源信息存档与字段历史
--
-- - abr_source_snapshot：每次抓取网页、调用官方接口、上传文件得到的原始依据（正文或接口返回、内容哈希、抓取时间、最终网址、HTTP 状态）。
--   同一网址内容不变时复用同一份存档，只更新“最近一次看到”的时间；最近一次看到超过 2 年的存档由定时任务
--   abrSourceTask.purgeExpired() 每天清理，当前在用值（abr_field_source）引用的存档不清理。
--   存档只在管理后台查看，不对外展示。
-- - abr_field_source 新增 snapshot_id：字段来源指向存档，可查看当时的原文并高亮引句。
-- - abr_field_history：院校、项目、申请要求的每次字段变化（旧值、新值、依据存档、生效方式、操作人），可一键回滚。
-- - abr_school 新增 aliases（别名，用于学校识别）、ext_ids（外部编号，如美国 IPEDS 编号）。
-- 依赖：update_20261013_catalog.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_source_snapshot (
  snapshot_id     bigserial      not null,
  source_url      varchar(1000)  not null,
  final_url       varchar(1000)  default '',
  source_kind     varchar(16)    not null default 'web',
  http_status     integer        default null,
  content_type    varchar(100)   default '',
  content_hash    varchar(64)    not null,
  content         text           default null,
  content_size    integer        default 0,
  title           varchar(300)   default '',
  first_seen_time timestamp      default now(),
  last_seen_time  timestamp      default now(),
  seen_count      integer        default 1,
  fetched_by      varchar(64)    default '',
  remark          varchar(500)   default null,
  primary key (snapshot_id)
);
create unique index if not exists uk_abr_source_snapshot_url_hash on abr_source_snapshot (source_url, content_hash);
create index if not exists idx_abr_source_snapshot_seen on abr_source_snapshot (last_seen_time);

comment on table  abr_source_snapshot                 is '源信息存档（网页正文、官方接口返回、上传文件的文字；只在管理后台查看，最近一次看到超过 2 年且不再被引用时清理）';
comment on column abr_source_snapshot.snapshot_id     is '存档ID';
comment on column abr_source_snapshot.source_url      is '请求的网址（接口地址不含密钥）';
comment on column abr_source_snapshot.final_url       is '重定向后的最终网址';
comment on column abr_source_snapshot.source_kind     is '来源类型（字典 abr_source_kind：web 网页 / api 官方接口 / file 上传文件）';
comment on column abr_source_snapshot.http_status     is 'HTTP 状态';
comment on column abr_source_snapshot.content_type    is '内容类型';
comment on column abr_source_snapshot.content_hash    is '内容 SHA-256';
comment on column abr_source_snapshot.content         is '内容（网页转纯文本、接口返回 JSON、文件抽取的文字）';
comment on column abr_source_snapshot.content_size    is '内容字符数';
comment on column abr_source_snapshot.title           is '页面标题或说明';
comment on column abr_source_snapshot.first_seen_time is '第一次抓到这份内容的时间';
comment on column abr_source_snapshot.last_seen_time  is '最近一次抓到同样内容的时间（保留期从这里算）';
comment on column abr_source_snapshot.seen_count      is '抓到同样内容的次数';
comment on column abr_source_snapshot.fetched_by      is '抓取者（Agent 或员工）';
comment on column abr_source_snapshot.remark          is '备注';

alter table abr_field_source add column if not exists snapshot_id bigint default null;
comment on column abr_field_source.snapshot_id is '依据的存档（abr_source_snapshot）';
comment on column abr_field_source.table_name is '记录所在表（abr_school / abr_program / abr_requirement）';

create table if not exists abr_field_history (
  history_id   bigserial      not null,
  table_name   varchar(64)    not null,
  record_id    bigint         not null,
  field_name   varchar(64)    not null,
  old_value    text           default null,
  new_value    text           default null,
  snapshot_id  bigint         default null,
  source_url   varchar(1000)  default '',
  quote        varchar(2000)  default '',
  change_mode  varchar(16)    not null default 'manual',
  status       varchar(16)    not null default 'applied',
  operator     varchar(64)    default '',
  create_time  timestamp      default now(),
  primary key (history_id)
);
create index if not exists idx_abr_field_history_record on abr_field_history (table_name, record_id, history_id desc);

comment on table  abr_field_history             is '院校库字段变化历史（可回滚）';
comment on column abr_field_history.history_id  is 'ID';
comment on column abr_field_history.table_name  is '表（abr_school / abr_program / abr_requirement）';
comment on column abr_field_history.record_id   is '记录ID';
comment on column abr_field_history.field_name  is '字段（Java 属性名）';
comment on column abr_field_history.old_value   is '旧值（文本）';
comment on column abr_field_history.new_value   is '新值（文本）';
comment on column abr_field_history.snapshot_id is '依据的存档（存档清理后仍保留其余信息）';
comment on column abr_field_history.source_url  is '来源网址';
comment on column abr_field_history.quote       is '原文引句';
comment on column abr_field_history.change_mode is '生效方式（字典 abr_change_mode：manual 人工 / ai_prefill AI 预填 / import 导入 / auto 自动 / veto_window 否决窗口后自动 / rollback 回滚）';
comment on column abr_field_history.status      is '状态（applied 已生效 / rolled_back 已回滚）';
comment on column abr_field_history.operator    is '操作人或 Agent';
comment on column abr_field_history.create_time is '变化时间';

alter table abr_school add column if not exists aliases jsonb not null default '[]'::jsonb;
alter table abr_school add column if not exists ext_ids jsonb not null default '{}'::jsonb;
comment on column abr_school.aliases is '别名（中英文简称、旧名，用于学校识别）';
comment on column abr_school.ext_ids is '外部编号（如 {"ipeds": "166027"}，美国院校的 IPEDS 编号）';

-- ----------------------------
-- 字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('来源类型', 'abr_source_kind'), ('字段生效方式', 'abr_change_mode')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '网页',             'web',         'abr_source_kind', 'primary'),
  (2, '官方接口',         'api',         'abr_source_kind', 'success'),
  (3, '上传文件',         'file',        'abr_source_kind', 'info'),
  (1, '人工',             'manual',      'abr_change_mode', 'info'),
  (2, 'AI 预填',          'ai_prefill',  'abr_change_mode', 'primary'),
  (3, '导入',             'import',      'abr_change_mode', 'primary'),
  (4, '自动',             'auto',        'abr_change_mode', 'success'),
  (5, '否决窗口后自动',   'veto_window', 'abr_change_mode', 'warning'),
  (6, '回滚',             'rollback',    'abr_change_mode', 'danger'),
  (3, '自动采集',         '2',           'abr_source_origin', 'success')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

comment on column abr_field_source.origin is '来源方式（字典 abr_source_origin：0 人工 1 AI 预填 2 自动采集）';

-- ----------------------------
-- 按钮：院校库下的来源查看与回滚（挂在院校菜单下）
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), v.remark
from (select menu_id from sys_menu where perms = 'abroad:school:list') c
cross join (values ('来源与历史查看', 90, 'abroad:source:query', '查看存档原文与字段历史'),
                   ('字段回滚', 91, 'abroad:source:rollback', '把字段恢复为历史值')) as v(menu_name, order_num, perms, remark)
where not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms in ('abroad:source:query', 'abroad:source:rollback')
where r.role_key in ('abr_owner', 'abr_editor') and r.del_flag = '0'
on conflict do nothing;

-- ----------------------------
-- 定时任务：存档清理
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '源信息存档清理', 'DEFAULT', 'abrSourceTask.purgeExpired()', '0 10 4 * * ?', '3', '1', '0', 'admin', now(),
       'M2-09：删除最近一次看到超过 2 年、且不再被当前字段来源引用的存档'
where not exists (select 1 from sys_job where invoke_target = 'abrSourceTask.purgeExpired()');

-- ----------------------------------------------------------------------------
-- M5-04 线索列表、详情、手动跟进与阶段（后端）
--
-- - abr_lead_follow：员工的跟进记录（方式、内容、下次联系时间）、阶段变更（流失须填原因）、转给同事；进入线索时间线。
-- - abr_lead：下次联系时间、最近跟进时间、阶段变更时间、流失原因。
-- - 列表默认按 意向度 → 下次联系时间 → 最近触达 排序；可按负责人、“我的”、“到期待联系”筛选。
-- 依赖：update_20261019_lead.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、线索：跟进字段
-- ----------------------------
alter table abr_lead add column if not exists next_follow_time timestamp    default null;
alter table abr_lead add column if not exists last_follow_time timestamp    default null;
alter table abr_lead add column if not exists stage_time       timestamp    default null;
alter table abr_lead add column if not exists lost_reason      varchar(500) default '';
create index if not exists idx_abr_lead_owner on abr_lead (owner_user_id, next_follow_time) where merged_into is null;

comment on column abr_lead.next_follow_time is '下次联系时间（顾问记跟进时填写）';
comment on column abr_lead.last_follow_time is '最近跟进时间';
comment on column abr_lead.stage_time       is '进入当前阶段的时间';
comment on column abr_lead.lost_reason      is '流失原因（阶段为流失时必填）';

-- ----------------------------
-- 2、跟进记录
-- ----------------------------
create table if not exists abr_lead_follow (
  follow_id        bigserial      not null,
  lead_id          bigint         not null,
  follow_kind      varchar(16)    not null default 'follow',
  method           varchar(16)    default '',
  content          varchar(2000)  default '',
  next_follow_time timestamp      default null,
  stage_from       varchar(16)    default null,
  stage_to         varchar(16)    default null,
  owner_from       bigint         default null,
  owner_to         bigint         default null,
  create_by        varchar(64)    default '',
  create_time      timestamp      default now(),
  primary key (follow_id)
);
create index if not exists idx_abr_lead_follow_lead on abr_lead_follow (lead_id, follow_id desc);

comment on table  abr_lead_follow                  is '线索跟进记录（员工跟进、阶段变更、转给同事；进入时间线）';
comment on column abr_lead_follow.follow_id        is '记录ID';
comment on column abr_lead_follow.lead_id          is '线索ID';
comment on column abr_lead_follow.follow_kind      is '类型（字典 abr_follow_kind：follow 跟进 / stage 阶段变更 / assign 转给同事）';
comment on column abr_lead_follow.method           is '跟进方式（字典 abr_follow_method）';
comment on column abr_lead_follow.content          is '内容（内部记录，学生不可见）';
comment on column abr_lead_follow.next_follow_time is '约定的下次联系时间';
comment on column abr_lead_follow.stage_from       is '原阶段';
comment on column abr_lead_follow.stage_to         is '新阶段';
comment on column abr_lead_follow.owner_from       is '原负责人';
comment on column abr_lead_follow.owner_to         is '新负责人';
comment on column abr_lead_follow.create_by        is '记录人';
comment on column abr_lead_follow.create_time      is '记录时间';

-- ----------------------------
-- 3、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('跟进方式',     'abr_follow_method', '员工跟进线索的方式'),
  ('跟进记录类型', 'abr_follow_kind',   '线索跟进记录的类型')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1, '电话',       'phone',   'abr_follow_method', 'primary', ''),
  (2, '企业微信',   'wecom',   'abr_follow_method', 'success', ''),
  (3, '微信',       'wechat',  'abr_follow_method', 'success', ''),
  (4, '线上会议',   'meeting', 'abr_follow_method', 'info',    ''),
  (5, '到访',       'visit',   'abr_follow_method', 'warning', ''),
  (6, '其他',       'other',   'abr_follow_method', 'default', ''),
  (1, '跟进',       'follow',  'abr_follow_kind',   'primary', ''),
  (2, '阶段变更',   'stage',   'abr_follow_kind',   'warning', ''),
  (3, '转给同事',   'assign',  'abr_follow_kind',   'info',    '')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 4、按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:lead:list', '记跟进',     7, 'abroad:lead:follow'),
  ('abroad:lead:list', '改阶段',     8, 'abroad:lead:stage'),
  ('abroad:lead:list', '转给同事',   9, 'abroad:lead:assign'),
  ('abroad:lead:list', '修改线索',  10, 'abroad:lead:edit')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

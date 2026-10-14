-- ----------------------------------------------------------------------------
-- M3-02 访客档案与测评记录：匿名可用、分步保存、断点续答；入口工具与完整测评共用访客档案
-- 可重复执行（create if not exists / where not exists）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、访客表（C 端匿名身份 + 渐进档案）
-- ----------------------------
create table if not exists abr_visitor (
  visitor_id        bigserial      not null,
  token_hash        varchar(64)    not null,
  channel_code      varchar(32)    default 'direct',
  content_code      varchar(32)    default '',
  client_platform   varchar(16)    default '',
  profile           jsonb          default '{}'::jsonb,
  app_user_id       bigint         default null,
  last_seen_time    timestamp,
  del_flag          char(1)        default '0',
  create_by         varchar(64)    default '',
  create_time       timestamp,
  update_by         varchar(64)    default '',
  update_time       timestamp,
  remark            varchar(500)   default null,
  primary key (visitor_id)
);
create unique index if not exists uk_abr_visitor_token_hash on abr_visitor (token_hash);
create index if not exists idx_abr_visitor_app_user on abr_visitor (app_user_id);

comment on table  abr_visitor                 is '访客表（C 端匿名身份；访客令牌只存哈希；登录后关联学生账号）';
comment on column abr_visitor.visitor_id      is '访客ID';
comment on column abr_visitor.token_hash      is '访客令牌 SHA-256（十六进制），令牌明文只返回给前端一次';
comment on column abr_visitor.channel_code    is '首次进入的渠道码';
comment on column abr_visitor.content_code    is '首次进入的内容编号';
comment on column abr_visitor.client_platform is '首次进入的端（h5 / mp_weixin）';
comment on column abr_visitor.profile         is '渐进档案（jsonb：题目 id → 最近一次答案），入口工具与测评共用，新测评据此预填';
comment on column abr_visitor.app_user_id     is '学生账号ID（登录后关联，M4-04）';
comment on column abr_visitor.last_seen_time  is '最近访问时间';
comment on column abr_visitor.del_flag        is '删除标志（0存在 2删除）';
comment on column abr_visitor.create_by       is '创建者';
comment on column abr_visitor.create_time     is '创建时间';
comment on column abr_visitor.update_by       is '更新者';
comment on column abr_visitor.update_time     is '更新时间';
comment on column abr_visitor.remark          is '备注';

-- ----------------------------
-- 2、测评表
-- ----------------------------
create table if not exists abr_assessment (
  assessment_id          bigserial      not null,
  visitor_id             bigint         not null,
  questionnaire_id       bigint         not null,
  questionnaire_code     varchar(64)    not null,
  questionnaire_version  integer        not null,
  request_id             varchar(64)    not null,
  channel_code           varchar(32)    default 'direct',
  client_platform        varchar(16)    default '',
  policy_code            varchar(64)    default '',
  policy_version         varchar(32)    default '',
  consent_time           timestamp,
  assessment_status      varchar(16)    default 'in_progress',
  answers                jsonb          default '{}'::jsonb,
  flags                  jsonb          default '{}'::jsonb,
  is_minor               char(1)        default 'N',
  current_screen         varchar(64)    default '',
  submitted_time         timestamp,
  del_flag               char(1)        default '0',
  create_by              varchar(64)    default '',
  create_time            timestamp,
  update_by              varchar(64)    default '',
  update_time            timestamp,
  remark                 varchar(500)   default null,
  primary key (assessment_id)
);
create unique index if not exists uk_abr_assessment_visitor_request on abr_assessment (visitor_id, request_id);
create index if not exists idx_abr_assessment_visitor on abr_assessment (visitor_id, assessment_status);

comment on table  abr_assessment                       is '测评表（匿名创建，分步保存答案，断点续答）';
comment on column abr_assessment.assessment_id         is '测评ID';
comment on column abr_assessment.visitor_id            is '访客ID';
comment on column abr_assessment.questionnaire_id      is '问卷版本ID（创建时的启用版本，之后不变）';
comment on column abr_assessment.questionnaire_code    is '问卷编码';
comment on column abr_assessment.questionnaire_version is '问卷版本号';
comment on column abr_assessment.request_id            is '前端生成的请求ID（同一访客内幂等创建）';
comment on column abr_assessment.channel_code          is '渠道码';
comment on column abr_assessment.client_platform       is '答题端（h5 / mp_weixin）';
comment on column abr_assessment.policy_code           is '同意的隐私政策编码';
comment on column abr_assessment.policy_version        is '同意的隐私政策版本';
comment on column abr_assessment.consent_time          is '同意时间';
comment on column abr_assessment.assessment_status     is '测评状态（字典 abr_assessment_status：in_progress 答题中 terminated 已终止 submitted 已提交）';
comment on column abr_assessment.answers               is '答案（jsonb：题目 id → 答案）；隐藏题的答案保留在草稿中，提交时剔除';
comment on column abr_assessment.flags                 is '流程标志（jsonb，来自问卷 on_answer.flags，如 is_minor、exclude_from_referral_unlock）';
comment on column abr_assessment.is_minor              is '是否未成年（Y是 N否，来自 flags.is_minor）';
comment on column abr_assessment.current_screen        is '断点：最近保存时所在的屏 id';
comment on column abr_assessment.submitted_time        is '提交时间';
comment on column abr_assessment.del_flag              is '删除标志（0存在 2删除）';
comment on column abr_assessment.create_by             is '创建者';
comment on column abr_assessment.create_time           is '创建时间';
comment on column abr_assessment.update_by             is '更新者';
comment on column abr_assessment.update_time           is '更新时间';
comment on column abr_assessment.remark                is '备注';

-- ----------------------------
-- 3、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '测评状态', 'abr_assessment_status', '0', 'admin', now(), '测评记录的状态'
where not exists (select 1 from sys_dict_type t where t.dict_type = 'abr_assessment_status');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_assessment_status', v.list_class, 'N', '0', 'admin', now()
from (values
  (1, '答题中', 'in_progress', 'primary'),
  (2, '已终止', 'terminated',  'info'),
  (3, '已提交', 'submitted',   'success')
) as v(dict_sort, dict_label, dict_value, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_assessment_status' and d.dict_value = v.dict_value);

-- ----------------------------------------------------------------------------
-- M4-01 C 端落地页与隐私同意：协议版本表（隐私政策等，按版本保存全文）、测评记录内容编号
-- 学生同意时记录的是 abr_assessment.policy_code + policy_version，必须对应本表中启用的版本
-- 可重复执行（create if not exists / add column if not exists）；协议种子由 update_20261015_policy_seed.sql 导入
-- ----------------------------------------------------------------------------

create table if not exists abr_policy (
  policy_id        bigserial      not null,
  policy_code      varchar(64)    not null,
  version          varchar(32)    not null,
  title            varchar(200)   not null,
  content          text           not null,
  is_draft         char(1)        default 'Y',
  publish_status   varchar(16)    default 'draft',
  effective_date   date,
  del_flag         char(1)        default '0',
  create_by        varchar(64)    default '',
  create_time      timestamp,
  update_by        varchar(64)    default '',
  update_time      timestamp,
  remark           varchar(500)   default null,
  primary key (policy_id)
);
create unique index if not exists uk_abr_policy_version on abr_policy (policy_code, version) where del_flag = '0';
create unique index if not exists uk_abr_policy_active on abr_policy (policy_code) where publish_status = 'active' and del_flag = '0';

comment on table  abr_policy                is '协议版本表（隐私政策、用户协议等；按版本保存全文，学生同意的版本可追溯）';
comment on column abr_policy.policy_id      is '协议版本ID';
comment on column abr_policy.policy_code    is '协议编码（如 abr_privacy_policy，与问卷 consent.policy_code 一致）';
comment on column abr_policy.version        is '版本号';
comment on column abr_policy.title          is '标题';
comment on column abr_policy.content        is '全文（Markdown；C 端按纯文本段落展示，不渲染 HTML）';
comment on column abr_policy.is_draft       is '是否未经律师审阅的草案（Y是 N否）；草案只能用于测试环境，C 端会显示“草案”标识';
comment on column abr_policy.publish_status is '发布状态（字典 abr_engine_status：draft 草稿 active 启用 retired 已停用）；同一协议只能有一个启用版本';
comment on column abr_policy.effective_date is '生效日期';
comment on column abr_policy.del_flag       is '删除标志（0存在 2删除）';
comment on column abr_policy.create_by      is '创建者';
comment on column abr_policy.create_time    is '创建时间';
comment on column abr_policy.update_by      is '更新者';
comment on column abr_policy.update_time    is '更新时间';
comment on column abr_policy.remark         is '备注';

alter table abr_assessment add column if not exists content_code varchar(32) default '';
comment on column abr_assessment.content_code is '创建测评时最近一次进入的内容编号（与 channel_code 一起用于内容转化统计）';

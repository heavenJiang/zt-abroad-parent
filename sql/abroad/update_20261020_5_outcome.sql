-- ----------------------------------------------------------------------------
-- M9-06 录取结果回填：学生在 C 端回填选校单项目的结果（单独授权），进入案例库（自报 C 级，员工核验截图后升级），
-- 首次回填按申请季发一份服务抵扣作为感谢（不给现金）；奖励表与 M9-02 推荐奖励共用。
-- 依赖：update_20261020_4_case.sql（案例库）、update_20261019_lead_assign.sql（员工提醒）
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_reward (
  reward_id     bigserial      not null,
  app_user_id   bigint         default null,
  lead_id       bigint         default null,
  reward_type   varchar(32)    not null,
  title         varchar(100)   not null,
  product_code  varchar(64)    default '',
  status        varchar(16)    not null default 'issued',
  ref_type      varchar(32)    default '',
  ref_id        bigint         default null,
  dedup_key     varchar(100)   default null,
  confirm_by    varchar(64)    default '',
  confirm_time  timestamp      default null,
  used_time     timestamp      default null,
  create_time   timestamp      default now(),
  remark        varchar(500)   default null,
  primary key (reward_id)
);
create unique index if not exists uk_abr_reward_dedup on abr_reward (dedup_key) where dedup_key is not null;
create index if not exists idx_abr_reward_user on abr_reward (app_user_id, reward_id desc);

comment on table  abr_reward              is '奖励（回填结果的感谢、推荐奖励；只发服务抵扣或积分，不发现金）';
comment on column abr_reward.reward_id    is '奖励ID';
comment on column abr_reward.app_user_id  is '学生账号';
comment on column abr_reward.lead_id      is '线索';
comment on column abr_reward.reward_type  is '类型（字典 abr_reward_type：outcome_thanks 回填感谢 / referral 推荐奖励）';
comment on column abr_reward.title        is '奖励内容（如“免费定位咨询一次”；来自配置）';
comment on column abr_reward.product_code is '可抵扣的产品（abr_product）';
comment on column abr_reward.status       is '状态（字典 abr_reward_status：pending 待确认发放 / issued 已发放 / used 已使用 / void 已作废）';
comment on column abr_reward.ref_type     is '来源对象类型（case / referral）';
comment on column abr_reward.ref_id       is '来源对象ID';
comment on column abr_reward.dedup_key    is '去重键（同一来源只发一次）';
comment on column abr_reward.confirm_by   is '确认发放人';
comment on column abr_reward.confirm_time is '确认发放时间';
comment on column abr_reward.used_time    is '使用时间';
comment on column abr_reward.create_time  is '创建时间';
comment on column abr_reward.remark       is '备注';

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('奖励类型', 'abr_reward_type'), ('奖励状态', 'abr_reward_status')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '回填感谢',   'outcome_thanks', 'abr_reward_type',     'primary'),
  (2, '推荐奖励',   'referral',       'abr_reward_type',     'success'),
  (1, '待确认发放', 'pending',        'abr_reward_status',   'warning'),
  (2, '已发放',     'issued',         'abr_reward_status',   'success'),
  (3, '已使用',     'used',           'abr_reward_status',   'info'),
  (4, '已作废',     'void',           'abr_reward_status',   'danger'),
  (10, '回填录取结果', 'outcome',     'abr_lead_touch_type', 'success'),
  (7, '回填结果待核验', 'outcome_review', 'abr_notice_kind', 'primary')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- 回填的学生账号（本人查看、撤回、删除时按它判断）
alter table abr_case add column if not exists app_user_id bigint default null;
create index if not exists idx_abr_case_app_user on abr_case (app_user_id) where app_user_id is not null;
comment on column abr_case.app_user_id is '回填的学生账号（用户回填时；本人可查看、撤回授权、删除）';

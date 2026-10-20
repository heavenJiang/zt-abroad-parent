-- ----------------------------------------------------------------------------
-- M9-02 推荐码、奖励规则与防刷、referral_agent
--
-- - abr_referrer：推荐人（签约学生 / 校园大使 / 合作方）与专属码；链接 C 端首页 ?ch=ref&ct=推荐码，访客的渠道码与内容编号即归因。
-- - abr_referral：每个被推荐的访客一条（首次进入时的推荐码为准）：已注册 → 有效测评 → 已签约，或判为无效（附原因）。
-- - abr_referral_rule：奖励规则（推荐人类型 × 里程碑：有效测评 / 签约；推荐人奖励、被推荐人奖励、每天上限），只发服务抵扣不发现金。
-- - 防刷自动执行：同一手机号 / 微信只算一人（线索去重）；自己推荐自己无效；测评用时过短（abroad.referral.min-assessment-seconds）
--   或未成年人（不参加分享奖励类活动）不算有效测评；每个推荐人每天的有效测评奖励有上限。
-- - 奖励写入 abr_reward（待确认发放），员工在管理端确认发放、作废、标记已使用。
-- - referral_agent：订阅线索收口、测评提交、阶段变更（签约）；签约满月给学生生成推荐码并发送（消息模板 referral_code，停用）。
-- 依赖：update_20261020_5_outcome.sql（奖励表）、update_20261020_1_message.sql（消息模板）
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_referrer (
  referrer_id    bigserial      not null,
  referrer_type  varchar(16)    not null,
  display_name   varchar(64)    not null,
  ref_code       varchar(16)    not null,
  app_user_id    bigint         default null,
  lead_id        bigint         default null,
  status         char(1)        default '0',
  create_by      varchar(64)    default '',
  create_time    timestamp      default now(),
  update_by      varchar(64)    default '',
  update_time    timestamp      default null,
  remark         varchar(500)   default null,
  primary key (referrer_id)
);
create unique index if not exists uk_abr_referrer_code on abr_referrer (ref_code);
create unique index if not exists uk_abr_referrer_lead on abr_referrer (lead_id) where lead_id is not null;

comment on table  abr_referrer               is '推荐人与专属推荐码';
comment on column abr_referrer.referrer_id   is '推荐人ID';
comment on column abr_referrer.referrer_type is '类型（字典 abr_referrer_type：student 签约学生 / ambassador 校园大使 / partner 合作方）';
comment on column abr_referrer.display_name  is '称呼（只给员工看）';
comment on column abr_referrer.ref_code      is '推荐码（链接参数 ct；系统生成，8 位小写字母与数字）';
comment on column abr_referrer.app_user_id   is '学生账号（签约学生；C 端可查看自己的推荐码与进度）';
comment on column abr_referrer.lead_id       is '线索（签约学生）';
comment on column abr_referrer.status        is '状态（0 正常 1 停用：停用后新访客不再归因）';
comment on column abr_referrer.create_by     is '创建者';
comment on column abr_referrer.create_time   is '创建时间';
comment on column abr_referrer.update_by     is '更新者';
comment on column abr_referrer.update_time   is '更新时间';
comment on column abr_referrer.remark        is '备注';

create table if not exists abr_referral (
  referral_id    bigserial      not null,
  referrer_id    bigint         not null,
  visitor_id     bigint         not null,
  app_user_id    bigint         default null,
  lead_id        bigint         default null,
  status         varchar(16)    not null default 'registered',
  invalid_reason varchar(200)   default '',
  valid_time     timestamp      default null,
  signed_time    timestamp      default null,
  create_time    timestamp      default now(),
  update_time    timestamp      default now(),
  primary key (referral_id)
);
create unique index if not exists uk_abr_referral_visitor on abr_referral (visitor_id);
create index if not exists idx_abr_referral_referrer on abr_referral (referrer_id, status);

comment on table  abr_referral                is '被推荐人（每个访客一条，首次进入时的推荐码为准）';
comment on column abr_referral.referral_id    is 'ID';
comment on column abr_referral.referrer_id    is '推荐人';
comment on column abr_referral.visitor_id     is '被推荐的访客';
comment on column abr_referral.app_user_id    is '被推荐人的学生账号';
comment on column abr_referral.lead_id        is '被推荐人的线索';
comment on column abr_referral.status         is '状态（字典 abr_referral_status：registered 已进入 / valid 有效测评 / signed 已签约 / invalid 无效）';
comment on column abr_referral.invalid_reason is '无效原因（自己推荐自己、测评过快、未成年人、超出每天上限）';
comment on column abr_referral.valid_time     is '成为有效测评的时间';
comment on column abr_referral.signed_time    is '签约时间';
comment on column abr_referral.create_time    is '创建时间';
comment on column abr_referral.update_time    is '更新时间';

create table if not exists abr_referral_rule (
  rule_id         bigserial      not null,
  referrer_type   varchar(16)    not null,
  milestone       varchar(32)    not null,
  reward_title    varchar(100)   default '',
  reward_product  varchar(64)    default '',
  referee_title   varchar(100)   default '',
  daily_cap       integer        default 0,
  status          char(1)        default '0',
  create_by       varchar(64)    default '',
  create_time     timestamp      default now(),
  update_by       varchar(64)    default '',
  update_time     timestamp      default null,
  remark          varchar(500)   default null,
  primary key (rule_id)
);
create unique index if not exists uk_abr_referral_rule on abr_referral_rule (referrer_type, milestone);

comment on table  abr_referral_rule                is '推荐奖励规则（只发服务抵扣或积分，不发现金；发放须员工确认）';
comment on column abr_referral_rule.rule_id        is '规则ID';
comment on column abr_referral_rule.referrer_type  is '推荐人类型（字典 abr_referrer_type）';
comment on column abr_referral_rule.milestone      is '里程碑（字典 abr_referral_milestone：valid_assessment 有效测评 / signed 签约）';
comment on column abr_referral_rule.reward_title   is '推荐人奖励（空为不奖励）';
comment on column abr_referral_rule.reward_product is '推荐人奖励可抵扣的产品';
comment on column abr_referral_rule.referee_title  is '被推荐人奖励（空为不奖励；双方各得）';
comment on column abr_referral_rule.daily_cap      is '每个推荐人每天最多奖励几次（0 不限）';
comment on column abr_referral_rule.status         is '状态（0 正常 1 停用）';
comment on column abr_referral_rule.create_by      is '创建者';
comment on column abr_referral_rule.create_time    is '创建时间';
comment on column abr_referral_rule.update_by      is '更新者';
comment on column abr_referral_rule.update_time    is '更新时间';
comment on column abr_referral_rule.remark         is '备注';

alter table abr_reward add column if not exists referrer_id bigint default null;
comment on column abr_reward.referrer_id is '推荐人（推荐奖励发给没有学生账号的校园大使、合作方时）';

-- 种子规则（草案，待创始人确认；奖励内容为服务抵扣，金额与形式在规则中修改）
insert into abr_referral_rule (referrer_type, milestone, reward_title, reward_product, referee_title, daily_cap, status, create_by, create_time, remark)
select v.t, v.m, v.title, v.product, v.referee, v.cap, v.status, 'admin', now(), v.remark
from (values
  ('student',    'signed',           '推荐签约奖励：服务抵扣', 'service_master', '被推荐签约奖励：服务抵扣', 0, '0', '老带新：推荐的同学签约后双方各得服务抵扣（M0 草案）'),
  ('ambassador', 'valid_assessment', '校园大使：有效测评奖励', '',               '',                       5, '0', '按有效测评计；每天最多 5 次（防刷）'),
  ('ambassador', 'signed',           '校园大使：签约奖励',     '',               '',                       0, '0', '成交后结算'),
  ('partner',    'signed',           '',                       '',               '',                       0, '1', '合作方按成交分成（M9-05），这里不发奖励')
) as v(t, m, title, product, referee, cap, status, remark)
where not exists (select 1 from abr_referral_rule r where r.referrer_type = v.t and r.milestone = v.m);

-- 签约满月发推荐码的消息模板（停用）
insert into abr_message_template (template_code, channel, template_name, category, field_map, content, link, status, create_by, create_time, remark)
select 'referral_code', 'service_account', '推荐码', 'service', '{"thing1": "你的推荐码 {refCode}"}'::jsonb,
       '你的专属推荐码是 {refCode}，同学通过你的链接测评并签约后，你们各得一份服务抵扣。', '{refLink}', '1', 'admin', now(), '签约满月发送（referral_agent）'
where not exists (select 1 from abr_message_template t where t.template_code = 'referral_code' and t.channel = 'service_account');

-- 触发规则
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select v.name, 'event', v.event_type, v.filter::jsonb, null, null, 'referral_agent', v.action, '{}'::jsonb, v.delay, v.ttl, 3, '0', 'admin', now(), 'M9-02'
from (values
  ('线索收口 → 推荐归因',   'lead_captured',        '{}',              'attribute',   0,     24),
  ('测评提交 → 有效测评判断', 'assessment_submitted', '{}',              'assessment',  0,     24),
  ('签约 → 推荐签约奖励',   'lead_stage_changed',   '{"to": ["won"]}', 'signed',      0,     24),
  ('签约满月 → 发推荐码',   'lead_stage_changed',   '{"to": ["won"]}', 'invite_code', 43200, 72)
) as v(name, event_type, filter, action, delay, ttl)
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'referral_agent' and t.action = v.action);

-- 字典
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), ''
from (values ('推荐人类型', 'abr_referrer_type'), ('被推荐人状态', 'abr_referral_status'), ('推荐里程碑', 'abr_referral_milestone')) as v(dict_name, dict_type)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '签约学生', 'student',          'abr_referrer_type',      'primary'),
  (2, '校园大使', 'ambassador',       'abr_referrer_type',      'success'),
  (3, '合作方',   'partner',          'abr_referrer_type',      'warning'),
  (1, '已进入',   'registered',       'abr_referral_status',    'info'),
  (2, '有效测评', 'valid',            'abr_referral_status',    'primary'),
  (3, '已签约',   'signed',           'abr_referral_status',    'success'),
  (4, '无效',     'invalid',          'abr_referral_status',    'danger'),
  (1, '有效测评', 'valid_assessment', 'abr_referral_milestone', 'primary'),
  (2, '签约',     'signed',           'abr_referral_milestone', 'success')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- 菜单：获客运营 → 推荐与奖励 + 按钮；授予负责人
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '推荐与奖励', p.menu_id, 25, 'referral', 'abroad/referral/index', '', '', 1, 0, 'C', '0', '0', 'abroad:referral:list', 'peoples', 'admin', now(), '推荐码、被推荐人、奖励规则与发放'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:referral:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), v.remark
from sys_menu c
cross join (values ('推荐人新增', 1, 'abroad:referral:add', ''), ('推荐人修改', 2, 'abroad:referral:edit', '含奖励规则'),
                   ('推荐人删除', 3, 'abroad:referral:remove', ''), ('奖励发放', 4, 'abroad:reward:confirm', '确认发放、作废、标记已使用')) as v(menu_name, order_num, perms, remark)
where c.perms = 'abroad:referral:list' and not exists (select 1 from sys_menu m where m.perms = v.perms);

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms like 'abroad:referral:%' or m.perms = 'abroad:reward:confirm'
where r.role_key = 'abr_owner' and r.del_flag = '0'
on conflict do nothing;

-- 员工提醒类型：发送推荐码
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select 8, '发送推荐码', 'referral_code', 'abr_notice_kind', 'primary', 'N', '0', 'admin', now(), ''
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_notice_kind' and d.dict_value = 'referral_code');

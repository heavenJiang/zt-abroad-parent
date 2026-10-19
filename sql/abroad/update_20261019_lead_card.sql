-- ----------------------------------------------------------------------------
-- M5-05 lead_card 线索卡片：给顾问看的背景摘要、最可能的顾虑、建议的开场话题（仅内部可见）
--
-- - 基础信息（意向度与理由、阶段、来源、目标、背景、测评档位、选校单、转化时刻、工具使用）由规则从库里取，始终展示；
-- - AI 部分经 AI 网关调用 lead_card（审核策略 internal_only，去标识化；不给模型录取概率，输出不得有百分比或承诺）；
--   失败或未通过校验时只展示基础信息（fallback）；输入未变化时不重复生成。
-- - 触发：线索升到 A / B（lead_grade_changed），管理端手动重新生成；预约成功（M5-06）后接入。
-- 依赖：update_20261019_lead_score.sql、update_20261014_ai.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_lead_card (
  card_id         bigserial      not null,
  lead_id         bigint         not null,
  card_status     varchar(16)    not null default 'fallback',
  basics          jsonb          not null default '{}'::jsonb,
  ai_content      jsonb          default null,
  input_digest    varchar(64)    default '',
  ai_call_id      bigint         default null,
  ai_status       varchar(32)    default '',
  error_msg       varchar(500)   default '',
  trigger_type    varchar(32)    default '',
  generated_time  timestamp      default null,
  create_by       varchar(64)    default '',
  create_time     timestamp      default now(),
  update_by       varchar(64)    default '',
  update_time     timestamp      default now(),
  primary key (card_id)
);
create unique index if not exists uk_abr_lead_card_lead on abr_lead_card (lead_id);

comment on table  abr_lead_card                is '线索卡片（每条线索一张，重新生成时覆盖；仅内部可见）';
comment on column abr_lead_card.card_id        is '卡片ID';
comment on column abr_lead_card.lead_id        is '线索ID';
comment on column abr_lead_card.card_status    is '状态（done AI 生成 / fallback 只有基础信息）';
comment on column abr_lead_card.basics         is '基础信息（规则取数：意向度与理由、阶段、来源、目标、背景、测评档位、选校单、转化时刻、工具使用）';
comment on column abr_lead_card.ai_content     is 'AI 内容（jsonb：summary 背景摘要 / concerns 顾虑 [{text, basis}] / openers 开场话题）';
comment on column abr_lead_card.input_digest   is '模型输入摘要（SHA-256；未变化时不重复生成）';
comment on column abr_lead_card.ai_call_id     is 'AI 调用ID';
comment on column abr_lead_card.ai_status      is 'AI 调用状态';
comment on column abr_lead_card.error_msg      is '失败或未通过校验的原因';
comment on column abr_lead_card.trigger_type   is '触发（grade 升到 A / B / manual 手动 / booking 预约成功）';
comment on column abr_lead_card.generated_time is '生成时间';
comment on column abr_lead_card.create_by      is '创建者';
comment on column abr_lead_card.create_time    is '创建时间';
comment on column abr_lead_card.update_by      is '更新者';
comment on column abr_lead_card.update_time    is '更新时间';

-- 线索卡片 Agent 已在 M2-04 种子中（internal_only、mock 模型）；描述与兜底说明对齐本卡
update abr_ai_agent
   set description = '生成顾问看的线索卡片（背景摘要、最可能的顾虑、开场话题；不含录取概率）', remark = '失败时只展示基础信息',
       update_by = 'admin', update_time = now()
 where agent_code = 'lead_card' and remark = '失败时只发基础信息';

insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select '线索升到 A / B → 线索卡片', 'event', 'lead_grade_changed', '{"to": ["A", "B"]}'::jsonb, null, null, 'lead_card', 'generate', '{}'::jsonb,
       0, 24, 2, '0', 'admin', now(), 'M5-05'
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'lead_card' and t.action = 'generate' and t.event_type = 'lead_grade_changed');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '生成线索卡片', c.menu_id, 11, '', '', '', '', 1, 0, 'F', '0', '0', 'abroad:lead:card', '#', 'admin', now(), ''
from sys_menu c
where c.perms = 'abroad:lead:list'
  and not exists (select 1 from sys_menu m where m.perms = 'abroad:lead:card');

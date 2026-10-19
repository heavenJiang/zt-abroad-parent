-- ----------------------------------------------------------------------------
-- M7-03 content_writer 多平台稿件
--
-- 按选题（abr_topic）为账号所在平台写稿：标题、正文、话题；数字和院校事实只取选题引用的院校库项目（90 天内核对过的才给数值），
-- 文末引导（私信口令或工具链接）与 AI 生成标识由系统按平台模板追加，不由大模型写。
-- 写完先过合规检查，不通过带着拦截项重写 1 次；仍不通过照常入库，合规闸门拦截后进例外队列（M6-04）。
-- 稿件经 IAbrContentService.insertAgentContent 入库（执行模式为人工确认时须确认后才能发布），选题记为已写稿。
-- 依赖：update_20261019_topic_plan.sql、update_20261019_review_queue.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、内容：模型输入（不合格样本回流评测集时作为样例输入）
-- ----------------------------
alter table abr_content add column if not exists ai_input jsonb default null;
comment on column abr_content.ai_input is '生成稿件的模型输入（不含个人信息；例外与抽检不合格时作为评测样例的输入）';

-- ----------------------------
-- 2、AI 网关 Agent（模型先用 mock，上线前在管理端改成真实模型）
-- ----------------------------
insert into abr_ai_agent (agent_code, agent_name, description, review_policy, sample_rate, primary_model, backup_model,
                          prompt_version, timeout_ms, max_output_tokens, temperature, status, create_by, create_time, remark)
values ('content_writer', '内容创作', '按选题为各平台写营销稿件（数字只取院校库；发出前经合规闸门与执行模式控制）',
        'direct', 0, 'mock:default', 'mock:default', 'v1', 45000, 3000, 0.70, '0', 'admin', now(),
        '合规不通过重写 1 次，仍失败进例外队列')
on conflict (agent_code) do nothing;

-- ----------------------------
-- 3、触发规则：选题排入后写稿；每天 08:00 补写到期的选题
-- ----------------------------
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select v.name, v.type, v.event_type, '{}'::jsonb, v.cron, null, 'content_writer', 'write_topics', '{}'::jsonb,
       0, 12, 3, '0', 'admin', now(), 'M7-03'
from (values
  ('选题排入 → 写稿',          'event',    'topics_planned', null),
  ('每天 08:00 补写到期的选题', 'schedule', null,             '0 0 8 * * *')
) as v(name, type, event_type, cron)
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'content_writer' and t.action = 'write_topics'
                  and coalesce(t.event_type, '') = coalesce(v.event_type, ''));

-- ----------------------------
-- 4、按钮：选题排期 → 立即写稿
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '立即写稿', c.menu_id, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'abroad:topic:write', '#', 'admin', now(), ''
from sys_menu c
where c.perms = 'abroad:topic:list'
  and not exists (select 1 from sys_menu m where m.perms = 'abroad:topic:write');

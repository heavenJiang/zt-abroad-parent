-- ----------------------------------------------------------------------------
-- M8-04 booking_agent 邀约：预约确认、开始前提醒、改约 / 取消通知、爽约后邀请改约；A 类线索与转化时刻的邀约（推荐产品）
--
-- - 服务提醒（确认、提醒、变更、爽约）按模板发给预约的学生账号（没有账号时发给线索），只受暂停与停用约束；
-- - 邀约为营销消息：受退订、未成年人、频控、免打扰约束并过合规闸门；booking_agent 为“人工确认”时不自动发，提醒负责顾问人工邀约；
--   渠道不可达或发送失败时同样转负责顾问人工邀约；
-- - 推荐的产品取转化时刻配置（abr_moment_rule.recommend_product），没有时用触发规则参数 product；
-- - 模板为停用状态，开通渠道并填好平台模板 ID 后启用；未启用时发送结果为 no_channel，不影响预约本身。
-- 依赖：update_20261019_booking.sql（预约）、update_20261020_1_message.sql（消息模板）
-- 可重复执行
-- ----------------------------------------------------------------------------

alter table abr_booking add column if not exists reminded_time timestamp default null;
comment on column abr_booking.reminded_time is '开始前提醒的发送时间（booking_agent；改约后清空重新提醒）';

-- ----------------------------
-- 1、模板（停用）
-- ----------------------------
insert into abr_message_template (template_code, channel, template_name, category, field_map, content, link, status, create_by, create_time, remark)
select v.code, v.channel, v.name, v.category, v.field_map::jsonb, v.content, v.link, '1', 'admin', now(), v.remark
from (values
  ('booking_noshow', 'service_account', '爽约后改约', 'service', '{"thing1": "{productName}", "time2": "{startTime}"}',
   '你预约的 {productName}（{startTime}）没有开始，可以重新选一个时间。', '{bookingUrl}', '标记未到后发送，跳转到预约页'),
  ('booking_noshow', 'sms', '爽约后改约', 'service', '{"product": "{productName}"}',
   '你预约的 {productName} 没有开始，可以重新选一个时间。', '', ''),
  ('booking_invite', 'service_account', '咨询邀约', 'marketing', '{"thing1": "{productName}", "thing2": "{reason}"}',
   '{reason}，可以预约 {productName}，和顾问聊聊你的选校单。', '{bookingUrl}', '营销类：发出前过合规闸门；{reason} 来自转化时刻的推荐语或固定文字，不写价格与录取承诺')
) as v(code, channel, name, category, field_map, content, link, remark)
where not exists (select 1 from abr_message_template t where t.template_code = v.code and t.channel = v.channel);

-- ----------------------------
-- 2、触发规则
-- ----------------------------
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select v.name, v.type, v.event_type, v.filter::jsonb, v.cron, null, 'booking_agent', v.action, v.params::jsonb, 0, v.ttl, 3, '0', 'admin', now(), 'M8-04'
from (values
  ('预约成功 → 确认通知',   'event',    'booking_created',    '{}',             null,             'confirm',       '{}', 24),
  ('预约变更 → 变更通知',   'event',    'booking_changed',    '{}',             null,             'notify_change', '{}', 24),
  ('开始前提醒',            'schedule', null,                 '{}',             '0 0/10 * * * ?', 'remind',        '{"hours_before": 24}', 1),
  ('线索升到 A → 邀约',     'event',    'lead_grade_changed', '{"to": ["A"]}',  null,             'invite',        '{"product": "consult_position"}', 48)
) as v(name, type, event_type, filter, cron, action, params, ttl)
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'booking_agent' and t.action = v.action);

-- 转化时刻 → 邀约推荐（M6-01 种子，M8-04 启用）
update abr_growth_trigger
set status = '0', params = '{"product": "consult_position"}'::jsonb, ttl_hours = 48, update_by = 'admin', update_time = now(),
    remark = 'M8-04 已启用：推荐转化时刻配置的产品（没有时用 product）'
where agent_code = 'booking_agent' and action = 'recommend' and event_type = 'plan_moment' and remark = 'M8-04 上线时启用';

-- ----------------------------
-- 3、员工提醒类型：人工邀约
-- ----------------------------
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select 6, '人工邀约', 'invite_manual', 'abr_notice_kind', 'primary', 'N', '0', 'admin', now(), ''
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_notice_kind' and d.dict_value = 'invite_manual');

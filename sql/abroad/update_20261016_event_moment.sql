-- ----------------------------------------------------------------------------
-- M4-14 业务事件表与转化时刻：C 端写操作与“我的申请规划”中的转化时刻写入 abr_event（M1-04 事件表），
-- 转化时刻规则（是否启用、推荐的服务、阈值）可配置；每类时刻每人只记录、推荐一次，推荐展示遵守频控
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、业务事件表
-- ----------------------------
create table if not exists abr_event (
  event_id          bigserial      not null,
  event_type        varchar(64)    not null,
  visitor_id        bigint         default null,
  app_user_id       bigint         default null,
  ref_type          varchar(32)    default null,
  ref_id            bigint         default null,
  dedup_key         varchar(128)   default null,
  payload           jsonb          default '{}'::jsonb,
  recommend_status  varchar(16)    default null,
  recommend_label   varchar(100)   default null,
  shown_time        timestamp      default null,
  handled_time      timestamp      default null,
  create_time       timestamp      default now(),
  primary key (event_id)
);
create unique index if not exists uk_abr_event_dedup on abr_event (event_type, visitor_id, dedup_key) where dedup_key is not null;
create index if not exists idx_abr_event_visitor on abr_event (visitor_id, event_type, create_time);
create index if not exists idx_abr_event_unhandled on abr_event (event_type, event_id) where handled_time is null;

comment on table  abr_event                  is '业务事件表（C 端写操作、转化时刻等；供线索评分、邀约等订阅方读取与重放）';
comment on column abr_event.event_id         is '事件ID';
comment on column abr_event.event_type       is '事件类型（plan_item_added / plan_item_removed / plan_material_updated / plan_share_created / plan_share_revoked / plan_moment 等）';
comment on column abr_event.visitor_id       is '访客ID';
comment on column abr_event.app_user_id      is '学生账号ID（M4-04 登录后）';
comment on column abr_event.ref_type         is '关联对象类型（program / share 等）';
comment on column abr_event.ref_id           is '关联对象ID';
comment on column abr_event.dedup_key        is '去重键（同一访客同类事件只记一次；转化时刻为时刻编码）';
comment on column abr_event.payload          is '事件内容（jsonb）';
comment on column abr_event.recommend_status is '推荐状态（转化时刻：pending 待展示 / shown 已展示 / suppressed 不展示（未成年等）/ none 无推荐）';
comment on column abr_event.recommend_label  is '推荐的服务名称（来自转化时刻规则）';
comment on column abr_event.shown_time       is '推荐展示时间';
comment on column abr_event.handled_time     is '订阅方处理时间（线索评分、邀约等；未处理为空）';
comment on column abr_event.create_time      is '发生时间';

-- ----------------------------
-- 2、转化时刻规则
-- ----------------------------
create table if not exists abr_moment_rule (
  rule_id            bigserial      not null,
  moment_code        varchar(32)    not null,
  moment_name        varchar(64)    not null,
  recommend_product  varchar(64)    default null,
  recommend_label    varchar(100)   default null,
  recommend_text     varchar(300)   default null,
  params             jsonb          default '{}'::jsonb,
  status             char(1)        default '0',
  create_by          varchar(64)    default '',
  create_time        timestamp,
  update_by          varchar(64)    default '',
  update_time        timestamp,
  remark             varchar(500)   default null,
  primary key (rule_id)
);
create unique index if not exists uk_abr_moment_rule_code on abr_moment_rule (moment_code);

comment on table  abr_moment_rule                   is '转化时刻规则（我的申请规划中的真实时刻 → 推荐的服务；每类每人只推荐一次）';
comment on column abr_moment_rule.rule_id           is '规则ID';
comment on column abr_moment_rule.moment_code       is '时刻编码（imbalance 冲保失衡 / requirement_unmet 要求变化后不达标 / materials_behind 截止前材料不齐 / parent_view 家长查看 / repeated_edits 反复修改）';
comment on column abr_moment_rule.moment_name       is '时刻名称';
comment on column abr_moment_rule.recommend_product is '推荐的产品编码（M5-06 产品与报价配置；未配置为空）';
comment on column abr_moment_rule.recommend_label   is '推荐的服务名称（为空时只记录事件、不向学生推荐）';
comment on column abr_moment_rule.recommend_text    is '推荐文案（展示给学生；不写价格）';
comment on column abr_moment_rule.params            is '阈值参数（jsonb，见各时刻说明）';
comment on column abr_moment_rule.status            is '状态（0正常 1停用；停用后不再记录该时刻）';
comment on column abr_moment_rule.create_by         is '创建者';
comment on column abr_moment_rule.create_time       is '创建时间';
comment on column abr_moment_rule.update_by         is '更新者';
comment on column abr_moment_rule.update_time       is '更新时间';
comment on column abr_moment_rule.remark            is '备注';

insert into abr_moment_rule (moment_code, moment_name, recommend_label, recommend_text, params, status, create_by, create_time, remark)
select v.code, v.name, v.label, v.text, cast(v.params as jsonb), '0', 'admin', now(), v.remark
from (values
  ('imbalance',         '冲保失衡',         '定位咨询',     '你的选校单冲刺偏多或还没有保底，可以预约一次免费定位咨询，帮你把组合排稳。',
   '{"min_tiered": 3}', '选校单中已分档项目不少于 min_tiered 个，且没有保底或冲刺多于主攻 + 保底'),
  ('requirement_unmet', '要求变化后不达标', '规划咨询',     '有项目的申请要求更新后你暂不满足，可以预约规划咨询，看看怎么补或换哪些项目。',
   '{}', '项目加入时可申（冲 / 稳 / 保），申请要求在加入后更新，现在变为补足后可申或不符合条件'),
  ('materials_behind',  '截止前材料不齐',   '文书 / 递交服务', '有项目临近截止但材料还差不少，可以了解文书与递交服务，按时把材料备齐。',
   '{"days_before": 30, "completion_below": 0.5}', '有项目距最近截止不超过 days_before 天，且该项目材料完成度低于 completion_below'),
  ('parent_view',       '家长查看',         '家长会谈',     '家长已经看过你的选校单，可以预约一次家长会谈，一起把方案定下来。',
   '{}', '家长打开了只读视图'),
  ('repeated_edits',    '反复修改',         '定位咨询',     '最近你多次调整选校单，如果拿不准，可以预约一次免费定位咨询。',
   '{"window_days": 14, "min_edits": 8, "min_days": 3}', 'window_days 天内加入或移除项目不少于 min_edits 次，且分布在不少于 min_days 天')
) as v(code, name, label, text, params, remark)
where not exists (select 1 from abr_moment_rule r where r.moment_code = v.code);

-- ----------------------------
-- 3、菜单：测评与选校 → 转化时刻规则 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '转化时刻', p.menu_id, 6, 'momentRule', 'abroad/momentRule/index', '', '', 1, 0, 'C', '0', '0', 'abroad:momentRule:list', 'guide', 'admin', now(), '转化时刻规则菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'assessment') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:momentRule:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('规则查询', 1, 'abroad:momentRule:query'),
  ('规则修改', 2, 'abroad:momentRule:edit')
) as v(menu_name, order_num, perms)
join sys_menu c on c.perms = 'abroad:momentRule:list'
where not exists (select 1 from sys_menu m where m.perms = v.perms);

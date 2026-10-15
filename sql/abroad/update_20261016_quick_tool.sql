-- ----------------------------------------------------------------------------
-- M4-02 30 秒快测：入口工具运行记录、我的选校单（先按访客保存，M4-04 登录后关联学生账号）
-- 可重复执行（create if not exists）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、入口工具运行记录（快测等；每次计算一条，用于选校单档位留痕、内容转化统计、顾问查看）
-- ----------------------------
create table if not exists abr_tool_run (
  tool_run_id      bigserial      not null,
  tool             varchar(32)    not null,
  visitor_id       bigint         not null,
  degree           varchar(16)    default 'master',
  questionnaire_id bigint         default null,
  input            jsonb          not null,
  result           jsonb          default '{}'::jsonb,
  engine_versions  jsonb          default '{}'::jsonb,
  channel_code     varchar(32)    default 'direct',
  content_code     varchar(32)    default '',
  client_platform  varchar(16)    default '',
  policy_code      varchar(64)    default '',
  policy_version   varchar(32)    default '',
  create_time      timestamp      default now(),
  primary key (tool_run_id)
);
create index if not exists idx_abr_tool_run_visitor on abr_tool_run (visitor_id, tool_run_id);
create index if not exists idx_abr_tool_run_time on abr_tool_run (tool, create_time);

comment on table  abr_tool_run                  is '入口工具运行记录（30 秒快测等；每次计算一条）';
comment on column abr_tool_run.tool_run_id      is '运行ID';
comment on column abr_tool_run.tool             is '工具（quick 30 秒快测）';
comment on column abr_tool_run.visitor_id       is '访客ID';
comment on column abr_tool_run.degree           is '学段（字典 abr_degree）';
comment on column abr_tool_run.questionnaire_id is '题目与换算表所用的问卷版本ID';
comment on column abr_tool_run.input            is '输入（题目 id → 答案，jsonb）';
comment on column abr_tool_run.result           is '计算留痕（各项目分组、档位、未达标项；含内部字段，只给管理端）';
comment on column abr_tool_run.engine_versions  is '所用引擎配置版本（国家 / 地区 → 版本）';
comment on column abr_tool_run.channel_code     is '渠道码';
comment on column abr_tool_run.content_code     is '内容编号';
comment on column abr_tool_run.client_platform  is '端（h5 / mp_weixin）';
comment on column abr_tool_run.policy_code      is '同意的协议编码';
comment on column abr_tool_run.policy_version   is '同意的协议版本';
comment on column abr_tool_run.create_time      is '计算时间';

-- ----------------------------
-- 2、我的选校单（M4-02 只做加入、查看、移除；排序、逐项达标与失衡提示在 M4-10）
-- ----------------------------
create table if not exists abr_plan_item (
  plan_item_id     bigserial      not null,
  visitor_id       bigint         not null,
  app_user_id      bigint         default null,
  program_id       bigint         not null,
  source           varchar(32)    not null,
  source_ref       bigint         default null,
  tier_snapshot    varchar(32)    default null,
  sort_order       integer        default 0,
  create_time      timestamp      default now(),
  update_time      timestamp      default now(),
  primary key (plan_item_id)
);
create unique index if not exists uk_abr_plan_item_visitor_program on abr_plan_item (visitor_id, program_id);

comment on table  abr_plan_item                 is '我的选校单（项目条目；匿名按访客保存，登录后关联学生账号）';
comment on column abr_plan_item.plan_item_id    is '条目ID';
comment on column abr_plan_item.visitor_id      is '访客ID';
comment on column abr_plan_item.app_user_id     is '学生账号ID（M4-04 登录后关联）';
comment on column abr_plan_item.program_id      is '项目ID';
comment on column abr_plan_item.source          is '加入来源（quick 快测、assessment 完整测评、program 项目页、tool 其他工具）';
comment on column abr_plan_item.source_ref      is '来源记录ID（快测为 abr_tool_run.tool_run_id）';
comment on column abr_plan_item.tier_snapshot   is '加入时的分组（reach / match / safety / unrated / needs_improvement；由来源记录取得，不信任前端）';
comment on column abr_plan_item.sort_order      is '排序';
comment on column abr_plan_item.create_time     is '加入时间';
comment on column abr_plan_item.update_time     is '更新时间';

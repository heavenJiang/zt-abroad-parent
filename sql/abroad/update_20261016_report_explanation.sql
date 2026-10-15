-- ----------------------------
-- M4-06 测评解读（report_explainer）：异步生成、模板兜底
-- 每份测评一条；解读对应某一次选校计算（result_computed_time），重新计算后旧解读失效、下次查看报告时重新生成。
-- 只保存通过校验的 AI 文字；模板文字由报告结构化数据实时生成，不入库。
-- ----------------------------
create table if not exists abr_report_explanation (
  explanation_id       bigserial      not null,
  assessment_id        bigint         not null,
  result_computed_time timestamp      not null,
  explain_status       varchar(16)    not null default 'running',
  claim_token          varchar(36)    not null,
  ai_output            jsonb          default null,
  ai_status            varchar(32)    default null,
  call_id              bigint         default null,
  error_msg            varchar(500)   default null,
  started_time         timestamp      default now(),
  finished_time        timestamp      default null,
  create_time          timestamp      default now(),
  update_time          timestamp      default now(),
  primary key (explanation_id)
);
create unique index if not exists uk_abr_report_explanation_assessment on abr_report_explanation (assessment_id);

comment on table  abr_report_explanation                      is '测评解读（report_explainer 生成的定位、学校理由、短板说明；每份测评一条）';
comment on column abr_report_explanation.explanation_id       is '解读ID';
comment on column abr_report_explanation.assessment_id        is '测评ID';
comment on column abr_report_explanation.result_computed_time is '所解读的选校结果计算时间（与 abr_assessment_result.computed_time 不同即失效）';
comment on column abr_report_explanation.explain_status       is '状态（running 生成中 / done 已完成 / failed 失败用模板）';
comment on column abr_report_explanation.claim_token          is '本次生成的认领令牌（写回结果时校验，避免旧任务覆盖新任务）';
comment on column abr_report_explanation.ai_output            is '通过校验的 AI 文字（headline、programs：项目ID → 理由、shortcomings：序号 → 说明）';
comment on column abr_report_explanation.ai_status            is 'AI 网关结果状态（success / degraded / filtered / error 等）';
comment on column abr_report_explanation.call_id              is 'AI 调用日志ID（abr_ai_call_log）';
comment on column abr_report_explanation.error_msg            is '失败原因';
comment on column abr_report_explanation.started_time         is '开始生成时间';
comment on column abr_report_explanation.finished_time        is '完成时间';
comment on column abr_report_explanation.create_time          is '创建时间';
comment on column abr_report_explanation.update_time          is '更新时间';

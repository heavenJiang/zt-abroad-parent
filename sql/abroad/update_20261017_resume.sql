-- ----------------------------
-- M4-07 简历 / 成绩单上传与 resume_parser 解析
-- 每份测评每类（简历、成绩单）最多一份有效文件；重新上传时旧文件标记删除并立即删除原件。
-- 原件存私有目录（abroad.resume.dir，不在 /profile 静态目录下），解析完成后按 purge_time 由定时任务删除。
-- 解析结果只是建议值，学生确认后由前端按普通答案保存（PUT /app/assessment/{id}/answers），本表不写答案。
-- ----------------------------
create table if not exists abr_assessment_file (
  file_id          bigserial      not null,
  assessment_id    bigint         not null,
  visitor_id       bigint         not null,
  file_kind        varchar(16)    not null,
  file_ext         varchar(8)     not null,
  file_size        bigint         not null default 0,
  original_name    varchar(100)   default null,
  storage_path     varchar(255)   default null,
  consent_id       varchar(64)    not null,
  consent_time     timestamp      not null,
  parse_status     varchar(16)    not null default 'running',
  claim_token      varchar(36)    not null,
  text_source      varchar(16)    default null,
  text_chars       integer        default null,
  parsed           jsonb          default null,
  fail_reason      varchar(32)    default null,
  ai_status        varchar(32)    default null,
  call_id          bigint         default null,
  error_msg        varchar(500)   default null,
  started_time     timestamp      default now(),
  finished_time    timestamp      default null,
  purge_time       timestamp      not null,
  purged_time      timestamp      default null,
  del_flag         char(1)        not null default '0',
  create_time      timestamp      default now(),
  update_time      timestamp      default now(),
  primary key (file_id)
);
create unique index if not exists uk_abr_assessment_file_kind on abr_assessment_file (assessment_id, file_kind) where del_flag = '0';
create index if not exists idx_abr_assessment_file_assessment on abr_assessment_file (assessment_id, create_time);
create index if not exists idx_abr_assessment_file_purge on abr_assessment_file (purge_time) where purged_time is null;

comment on table  abr_assessment_file                is '测评上传文件（简历、成绩单；resume_parser 解析为问卷建议值）';
comment on column abr_assessment_file.file_id        is '文件ID';
comment on column abr_assessment_file.assessment_id  is '测评ID';
comment on column abr_assessment_file.visitor_id     is '上传的访客ID';
comment on column abr_assessment_file.file_kind      is '类别（resume 简历 / transcript 成绩单）';
comment on column abr_assessment_file.file_ext       is '扩展名（pdf / jpg / jpeg / png，已按文件头核对）';
comment on column abr_assessment_file.file_size      is '文件大小（字节）';
comment on column abr_assessment_file.original_name  is '原文件名（只回显给本人，原件删除时一并清空）';
comment on column abr_assessment_file.storage_path   is '原件在私有目录下的相对路径（原件删除后清空）';
comment on column abr_assessment_file.consent_id     is '单独同意的编号（问卷 consent.separate_consents.id，如 resume_ai_parse）';
comment on column abr_assessment_file.consent_time   is '单独同意时间';
comment on column abr_assessment_file.parse_status   is '解析状态（running 识别中 / done 已识别 / failed 未识别）';
comment on column abr_assessment_file.claim_token    is '本次解析的认领令牌（写回结果时校验）';
comment on column abr_assessment_file.text_source    is '文字来源（pdf_text PDF 文字层 / ocr 文字识别）';
comment on column abr_assessment_file.text_chars     is '去除敏感行后交给模型的文字字数';
comment on column abr_assessment_file.parsed         is '通过校验的建议值（suggestions：题目 → 答案形状的值；schoolCandidates：本科学校候选）';
comment on column abr_assessment_file.fail_reason    is '未识别原因（no_text 无文字 / ocr_unavailable 图片暂不能识别 / ai_failed 解析失败 / nothing_found 未找到可填内容 / timeout 超时）';
comment on column abr_assessment_file.ai_status      is 'AI 网关结果状态';
comment on column abr_assessment_file.call_id        is 'AI 调用日志ID（abr_ai_call_log）';
comment on column abr_assessment_file.error_msg      is '内部错误信息（不返回 C 端）';
comment on column abr_assessment_file.started_time   is '开始解析时间';
comment on column abr_assessment_file.finished_time  is '解析完成时间';
comment on column abr_assessment_file.purge_time     is '原件计划删除时间（上传时间 + 保存天数）';
comment on column abr_assessment_file.purged_time    is '原件实际删除时间';
comment on column abr_assessment_file.del_flag       is '删除标志（0 有效 2 已删除或已被重新上传替换）';
comment on column abr_assessment_file.create_time    is '上传时间';
comment on column abr_assessment_file.update_time    is '更新时间';

-- ----------------------------
-- 定时任务：每天 03:20 删除到期的简历 / 成绩单原件（默认启用）
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select '删除到期的简历原件', 'DEFAULT', 'abrResumeTask.purgeExpired()', '0 20 3 * * ?', '3', '1', '0', 'admin', now(),
       'M4-07：简历 / 成绩单原件到期（默认上传后 30 日）删除，解析出的建议值保留'
where not exists (select 1 from sys_job where invoke_target = 'abrResumeTask.purgeExpired()');

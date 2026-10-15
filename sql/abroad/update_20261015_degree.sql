-- ----------------------------------------------------------------------------
-- M3-09 学段框架：问卷记录服务的学段，测评记录首屏选择的学段（学段字典 abr_degree：secondary 高中 / bachelor 本科 / master 硕士 / phd 博士）
-- 院校库项目（abr_program.degree）与引擎配置（abr_engine_config.degree）已带学段
-- 可重复执行（add column if not exists；回填只改与计算结果不同的行）
-- ----------------------------------------------------------------------------

alter table abr_questionnaire add column if not exists degrees varchar(100) default '';
comment on column abr_questionnaire.degrees is '服务的学段（字典 abr_degree，逗号分隔；取自问卷 JSON 的 degree_levels）；C 端首屏按学段找启用的问卷';

-- 按问卷 JSON 的 degree_levels 回填
update abr_questionnaire q
   set degrees = v.degrees
  from (select questionnaire_id,
               coalesce((select string_agg(x ->> 'value', ',' order by ord)
                           from jsonb_array_elements(case when jsonb_typeof(definition -> 'degree_levels') = 'array'
                                                          then definition -> 'degree_levels' else '[]'::jsonb end)
                                with ordinality as t(x, ord)), '') as degrees
          from abr_questionnaire) v
 where q.questionnaire_id = v.questionnaire_id
   and q.degrees is distinct from v.degrees;

alter table abr_assessment add column if not exists degree varchar(16) default 'master';
comment on column abr_assessment.degree is '学段（字典 abr_degree）：C 端首屏选择的学段，决定使用哪份问卷；引擎计算以问卷中的目标学位为准，未答时用本列';


-- ----------------------------------------------------------------------------
-- M2-06 院校字段抽取（catalog_extractor）：提示词 v1 生效 + 单次模型超时不超过 AI 预填总时限
-- 提示词目录：zt-abroad/src/main/resources/prompts/catalog_extractor/v1/
-- 可重复执行（只在值不同时更新）
-- ----------------------------------------------------------------------------

-- 1、生效提示词版本 v1（管理端「Agent 配置」切换版本前会校验目录是否存在）
update abr_ai_agent
   set prompt_version = 'v1', update_by = 'admin', update_time = now()
 where agent_code = 'catalog_extractor'
   and prompt_version <> 'v1';

-- 2、AI 预填总时限 30 秒（抓取页面最多 15 秒），单次模型调用超时收紧到 25 秒；已手工调小的保持不变
update abr_ai_agent
   set timeout_ms = 25000, update_by = 'admin', update_time = now()
 where agent_code = 'catalog_extractor'
   and (timeout_ms is null or timeout_ms > 25000);

-- 3、说明（失败时的兜底方式）
update abr_ai_agent
   set remark = '失败时手动录入；AI 预填只提议取值，录入者逐字段确认后才保存'
 where agent_code = 'catalog_extractor'
   and remark is distinct from '失败时手动录入；AI 预填只提议取值，录入者逐字段确认后才保存';

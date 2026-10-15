# RuoYi 落地对照（研发文档的实施口径）

> 2026-10-13 决定：**沿用 RuoYi 脚手架**（与 zt-vision-parent / zt-vue 同一套：RuoYi-Vue 3.9.2、JDK 17、Spring Boot 4、MyBatis、PostgreSQL 15、Vue 3 + Element Plus），不再按《技术选型》原方案新建 `abroad-*` 多模块工程。
>
> `01`–`05` 是在线研发文档的导出稿，业务内容（功能点、验收标准、接口范围、页面、任务卡）继续有效；其中与脚手架冲突的**技术口径一律按本文**执行。代码规范以 `doc/开发规范.md` 为准。

## 1 工程与模块

| 原设计 | 落地 |
| --- | --- |
| 单仓库 monorepo（后端 + 管理端 + 学生端） | 三个仓库：后端 `zt-abroad-parent`、管理端 `zt-abroad-vue`；学生端（uni-app 小程序 + H5）在 M1-06 时另建 `zt-abroad-uniapp` |
| 包名 `com.xx.abroad` | `com.ygb.abroad`（与 zt 工程同一公司包名） |
| `abroad-app` 启动模块 | `zt-admin`（启动类 `ZtApplication`，控制器在 `com.ygb.web.controller.abroad`；学生端接口在 `com.ygb.web.controller.app`） |
| `abroad-common` / `abroad-system` | RuoYi 的 `zt-common` / `zt-system` / `zt-framework`（直接复用，不改动） |
| `abroad-catalog` / `abroad-assessment` / `abroad-crm` | 合并为一个业务模块 `zt-abroad`，按层分包（`domain`/`mapper`/`service`），类名带业务前缀区分领域（如 `AbrSchool`、`AbrAssessment`、`AbrLead`） |
| `abroad-ai` | `zt-abroad` 内的 `com.ygb.abroad.ai` 包（AI 网关） |
| `abroad-integration` | `zt-abroad` 内的 `com.ygb.abroad.integration` 包 |
| 选校引擎 | `zt-abroad` 内的 `com.ygb.abroad.engine` 包（纯 Java，规则见开发规范 14.1） |
| 1b 的 content / delivery / essay | 仍放 `zt-abroad`；体量变大后再按开发规范 3.1 拆出 `zt-<业务名>` 模块 |
| ArchUnit 依赖检查 | 暂不引入；模块依赖方向按开发规范 2.1 在评审中检查 |

## 2 数据库

| 原设计 | 落地 |
| --- | --- |
| PostgreSQL 16 + pgvector | PostgreSQL 15（与 zt 工程一致）；1b 需要向量检索时评估 pgvector 扩展 |
| Flyway 迁移 | 不用 Flyway：全量脚本 `sql/zt_20261008_postgre.sql` + 增量脚本 `sql/abroad/update_<yyyyMMdd>_<内容>.sql`（可重复执行，见 `sql/abroad/README.md`）；《架构设计》中“启动时由 Flyway 自动迁移”改为部署时先执行 `sql/abroad/migrate.sh`（按文件名顺序全部重跑），compose 空库首次启动自动执行 |
| MyBatis-Plus | RuoYi 的 MyBatis + XML + PageHelper；单表 CRUD 用代码生成器生成 |
| 表前缀 `cat_` `asm_` `crm_` `ai_` `itg_` | 统一 `abr_`：`cat_school` → `abr_school`，`asm_assessment` → `abr_assessment`，`asm_result` → `abr_assessment_result`，`crm_lead` → `abr_lead`，`ai_call_log` → `abr_ai_call_log`，`itg_wecom_contact` → `abr_wecom_contact`，依此类推 |
| 必备字段 `id`、`tenant_id`、`created_by/at`、`updated_by/at`、`deleted` | RuoYi 口径：`xxx_id bigserial`、`status`、`del_flag`、`create_by/create_time`、`update_by/update_time`、`remark`（开发规范第 5 章） |
| `tenant_id` 多租户 | **取消**。定制输出按“每家客户独立部署一套”，不做多租户 |
| `sys_tenant` | 取消；品牌配置用 RuoYi 参数配置 `sys_config` |
| `sys_user` / `sys_role` / 权限表 | 直接用 RuoYi 的 `sys_user`、`sys_role`、`sys_menu`（权限标识 `abroad:资源:操作`）；3 个预置角色写在增量脚本里 |
| `sys_audit_log` | RuoYi 操作日志 `sys_oper_log`（`@Log`）+ 登录日志；查看手机号明文另加 `@Log` |
| `sys_dict` | RuoYi 字典 `sys_dict_type` / `sys_dict_data`（类型 `abr_<含义>`） |
| `sys_event` | `abr_event`（事件记录表） |

## 3 接口与鉴权

| 原设计 | 落地 |
| --- | --- |
| 管理端 `/admin/api/<模块>/<资源>` | `/abroad/<资源>`（如 `/abroad/school`、`/abroad/lead`），REST 约定按开发规范 4.1（`/list`、`/{id}`、`/export`…） |
| 管理端 `/auth/login`、`/auth/refresh` | RuoYi 现成的 `/login`、`/getInfo`、`/getRouters`（令牌自动续期） |
| 统一响应 `{code,message,data,traceId}` | RuoYi 的 `AjaxResult` / `TableDataInfo`（`{code,msg,data}`） |
| 学生端 `/app/api/**` 独立令牌 | `/app/**`：在 `SecurityConfig` 中放行该前缀，由 `zt-abroad` 自带的学生令牌过滤器校验（Redis key 前缀 `abr_app_token:`），与员工令牌互不通用；在 M4 任务卡中实现 |
| 外部回调 `/callback/**` | `/callback/**`（企微、小程序回调），`@Anonymous` + 验签 |
| OpenAPI → openapi-typescript 生成前端代码 | springdoc 保留；管理端沿用 RuoYi 手写 `src/api/<模块>/<资源>.js`（或代码生成器生成） |

## 4 前端

| 原设计 | 落地 |
| --- | --- |
| 管理端 Vue 3 + TypeScript | `zt-abroad-vue`（RuoYi-Vue3，JavaScript），规范见其 `doc/前端开发规范.md` |
| 员工、角色、菜单、字典、日志页面 | RuoYi 现成页面，不再开发 |
| 学生端 uni-app | C 端独立工程 `zt-abroad-uniapp`（Vue 3，先 H5 后小程序），只给学生和家长，只调 `/app/**`；管理后台 `zt-abroad-vue` 只给员工；公开项目页由后端生成静态 HTML。见《模块拆分·端划分》 |

## 5 开发计划的调整

| 任务卡 | 调整 |
| --- | --- |
| M0-03 建仓库、`CLAUDE.md`、导出研发文档 | 已完成（本次）；CI 流水线另做 |
| M1-01 后端多模块工程 | 由脚手架完成；只剩“JSON 日志”按需 |
| M1-02 租户上下文 | 取消 |
| M1-03 员工登录、JWT、角色权限 | 由脚手架完成；只剩“预置 3 个角色 + 顾问只看自己线索”（用 RuoYi 数据权限 `@DataScope` 或按负责人过滤），并入 M5 |
| M1-04 审计日志、字典、事件表 | 审计、字典由脚手架完成；只剩 `abr_event` 与事件发布 |
| M1-05 管理端工程 | 由 `zt-abroad-vue` 完成 |
| M1-06 学生端工程 | 不变 |
| M1-07 部署脚本 | 本地版 `docker-compose.yml`；生产 / 测试版在 `deploy/`（按域名分流的 Nginx、每日备份与恢复脚本、一键部署 `deploy.sh`，2026-10-15）；测试环境自动部署、异地备份、HTTPS 证书等服务器与账号就绪（M0-01）后补 |
| 其余任务卡 | 不变，只按本文替换表名、路径与技术口径 |

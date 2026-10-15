# zt-abroad-parent 项目说明（项目级规范）

本项目团队开发规范为 `doc/开发规范.md`，**所有代码修改、新增模块、SQL 脚本都必须遵守**，与本文件冲突时以开发规范为准：

@doc/开发规范.md

## 关键约定速查

- 产品：留学智能体平台（新成立的 10 人以内创业型留学工作室自用，验证后“标准产品 + 定制交付”输出；主做美、英、澳、新、港、日，韩、德保留；**全学段：美高、美本、硕士、博士**，学段为一等维度，未成年人以家长为账号主体）。1a 重心是**获客系统，由 Agent 全自动执行**（`doc/产品/06-获客系统设计.md`）：产品本身是第一获客渠道（免费主产品“我的申请规划”，核心结果不设留资墙），不做付费投放。系统主要由 AI 编写，按任务卡逐张开发（`doc/研发/05-开发计划.md`）。
- 设计文档：产品设计 `doc/产品/`；研发文档 `doc/研发/`。**技术口径以 `doc/研发/00-RuoYi落地对照.md` 为准**（01–05 为在线文档导出稿，其中 `abroad-*` 模块、`cat_/asm_/crm_` 表前缀、`tenant_id`、Flyway、MyBatis-Plus、`/admin/api` 均已按对照表替换）。
- 基于 RuoYi-Vue 3.9.2（与 `zt-vision-parent` 同一套脚手架）；Maven 根项目 `com.ygb:zt-abroad-parent`；启动模块 `zt-admin`；业务模块 `zt-abroad`。
- 留学业务统一放在 `zt-abroad`：包 `com.ygb.abroad`，控制器在 `zt-admin`：管理端 `com.ygb.web.controller.abroad`（`/abroad/**`，只给员工），C 端 `com.ygb.web.controller.app`（`/app/**`，学生令牌 + `abr_app_user`，只返回专用 VO），回调 `com.ygb.web.controller.callback`，表前缀 `abr_`，权限 `abroad:资源:操作`，路径 `/abroad/资源`，字典 `abr_含义`。
- 获客自动化在 `com.ygb.abroad.growth`：不做付费投放，靠情报（院校 / 政策变化、需求信号）驱动自然流量；对外内容和消息发出前必须过合规闸门、频控、AI 预算闸，支持熔断与一键暂停；情报采集只抓白名单官方页面；价格、产品、剧本、词表都是配置，不写死。
- 选校引擎在 `com.ygb.abroad.engine`：纯 Java、确定性，不访问数据库、不调用大模型；单元测试覆盖率 ≥ 90%。学生只看冲 / 稳 / 保，不展示概率百分比。
- 大模型只能经 `com.ygb.abroad.ai` 网关调用（脱敏、schema 校验、调用日志、审核策略）；审核策略按 Agent 配置：报告解读直接展示 + 抽检，个性化建议类内容默认顾问确认后发出；AI 内容一律标明“AI 生成”。
- 不做多租户（定制输出按客户独立部署），表中不加 `tenant_id`。
- 数据库 **PostgreSQL 15**；写 SQL 禁止 MySQL 语法；char 列比较加引号；String/boolean 写入数值列要 `cast`；时间区间参数要 `cast(... as date/timestamp)`；种子数据后重置序列。业务增量脚本放 `sql/abroad/`（见其 README）。
- 修改既有文件时匹配周边代码风格（大括号另起一行、中文 Javadoc、`@Autowired` 字段注入）。
- `*.bat` 文件保持 GBK + CRLF，编辑时按字节处理（如 `LC_ALL=C sed`），不要用会转码的工具重写。
- `@author ruoyi`、README 中的上游署名保留不改。
- 前端分两端：**管理后台**为同级目录 `../zt-abroad-vue`（Vue3 + Vite + Element Plus，只给工作室员工），开发代理 `/dev-api` → `http://localhost:8090`，规范见 `../zt-abroad-vue/doc/前端开发规范.md`；**C 端**（学生、家长）为独立工程 `../zt-abroad-uniapp`（uni-app，先 H5 后小程序，M1-06 已建，规范见其 `doc/前端开发规范.md`）；公开项目页由后端生成静态 HTML。端划分见 `doc/研发/04-模块拆分.md`「端划分」。

## 构建与验证

- 需要 JDK 17+：`mvn -q -B package -Dmaven.test.skip=true`；本机只有 JDK 8 时用 Docker：
  `docker run --rm -v "$PWD":/src -v ~/.m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-17 mvn -q -B package -Dmaven.test.skip=true`
- 本地运行：`docker compose up -d --build`（`abr-postgres` 5433 / `abr-redis` 6380 / `abr-admin` 8090，与 zt-vision-parent 错开）；数据在 `abr-pgdata` 卷，空库首次启动自动执行 `sql/zt_20261008_postgre.sql` + `sql/quartz_postgre.sql`。
- 涉及 SQL 的改动，须在 PostgreSQL 上实际执行验证（建临时库验证后删除）；仅编译通过不算完成。

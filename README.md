<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">zt-abroad-parent</h1>
<h4 align="center">留学智能体平台 · 后端</h4>

## 平台简介

面向小型留学工作室的智能体平台：智能选校测评获客 → 线索与顾问工作台 → 服务交付，先内部使用，验证后以“标准产品 + 定制交付”输出。

* 基于 RuoYi-Vue 3.9.2 脚手架（与 zt-vision-parent 同一套）：JDK 17、Spring Boot 4、Spring Security + JWT、MyBatis、PostgreSQL 15、Redis。
* 管理端前端：同级目录 `../zt-abroad-vue`（Vue 3 + Element Plus），开发代理 `/dev-api` → `http://localhost:8090`。
* 业务模块 `zt-abroad`（包 `com.ygb.abroad`，表前缀 `abr_`，权限 `abroad:资源:操作`，路径 `/abroad/资源`）。

## 快速开始

```bash
# 构建（JDK 17+）
mvn clean package -Dmaven.test.skip=true

# 本地一键运行：PostgreSQL(5433) + Redis(6380) + 后端(8090)，端口与 zt-vision-parent 错开
docker compose up -d --build
docker compose logs -f abr-admin
```

默认账号 `admin / admin123` **仅限本地**。数据库与运行细节见 [doc/readme.md](doc/readme.md)。

## 文档

| 文档 | 说明 |
| --- | --- |
| [doc/开发规范.md](doc/开发规范.md) | 后端开发规范（**强制**），第 14 章为留学业务补充规范 |
| [doc/产品/](doc/产品/) | 产品设计 v6、模块详细设计、冷启动、顾问工作台、设计盘点、竞品调研 |
| [doc/研发/00-RuoYi落地对照.md](doc/研发/00-RuoYi落地对照.md) | 研发文档在 RuoYi 脚手架上的实施口径（优先于 01–05 中的技术口径） |
| [doc/研发/](doc/研发/) | 开发规范（原稿）、架构设计、技术选型、模块拆分（功能点与验收标准、接口、页面）、开发计划（任务卡） |
| [sql/abroad/README.md](sql/abroad/README.md) | 业务增量脚本约定 |

在线版（含架构图、里程碑图）：[产品设计](https://claude.ai/code/artifact/3f423242-8dd1-4136-99ec-c924c5bddfc5)、[研发文档](https://claude.ai/code/artifact/200d60ea-485b-410e-9a16-b59812fb71a8)。

## 模块

| 模块 | 说明 |
| --- | --- |
| `zt-admin` | 启动入口、全部 Web 控制器、配置文件 |
| `zt-framework` | Security、JWT、数据源、AOP、全局异常、Redis |
| `zt-system` | 用户、角色、菜单、部门、岗位、字典、参数、公告、日志 |
| `zt-quartz` | 定时任务 |
| `zt-generator` | 代码生成（已配置为 `com.ygb.abroad` / `abr_`） |
| `zt-common` | 通用工具、注解、基类 |
| `zt-abroad` | 留学业务：domain、mapper、service、选校引擎 `engine`、AI 网关 `ai`、外部集成 `integration`、定时任务 `task` |

## 脚手架内置功能（RuoYi）


1.  用户管理：用户是系统操作者，该功能主要完成系统用户配置。
2.  部门管理：配置系统组织机构（公司、部门、小组），树结构展现支持数据权限。
3.  岗位管理：配置系统用户所属担任职务。
4.  菜单管理：配置系统菜单，操作权限，按钮权限标识等。
5.  角色管理：角色菜单权限分配、设置角色按机构进行数据范围权限划分。
6.  字典管理：对系统中经常使用的一些较为固定的数据进行维护。
7.  参数管理：对系统动态配置常用参数。
8.  通知公告：系统通知公告信息发布维护。
9.  操作日志：系统正常操作日志记录和查询；系统异常信息日志记录和查询。
10. 登录日志：系统登录日志记录查询包含登录异常。
11. 在线用户：当前系统中活跃用户状态监控。
12. 定时任务：在线（添加、修改、删除)任务调度包含执行结果日志。
13. 代码生成：前后端代码的生成（java、html、xml、sql）支持CRUD下载 。
14. 系统接口：根据业务代码自动生成相关的api接口文档。
15. 服务监控：监视当前系统CPU、内存、磁盘、堆栈等相关信息。
16. 缓存监控：对系统的缓存信息查询，命令统计等。
17. 在线构建器：拖动表单元素生成相应的HTML代码。
18. 连接池监视：监视当前系统数据库连接池状态，可进行分析SQL找出系统性能瓶颈。


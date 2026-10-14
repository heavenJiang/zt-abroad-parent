# 接口回归测试

针对运行中的后端执行，用于任务卡验收与回归。只用 Python 标准库。

前提：
- 后端已启动（默认 `http://localhost:8090`，可用环境变量 `ABR_BASE` 指向其他地址）；
- 数据库已执行 `sql/` 全量脚本与 `sql/abroad/` 增量脚本；
- 关闭登录验证码（测试库执行 `update sys_config set config_value='false' where config_key='sys.account.captchaEnabled'`），默认账号 admin / admin123。

**只能对测试库执行**：脚本会新增并删除院校、项目数据；AI 网关脚本会写调用日志、审核记录，并临时修改 `_selftest` Agent 的审核策略（结束时恢复）。

| 脚本 | 覆盖 |
| --- | --- |
| `m2_catalog_test.py` | M2-01 院校 / 项目 / 申请要求 / 字段来源；M2-03 国内高校搜索 |
| `m2_ai_gateway_test.py` | M2-04 / M2-05 AI 网关：Agent 配置、试用接口（mock 模型）、主备切换、Schema 重试与降级、输出过滤、去标识化、审核队列（通过 / 驳回 / 修改）、抽检、调用日志筛选与用量统计 |

```bash
python3 -I test/api/m2_catalog_test.py
ABR_BASE=http://localhost:8091 python3 -I test/api/m2_ai_gateway_test.py
```

AI 评测命令（不经 HTTP，跑 `prompts/<agent>/<版本>/eval/` 的样例，报告写到 `./ai-eval/`，全部通过退出码 0）：

```bash
java -jar zt-admin/target/zt-admin.jar --abroad.ai.eval=_selftest
```

# 接口回归测试

针对运行中的后端执行，用于任务卡验收与回归。只用 Python 标准库。

前提：
- 后端已启动（默认 `http://localhost:8090`，可用环境变量 `ABR_BASE` 指向其他地址）；
- 数据库已执行 `sql/` 全量脚本与 `sql/abroad/` 增量脚本；
- 关闭登录验证码（测试库执行 `update sys_config set config_value='false' where config_key='sys.account.captchaEnabled'`），默认账号 admin / admin123。

**只能对测试库执行**：脚本会新增并删除院校、项目数据。

| 脚本 | 覆盖 |
| --- | --- |
| `m2_catalog_test.py` | M2-01 院校 / 项目 / 申请要求 / 字段来源；M2-03 国内高校搜索 |

```bash
python3 -I test/api/m2_catalog_test.py
```

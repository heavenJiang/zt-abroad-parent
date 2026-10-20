# 提示词目录

AI 网关（`com.ygb.abroad.ai`）从 classpath 的本目录加载提示词，代码中不写提示词。

```
prompts/
├── _filters/                 输出过滤词表（每行一个词，# 注释，“# 类别：xxx” 为类别名称，! 开头为例外短语）
│   ├── admission_promise.txt   录取承诺
│   └── ad_banned.txt           广告违禁词
└── <agentCode>/<版本>/        版本即 abr_ai_agent.prompt_version（如 v1）
    ├── system.md              系统提示词
    ├── user.md                用户提示词模板：{{变量}} 取输入（支持 a.b 路径；对象、数组填 JSON）；{{facts}} 为检索到的事实
    ├── schema.json            输出 JSON Schema（type / properties / required / items / enum / minimum / maximum /
    │                          minLength / maxLength / additionalProperties:false / ["string","null"]）
    ├── mock.json              可选：mock 模型返回的固定输出（字符串中的 {{变量}} 用去标识化后的输入渲染）
    └── eval/*.json            评测样例：{name, input, knownNames, facts, expectStatus, expect:[{path, equals|contains|exists|notExists}]}
                               input 中可带 _mockOutput（JSON 对象）：mock 模型返回它而不是 mock.json，便于同一 Agent 的多个样例各有 mock 输出；
                               真实模型忽略该键（样例的输入、断言对真实模型同样有效）
```

- 改提示词就新建版本目录（v2…），在管理端「Agent 配置」切换生效版本；旧版本保留便于回滚。
- 改提示词或模型前后都跑评测：`java -jar zt-admin.jar --abroad.ai.eval=<agentCode> [--abroad.ai.eval-provider=<provider:model>]`，
  报告写到 `./ai-eval/<agentCode>-<yyyyMMddHHmmss>.md`，全部通过退出码 0，否则 1。
- `_selftest` 是网关自测 Agent（业务停用，试用与评测可用），用来演示与回归整条管道。
- 业务 Agent（report_explainer、resume_parser、lead_card、catalog_extractor）的提示词由各自任务卡编写；目录不存在时调用返回 error。
- `catalog_extractor/v1`（M2-06 项目 AI 预填）：输入 `url`、`programHint`、`pageTitle`、`pageText`（官网页面转纯文本，最多 30000 字）；
  输出每个字段 `{value, quote}`。调用方 `AbrProgramAiPrefillServiceImpl` 事后逐字段核对：引句不在页面原文中、取值不符合人工保存规则的一律丢弃，
  所以提示词只负责“尽量逐字引用、没有就填 null”，不要依赖模型自觉。eval/ 的三个样例：英国 MSc（学费、雅思、按层次均分、轮次截止日）、滚动录取、页面没有语言要求。
- `report_explainer/v1`（M4-06 测评解读）：输入 `report`（报告中学生可见的内容：各档数量、代表校的档位 / 已达标 / 未达标 / 补足方式、短板含义）与 `templateHeadline`（模板定位）；
  输出 `headline`、`programs[{programId, reason}]`、`shortcomings[{index, text}]`。调用方 `AbrReportExplanationServiceImpl` 在后台生成并逐条校验（项目须来自输入、
  数字须在输入中出现、不得有百分比或“概率”、不超长），不合格的条目改用模板文字；mock.json 只回模板定位，学校理由与短板全部走模板。
  eval/：正常解读、录取承诺被输出过滤拦截。
- `resume_parser/v1`（M4-07 简历解析）：输入 `kind`（简历 / 成绩单）与 `text`（PDF 文字层或文字识别结果，已去掉出生日期、住址、证件号、学号等，最多 12000 字；姓名、手机号等由网关替换为占位符）；
  输出 `school{name, overseas}`、`major`、`gpa{type, total, max}`、`english{type, total, min_sub}`、`research{count, level}`、`work{count, total_months, fulltime_months}`、`awards{count, level}`，没有的填 null。
  调用方 `AbrResumeServiceImpl` 逐项校验（校名、专业须在原文中逐字出现；分数须在原文中出现且符合问卷分制范围与步长；级别须是问卷选项），不合格的字段丢弃，结果只作建议值由学生确认。
  mock.json 返回一份固定样例，校验会丢掉原文中没有的项。eval/：中文简历、成绩单只有 4 分制绩点、没有可填内容。
- `growth_analyst/v1`（M9-04 增长周报）：输入 `data`（系统算好的周数据：本周与上周漏斗、每周活跃选校单、最好 / 最差的内容、近 28 天各内容类型表现、熔断、A/B 实验、内容配比调整）；
  输出 `summary`、`highlights[]`、`suggestions[]`。调用方 `AbrGrowthAnalystServiceImpl` 校验输出中的每个数字都在数据中原样出现（不允许模型自己算比率），不合格整份改用模板文字；
  审核策略内部可见。mock.json 用输入渲染一段固定摘要。eval/：基本周报。

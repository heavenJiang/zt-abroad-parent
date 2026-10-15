# 示例案例（cases_sample.csv）的来源与口径

> **这是 AI 构造的示例集，不是真实录取案例。** 项目与申请要求是真实的（取自院校官网，见下表），学生背景是虚构的典型中国申请者，冲 / 稳 / 保档位由 AI 按公开的项目竞争程度判断标注，`outcome` 为空（没有真实录取结果）。
> 档位在引擎第一次运行这些案例**之前**写定并单独提交（见 git 历史），之后不按引擎结果修改；测试台的命中率只说明“引擎与 AI 经验判断的一致程度”，**不能当作真实准确率，也不作为上线门槛**。真实准确率只能用顾问提供的真实案例（`cases.csv`，A / B / C 级）衡量。

## 列的补充

在模板列之外增加了 6 列（真实案例也可以填，用于检验硬门槛）：

| 列 | 含义 |
| --- | --- |
| `req_min_gpa` | 该项目对**该学生所在层次**的最低百分制均分（官网公布；空 = 官网未公布） |
| `req_ielts_overall` / `req_ielts_min_sub` | 官网公布的雅思总分 / 单项最低分（空 = 未公布或未能核实） |
| `req_verified` | `Y` 已在官网页面核实；`N` 只见于搜索摘要、官网正文未能取到 |
| `req_source` | 下表中的项目键 |
| `expected_threshold` | 按官网要求（不经引擎）判断该学生是否达标：`pass` / `needs_improvement`。官网未公布的项不判 |

`verification_level = X`：AI 构造、无真实结果（新增级别，只用于示例集）。

## 项目要求来源（2026-10-15 查询）

| 键 | 项目 | 官网要求（与本表填写口径） | 来源 |
| --- | --- | --- | --- |
| ED-MGT | The University of Edinburgh · MSc Management | UK 2:1 或以上；中国：2:1 对应 Priority List 院校 80%+、其他认可院校 80%–85%+（本表 Priority List 按 C9 / 985 / 211 近似，其他按 85% 取上限）；IELTS 7.0，单项 6.0 | [study.ed.ac.uk 中国入学要求](https://study.ed.ac.uk/programmes/regions/china)、[MSc Management](https://study.ed.ac.uk/programmes/postgraduate-taught/277-management) |
| ED-FIN | The University of Edinburgh · MSc Finance and Investment | 同上（2:1，相关专业，至少一门定量课程）；IELTS 7.0，单项 6.0；学院页说明项目竞争激烈、超过最低线才有竞争力 | [MSc Finance and Investment](https://study.ed.ac.uk/programmes/postgraduate-taught/1128-finance-and-investment)、[商学院入学要求](https://www.business-school.ed.ac.uk/msc/finance-investment/entry-requirements) |
| MAN-ACS | The University of Manchester · MSc Advanced Computer Science | 中国：排名较好院校 4 年制本科 85% 以上（相当于一等）；IELTS 7.0，单项不低于 6.5 | [Entry requirements](https://www.manchester.ac.uk/study/masters/courses/list/21573/msc-advanced-computer-science/entry-requirements/) |
| HKU-MFIN | The University of Hong Kong · Master of Finance | 认可的学士学位，未公布均分线；IELTS 6.0，单项不低于 5.5（或 TOEFL 80）；GMAT / GRE 不要求 | [Admissions Requirements](https://www4.fbe.hku.hk/mfin/admissions/admissions-requirements) |
| CUHK-FIN | The Chinese University of Hong Kong · MSc in Finance | 二等荣誉学士或平均 B；IELTS 6.5（或 TOEFL 79）；GMAT / GRE 不强制 | [MSc in Finance](https://masters.bschool.cuhk.edu.hk/programmes/mscfin/)、[研究院入学要求](https://www.gs.cuhk.edu.hk/admissions/requirements) |
| NUS-FIN | National University of Singapore · MSc Finance | “good undergraduate degree”，官方 FAQ 明确没有固定均分线；IELTS 7.0 **只见于搜索摘要**，官网正文未能取到，`req_verified = N` | [FAQs](https://mscfin.nus.edu.sg/faqs/) |
| UNSW-MCOM | UNSW Sydney · Master of Commerce | WAM 65%（credit average）；中国非 211 院校需本校均分 88（≈ 澳洲 85%）；本表对双一流 / 双非填 88，211 及以上未填（65% 澳洲口径与国内百分制不能直接对应）；英语要求页面未列出，未填 | [Master of Commerce](https://www.unsw.edu.au/study/postgraduate/master-of-commerce) |
| MEL-MMF | The University of Melbourne · Master of Management (Finance) | 任何专业本科、WAM 等效要求未公布具体数字；**主修金融的申请人不予录取**；英语 band 7、单项不低于 6.5（2025 手册） | [Handbook 2025](https://handbook.unimelb.edu.au/2025/courses/mc-mgmtfin/entry-participation-requirements) |

难度档 D1–D5 是 AI 按公开竞争程度给的判断（与 M2-08 顾问定档是同一套口径，需顾问复核）。

## 已知的模型局限（构造时就预期引擎会出错的地方）

- **官网没有均分线的项目**（港大、NUS）：引擎会对空字段套用难度档兜底均分并标“要求待核实”，可能把官网上达标的学生判为“补足后可申”。这是 M3-04 “按字段兜底”口径的直接后果，测试台会把这类差异列为硬门槛不一致。
- **墨尔本“主修金融者不予录取”**：引擎没有“排除某些本科专业”的规则，示例中没有放金融专业学生申请该项目的行。

## 首次运行结果（2026-10-15，引擎参数各国 `draft.2`，未做任何调整）

测试台：`mvn test` 中的 `AbrEngineCaseBenchTest` 会打印同一份报告；管理端「引擎配置 → 测试台」上传本文件得到相同结果。

| 范围 | 行数 | 参与档位统计 | 整体命中 | 冲 | 稳 | 保 | 严重错档 | 引擎判补足后可申 | 硬门槛与官网一致 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 全部 | 27 | 11 | 6/11（54.5%） | 2/2 | 0/4 | 4/5 | 0 | 16 | 15/27 |
| 英国 | 9 | 7 | 4/7 | 2/2 | 0/3 | 2/2 | 0 | 2 | 8/9 |
| 中国香港 | 8 | 1 | 0/1 | – | – | 0/1 | 0 | 7 | 2/8 |
| 新加坡 | 3 | 0 | – | – | – | – | 0 | 3 | 0/3 |
| 澳洲 | 7 | 3 | 2/3 | – | 0/1 | 2/2 | 0 | 4 | 5/7 |

**发现（只记录，不在本卡调整；按 M0-06 第 4 节的顺序留给 M6-03 处理）：**

1. **硬门槛：官网没有均分线时被兜底值拦下（9 行，港、新）**。港大 MFin、港中文 MSc Finance、NUS MSc Finance 官网都没有均分线，但引擎对空的均分字段套用难度档兜底（如香港 D1 211 要 87+），把 9 个按官网达标的学生判为“补足后可申”。这是 M3-04“按字段兜底”口径的直接后果：项目库现在分不清“官网明确不设线”和“还没录入”。建议：申请要求增加“无均分线”的明确取值，项目库记录了“不设线”时不兜底。
2. **硬门槛：语言差 0.5 被当作“可读语言班”（3 行，英、澳）**。爱丁堡、墨尔本官网写的是最低雅思，引擎按国家规则把差 0.5 以内的判为可读语言班（不拦），示例集的 `expected_threshold` 按官网最低分严格判断。两者口径不同，需要顾问定：这类学生在报告里算“补足后可申”还是“可读语言班”；项目库的“是否接受语言班”字段填好后可消除这类不确定。
3. **档位：引擎的分差比经验判断拉得开（稳档 0/4）**。211 均分 82（爱丁堡管理，D2）引擎判冲（P 0.11）、AI 判稳；985 均分 88（爱丁堡金融，D1）引擎判保、AI 判稳。原因是英国学术维度权重 0.65 且均分每 1 分计 3 分，均分差 6 分 → 综合分差约 12 分；稳档区间对应的综合分很窄（a = 0.18 时 P 0.35–0.70 只对应约 ±3.4 分）。需要真实案例判断是权重 / 斜率过陡，还是 AI 的经验判断偏平。
4. **没有严重错档**（冲 ↔ 保 0 条）。

再次说明：这些档位标注没有真实录取结果支撑，以上数字只能说明引擎与 AI 经验判断的差异在哪里，不能说明引擎准或不准。

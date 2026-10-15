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

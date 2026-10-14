package com.ygb.abroad.ai.eval;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiException;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiJson;
import com.ygb.abroad.ai.AiPromptLoader;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.ai.AiStore;
import com.ygb.abroad.domain.AbrAiAgent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * Agent 评测：把 prompts/&lt;agent&gt;/&lt;生效版本&gt;/eval/*.json 的样例逐个经网关执行，按断言出 Markdown 报告
 * <p>
 * 样例格式：{"name": "...", "input": {...}, "knownNames": ["张三"], "facts": [...], "expectStatus": "success",
 * "expect": [{"path": "/level", "equals": "ok"}, {"path": "/tags", "contains": "x"}, {"path": "/a", "exists": true},
 * {"path": "/b", "notExists": true}]}；path 为 JSON Pointer，作用于还原占位符后的输出。
 * </p>
 *
 * @author ygb
 */
public class AiEvalRunner
{
    private final AiGateway gateway;

    private final AiPromptLoader promptLoader;

    private final AiStore store;

    public AiEvalRunner(AiGateway gateway, AiPromptLoader promptLoader, AiStore store)
    {
        this.gateway = gateway;
        this.promptLoader = promptLoader;
        this.store = store;
    }

    /**
     * 执行评测
     *
     * @param agentCode Agent 编码
     * @param modelOverride 指定模型（provider:model），为空用 Agent 配置的主备模型
     * @param operator 操作人（写调用日志）
     * @return 报告
     */
    public Report run(String agentCode, String modelOverride, String operator)
    {
        AbrAiAgent agent = store.selectAgentByCode(agentCode);
        if (agent == null)
        {
            throw new AiException("Agent「" + agentCode + "」不存在");
        }
        Report report = new Report();
        report.agentCode = agentCode;
        report.promptVersion = agent.getPromptVersion();
        report.model = modelOverride != null && !modelOverride.isBlank() ? modelOverride
                : agent.getPrimaryModel() + (agent.getBackupModel() == null || agent.getBackupModel().isBlank() ? "" : "（备用 " + agent.getBackupModel() + "）");
        report.startTime = new Date();
        // 提示词目录缺失时直接报出（AiException）
        promptLoader.load(agentCode, agent.getPromptVersion());
        List<Map.Entry<String, JsonNode>> cases = promptLoader.loadEvalCases(agentCode, agent.getPromptVersion());
        if (cases.isEmpty())
        {
            throw new AiException("Agent「" + agentCode + "」的 prompts/" + agentCode + "/" + agent.getPromptVersion() + "/eval/ 下没有评测样例");
        }
        for (Map.Entry<String, JsonNode> entry : cases)
        {
            report.cases.add(runCase(agentCode, entry.getKey(), entry.getValue(), modelOverride, operator));
        }
        return report;
    }

    @SuppressWarnings("unchecked")
    private CaseResult runCase(String agentCode, String file, JsonNode spec, String modelOverride, String operator)
    {
        CaseResult cr = new CaseResult();
        cr.file = file;
        cr.name = spec.path("name").asString(file);
        cr.expectStatus = spec.path("expectStatus").asString(AiConstants.STATUS_SUCCESS);
        Map<String, Object> input = spec.has("input") ? AiJson.MAPPER.convertValue(spec.get("input"), LinkedHashMap.class) : new LinkedHashMap<>();
        AiContext ctx = AiContext.of(AiConstants.BIZ_EVAL, file, operator);
        ctx.setEvaluation(true);
        ctx.setModelOverride(modelOverride);
        List<String> names = new ArrayList<>();
        for (JsonNode n : spec.path("knownNames"))
        {
            names.add(n.asString());
        }
        ctx.setKnownNames(names);
        if (spec.has("facts"))
        {
            ctx.setFacts(AiJson.MAPPER.convertValue(spec.get("facts"), List.class));
        }
        AiResult result = gateway.invoke(agentCode, input, ctx);
        cr.result = result;
        cr.assertions.add(new AssertionResult("状态 = " + cr.expectStatus, cr.expectStatus.equals(result.getStatus()),
                result.getStatus() + (result.isSuccess() ? "" : "（" + result.getMessage() + "）")));
        for (JsonNode a : spec.path("expect"))
        {
            cr.assertions.add(check(result.getOutput(), a));
        }
        return cr;
    }

    /**
     * 执行一条断言
     *
     * @param output 输出（可为 null）
     * @param a 断言定义
     * @return 断言结果
     */
    public static AssertionResult check(JsonNode output, JsonNode a)
    {
        String path = a.path("path").asString("");
        JsonNode actual;
        try
        {
            actual = output == null ? MissingNode.getInstance() : output.at(path);
        }
        catch (IllegalArgumentException e)
        {
            return new AssertionResult(path + " 路径不合法", false, e.getMessage());
        }
        boolean missing = actual == null || actual.isMissingNode();
        String shown = missing ? "（不存在）" : AiJson.truncate(actual.toString(), 200);
        if (a.has("equals"))
        {
            JsonNode expected = a.get("equals");
            return new AssertionResult(path + " equals " + expected, !missing && AiJson.jsonEquals(actual, expected), shown);
        }
        if (a.has("contains"))
        {
            JsonNode expected = a.get("contains");
            boolean pass = false;
            if (!missing && actual.isString())
            {
                pass = actual.asString().contains(expected.asString());
            }
            else if (!missing && actual.isArray())
            {
                for (JsonNode item : actual)
                {
                    if (AiJson.jsonEquals(item, expected))
                    {
                        pass = true;
                        break;
                    }
                }
            }
            else if (!missing && actual.isObject())
            {
                pass = actual.has(expected.asString());
            }
            return new AssertionResult(path + " contains " + expected, pass, shown);
        }
        if (a.has("exists"))
        {
            boolean want = a.get("exists").asBoolean(true);
            return new AssertionResult(path + (want ? " exists" : " notExists"), missing != want, shown);
        }
        if (a.has("notExists"))
        {
            boolean want = a.get("notExists").asBoolean(true);
            return new AssertionResult(path + (want ? " notExists" : " exists"), missing == want, shown);
        }
        return new AssertionResult("未知断言 " + a, false, "支持 equals / contains / exists / notExists");
    }

    /**
     * 断言结果
     */
    public static class AssertionResult
    {
        private final String description;

        private final boolean passed;

        private final String actual;

        public AssertionResult(String description, boolean passed, String actual)
        {
            this.description = description;
            this.passed = passed;
            this.actual = actual;
        }

        public String getDescription()
        {
            return description;
        }

        public boolean isPassed()
        {
            return passed;
        }

        public String getActual()
        {
            return actual;
        }
    }

    /**
     * 单个样例结果
     */
    public static class CaseResult
    {
        private String file;

        private String name;

        private String expectStatus;

        private AiResult result;

        private final List<AssertionResult> assertions = new ArrayList<>();

        public boolean isPassed()
        {
            return assertions.stream().allMatch(AssertionResult::isPassed);
        }

        public String getName()
        {
            return name;
        }

        public AiResult getResult()
        {
            return result;
        }

        public List<AssertionResult> getAssertions()
        {
            return assertions;
        }
    }

    /**
     * 评测报告
     */
    public static class Report
    {
        private String agentCode;

        private String promptVersion;

        private String model;

        private Date startTime;

        private final List<CaseResult> cases = new ArrayList<>();

        public boolean isPassed()
        {
            return !cases.isEmpty() && cases.stream().allMatch(CaseResult::isPassed);
        }

        public List<CaseResult> getCases()
        {
            return cases;
        }

        public String getAgentCode()
        {
            return agentCode;
        }

        /**
         * 一行摘要
         *
         * @return 摘要
         */
        public String summary()
        {
            long passedCases = cases.stream().filter(CaseResult::isPassed).count();
            long assertions = cases.stream().mapToLong(c -> c.assertions.size()).sum();
            long passedAssertions = cases.stream().flatMap(c -> c.assertions.stream()).filter(AssertionResult::isPassed).count();
            return String.format("Agent %s（提示词 %s）评测%s：样例 %d/%d 通过，断言 %d/%d 通过，token %d，费用 %s 元",
                    agentCode, promptVersion, isPassed() ? "通过" : "未通过", passedCases, cases.size(), passedAssertions, assertions,
                    totalTokens(), totalCost().toPlainString());
        }

        private long totalTokens()
        {
            return cases.stream().mapToLong(c -> (long) c.result.getPromptTokens() + c.result.getCompletionTokens()).sum();
        }

        private BigDecimal totalCost()
        {
            return cases.stream().map(c -> c.result.getCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * Markdown 报告
         *
         * @return Markdown 文本
         */
        public String toMarkdown()
        {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            StringBuilder md = new StringBuilder();
            md.append("# AI 评测报告：").append(agentCode).append("\n\n");
            md.append("- 结论：**").append(isPassed() ? "通过" : "未通过").append("**\n");
            md.append("- 提示词版本：").append(promptVersion).append("\n");
            md.append("- 模型：").append(model).append("\n");
            md.append("- 时间：").append(fmt.format(startTime)).append("\n");
            md.append("- 摘要：").append(summary()).append("\n\n");
            md.append("## 样例汇总\n\n");
            md.append("| 样例 | 结果 | 状态 | 模型 | 切换备用 | 调用次数 | 输入 token | 输出 token | 费用（元） | 耗时（ms） | 调用ID |\n");
            md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
            for (CaseResult c : cases)
            {
                AiResult r = c.result;
                md.append("| ").append(cell(c.name)).append(" | ").append(c.isPassed() ? "PASS" : "FAIL")
                        .append(" | ").append(r.getStatus()).append(" | ").append(cell(r.getModelUsed()))
                        .append(" | ").append(r.isFallbackUsed() ? "是" : "否").append(" | ").append(r.getAttempts())
                        .append(" | ").append(r.getPromptTokens()).append(" | ").append(r.getCompletionTokens())
                        .append(" | ").append(r.getCost().toPlainString()).append(" | ").append(r.getLatencyMs())
                        .append(" | ").append(r.getCallId()).append(" |\n");
            }
            for (CaseResult c : cases)
            {
                md.append("\n## ").append(c.name).append("（").append(c.file).append("）\n\n");
                if (!c.result.isSuccess())
                {
                    md.append("说明：").append(cell(c.result.getMessage())).append("\n\n");
                }
                md.append("| 断言 | 结果 | 实际值 |\n| --- | --- | --- |\n");
                for (AssertionResult a : c.assertions)
                {
                    md.append("| ").append(cell(a.description)).append(" | ").append(a.passed ? "PASS" : "FAIL")
                            .append(" | ").append(cell(a.actual)).append(" |\n");
                }
            }
            return md.toString();
        }

        private static String cell(String s)
        {
            return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
        }
    }
}

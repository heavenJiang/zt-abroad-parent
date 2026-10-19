package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 线索评分 lead_scorer 的规则计算（纯计算、确定性，不访问数据库、不调用大模型）
 * <p>
 * 每条启用的规则按线索的特征给分并写一句理由；总分按分级线得到 A / B / C。规则种类固定，分值与阈值来自 abr_lead_score_rule。
 * </p>
 *
 * @author ygb
 */
final class AbrLeadScorer
{
    /** 规则编码 */
    static final String CONTACT = "contact";

    static final String MOMENT = "moment";

    static final String INTAKE = "intake";

    static final String TOOLS = "tools";

    static final String PLAN = "plan";

    static final String PARENT = "parent";

    static final String SOURCE = "source";

    static final String RECENCY = "recency";

    static final String GRADE = "grade";

    /** 各规则允许的参数键 */
    static final Map<String, Set<String>> KEYS = Map.of(
            CONTACT, Set.of("phone", "wechat"),
            MOMENT, Set.of("imbalance", "requirement_unmet", "materials_behind", "parent_view", "repeated_edits", "cap"),
            INTAKE, Set.of("months_12", "months_18"),
            TOOLS, Set.of("assessment", "runs_3", "runs_1", "days"),
            PLAN, Set.of("items_3", "items_1"),
            PARENT, Set.of("share", "viewed"),
            SOURCE, Set.of("referral", "partner", "lecture", "wecom", "dm", "manual", "h5"),
            RECENCY, Set.of("days_7", "stale_30"),
            GRADE, Set.of("a", "b"));

    /**
     * 线索特征
     *
     * @param hasPhone 有手机号
     * @param hasWechat 微信登录过（有学生账号）
     * @param moments 发生过的转化时刻
     * @param monthsToIntake 距入学月数（不知道为 null）
     * @param assessmentSubmitted 提交过完整测评
     * @param recentToolRuns 近 N 天工具使用次数
     * @param planItems 选校单项目数
     * @param shareCreated 开启过家长分享
     * @param shareViewed 家长查看过
     * @param sources 进入过的来源
     * @param daysSinceLastTouch 距最近触达天数
     */
    record Features(boolean hasPhone, boolean hasWechat, Set<String> moments, Integer monthsToIntake, boolean assessmentSubmitted, int recentToolRuns,
            int planItems, boolean shareCreated, boolean shareViewed, Set<String> sources, long daysSinceLastTouch)
    {
    }

    /**
     * 评分结果
     *
     * @param score 总分
     * @param grade 等级
     * @param reasons 理由
     */
    record Result(int score, String grade, List<Map<String, Object>> reasons)
    {
    }

    private AbrLeadScorer()
    {
    }

    /**
     * 评分
     *
     * @param f 特征
     * @param rules 启用的规则：编码 → 参数（分级线必须有）
     * @return 结果
     */
    static Result score(Features f, Map<String, JsonNode> rules)
    {
        List<Map<String, Object>> reasons = new ArrayList<>();
        JsonNode p = rules.get(CONTACT);
        if (p != null)
        {
            if (f.hasPhone())
            {
                add(reasons, CONTACT, "留了手机号", p.path("phone").asInt(0), "已绑定或登记手机号");
            }
            if (f.hasWechat())
            {
                add(reasons, CONTACT, "微信登录", p.path("wechat").asInt(0), "可通过服务号联系");
            }
        }
        p = rules.get(MOMENT);
        if (p != null && !f.moments().isEmpty())
        {
            int sum = 0;
            List<String> names = new ArrayList<>();
            for (String m : f.moments().stream().sorted().toList())
            {
                sum += p.path(m).asInt(0);
                names.add(AbrLeadRules.MOMENTS.getOrDefault(m, m));
            }
            int cap = p.path("cap").asInt(Integer.MAX_VALUE);
            add(reasons, MOMENT, "转化时刻", Math.min(sum, cap), String.join("、", names) + (sum > cap ? "（本项上限 " + cap + " 分）" : ""));
        }
        p = rules.get(INTAKE);
        if (p != null && f.monthsToIntake() != null && f.monthsToIntake() >= 0)
        {
            int m = f.monthsToIntake();
            if (m <= 12)
            {
                add(reasons, INTAKE, "入学时间近", p.path("months_12").asInt(0), "距入学约 " + m + " 个月");
            }
            else if (m <= 18)
            {
                add(reasons, INTAKE, "入学时间较近", p.path("months_18").asInt(0), "距入学约 " + m + " 个月");
            }
        }
        p = rules.get(TOOLS);
        if (p != null)
        {
            if (f.assessmentSubmitted())
            {
                add(reasons, TOOLS, "完整测评", p.path("assessment").asInt(0), "提交过完整测评");
            }
            int days = p.path("days").asInt(30);
            if (f.recentToolRuns() >= 3)
            {
                add(reasons, TOOLS, "常用工具", p.path("runs_3").asInt(0), "近 " + days + " 天用了 " + f.recentToolRuns() + " 次工具");
            }
            else if (f.recentToolRuns() >= 1)
            {
                add(reasons, TOOLS, "用过工具", p.path("runs_1").asInt(0), "近 " + days + " 天用了 " + f.recentToolRuns() + " 次工具");
            }
        }
        p = rules.get(PLAN);
        if (p != null)
        {
            if (f.planItems() >= 3)
            {
                add(reasons, PLAN, "选校单", p.path("items_3").asInt(0), "选校单中有 " + f.planItems() + " 个项目");
            }
            else if (f.planItems() >= 1)
            {
                add(reasons, PLAN, "选校单", p.path("items_1").asInt(0), "选校单中有 " + f.planItems() + " 个项目");
            }
        }
        p = rules.get(PARENT);
        if (p != null)
        {
            if (f.shareCreated())
            {
                add(reasons, PARENT, "家长参与", p.path("share").asInt(0), "开启了家长只读分享");
            }
            if (f.shareViewed())
            {
                add(reasons, PARENT, "家长查看", p.path("viewed").asInt(0), "家长查看过选校单");
            }
        }
        p = rules.get(SOURCE);
        if (p != null)
        {
            String best = null;
            int bestPoints = 0;
            for (String s : f.sources().stream().sorted().toList())
            {
                int v = p.path(s).asInt(0);
                if (best == null || v > bestPoints)
                {
                    best = s;
                    bestPoints = v;
                }
            }
            if (best != null && bestPoints != 0)
            {
                add(reasons, SOURCE, "来源", bestPoints, "来自" + SOURCE_LABELS.getOrDefault(best, best));
            }
        }
        p = rules.get(RECENCY);
        if (p != null)
        {
            if (f.daysSinceLastTouch() <= 7)
            {
                add(reasons, RECENCY, "近期活跃", p.path("days_7").asInt(0), "最近 7 天有触达");
            }
            else if (f.daysSinceLastTouch() > 30)
            {
                add(reasons, RECENCY, "长期无互动", p.path("stale_30").asInt(0), f.daysSinceLastTouch() + " 天没有触达");
            }
        }
        reasons.removeIf(r -> ((Integer) r.get("points")) == 0);
        int score = reasons.stream().mapToInt(r -> (Integer) r.get("points")).sum();
        return new Result(score, grade(score, rules.get(GRADE)), reasons);
    }

    /** 来源说明 */
    static final Map<String, String> SOURCE_LABELS = Map.of("referral", "转介绍", "partner", "合作方", "lecture", "讲座报名", "wecom", "企业微信",
            "dm", "平台私信", "manual", "员工录入", "h5", "C 端");

    /**
     * 按分级线得到等级
     *
     * @param score 总分
     * @param g 分级线参数（a、b）
     * @return A / B / C
     */
    static String grade(int score, JsonNode g)
    {
        int a = g == null ? 60 : g.path("a").asInt(60);
        int b = g == null ? 30 : g.path("b").asInt(30);
        return score >= a ? "A" : score >= b ? "B" : "C";
    }

    /**
     * 校验规则参数：只能有该规则的键，值为 -100 到 100 的整数；分级线 a &gt; b &gt; 0；工具规则的天数 1–365
     *
     * @param code 规则编码
     * @param params 参数
     * @return 错误说明，通过为 null
     */
    static String validate(String code, JsonNode params)
    {
        Set<String> keys = KEYS.get(code);
        if (keys == null)
        {
            return "规则“" + code + "”不存在";
        }
        if (params == null || !params.isObject())
        {
            return "参数必须是 JSON 对象";
        }
        for (Map.Entry<String, JsonNode> e : params.properties())
        {
            if (!keys.contains(e.getKey()))
            {
                return "参数“" + e.getKey() + "”不是这条规则的参数（可用：" + String.join("、", keys.stream().sorted().toList()) + "）";
            }
            if (!e.getValue().isIntegralNumber())
            {
                return "参数“" + e.getKey() + "”须为整数";
            }
            int v = e.getValue().asInt();
            boolean big = "cap".equals(e.getKey()) || "days".equals(e.getKey()) || GRADE.equals(code);
            if (big ? v < 0 || v > 1000 : v < -100 || v > 100)
            {
                return "参数“" + e.getKey() + "”超出范围";
            }
        }
        if (GRADE.equals(code))
        {
            int a = params.path("a").asInt(-1);
            int b = params.path("b").asInt(-1);
            if (a <= b || b <= 0)
            {
                return "分级线须满足 a > b > 0";
            }
        }
        if (TOOLS.equals(code) && params.has("days") && (params.path("days").asInt() < 1 || params.path("days").asInt() > 365))
        {
            return "工具使用的统计天数须为 1–365";
        }
        return null;
    }

    private static void add(List<Map<String, Object>> reasons, String rule, String label, int points, String detail)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rule", rule);
        m.put("label", label);
        m.put("points", points);
        m.put("detail", detail);
        reasons.add(m);
    }
}

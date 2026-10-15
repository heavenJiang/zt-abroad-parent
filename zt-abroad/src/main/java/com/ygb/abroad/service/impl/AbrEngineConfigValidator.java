package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.vo.AbrEngineConfigCheck;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.DegreeStrategy;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.engine.RecommendationPlanner;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.engine.SubScoreCalculator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 引擎配置 JSON 校验（规则见 seed/engine-config/README.md「导入校验规则」），并用一个示例档案试算一遍，确保引擎需要的参数齐全
 *
 * @author ygb
 */
final class AbrEngineConfigValidator
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 配置结构版本 */
    static final String SCHEMA = "abr-engine-config/1.0";

    /** 支持的国家 / 地区 */
    static final Set<String> COUNTRIES = Set.of("US", "UK", "AU", "SG", "HK", "JP", "KR", "DE");

    /** 文件状态 */
    static final Set<String> FILE_STATUSES = Set.of("draft", "confirmed");

    /** 难度档 */
    static final List<String> DIFFICULTIES = List.of("D1", "D2", "D3", "D4", "D5");

    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

    private static final int MAX_ERRORS = 30;

    private AbrEngineConfigValidator()
    {
    }

    /**
     * 校验
     *
     * @param json 配置 JSON
     * @return 校验结果（错误为空表示可以导入）
     */
    static AbrEngineConfigCheck check(String json)
    {
        AbrEngineConfigCheck result = new AbrEngineConfigCheck();
        JsonNode root;
        try
        {
            root = MAPPER.readTree(json);
        }
        catch (JacksonException e)
        {
            result.getErrors().add("不是合法的 JSON");
            return result;
        }
        if (root == null || !root.isObject())
        {
            result.getErrors().add("配置应为 JSON 对象");
            return result;
        }
        List<String> errors = result.getErrors();
        result.setConfigCode(text(root, "config_code"));
        result.setCountry(text(root, "country"));
        result.setDegree(text(root, "degree"));
        result.setVersion(text(root, "version"));
        result.setFileStatus(text(root, "status"));

        if (!SCHEMA.equals(text(root, "schema")))
        {
            errors.add("schema 应为 " + SCHEMA);
        }
        require(errors, result.getConfigCode(), "config_code", 64);
        require(errors, result.getVersion(), "version", 64);
        require(errors, result.getDegree(), "degree", 16);
        DegreeStrategy strategy = DegreeStrategy.of(result.getDegree());
        if (result.getDegree() != null && strategy == null)
        {
            errors.add("degree 应为 secondary / bachelor / master / phd 之一");
        }
        else if (strategy != null && !strategy.implemented())
        {
            errors.add("学段 " + result.getDegree() + " 的引擎策略尚未实现（学段扩展路线 M11–M13），暂不能导入配置");
        }
        if (!COUNTRIES.contains(result.getCountry()))
        {
            errors.add("country 应为 " + String.join(" / ", COUNTRIES.stream().sorted().toList()) + " 之一");
        }
        if (!FILE_STATUSES.contains(result.getFileStatus()))
        {
            errors.add("status 应为 draft 或 confirmed");
        }

        List<String> keys = new ArrayList<>();
        root.path("dimensions").path("keys").forEach(k -> keys.add(k.asString("")));
        if (!SubScoreCalculator.DIMENSIONS.equals(keys))
        {
            errors.add("dimensions.keys 应为 " + SubScoreCalculator.DIMENSIONS);
        }

        JsonNode weights = root.path("weights");
        if (!weights.path("default").isObject())
        {
            errors.add("缺少 weights.default");
        }
        else
        {
            weightGroup(errors, weights.path("default"), "weights.default", result.getCountry());
        }
        for (Map.Entry<String, JsonNode> e : weights.path("by_field").properties())
        {
            weightGroup(errors, e.getValue(), "weights.by_field." + e.getKey(), result.getCountry());
        }

        JsonNode cut = root.path("tier_cutoffs");
        BigDecimal reach = number(cut, "reach_below");
        BigDecimal safety = number(cut, "safety_above");
        if (reach == null || safety == null || reach.signum() <= 0 || safety.compareTo(BigDecimal.ONE) >= 0 || reach.compareTo(safety) >= 0)
        {
            errors.add("tier_cutoffs 应满足 0 < reach_below < safety_above < 1");
        }
        if (!cut.path("expose_probability_to_student").isBoolean() || cut.path("expose_probability_to_student").asBoolean())
        {
            errors.add("tier_cutoffs.expose_probability_to_student 必须为 false（学生端不展示录取概率）");
        }
        BigDecimal a = number(root.path("probability"), "a_default");
        if (a == null || a.signum() <= 0)
        {
            errors.add("probability.a_default 应大于 0");
        }
        BigDecimal last = null;
        for (String d : DIFFICULTIES)
        {
            BigDecimal t = number(root.path("benchmark_defaults").path("difficulty_levels").path(d), "t");
            if (t == null)
            {
                errors.add("缺少 benchmark_defaults.difficulty_levels." + d + ".t");
            }
            else if (last != null && t.compareTo(last) >= 0)
            {
                errors.add("benchmark_defaults 中 D1–D5 的 T 应严格递减（" + d + "）");
            }
            last = t == null ? last : t;
        }
        if (!root.path("hard_thresholds").isObject())
        {
            errors.add("缺少 hard_thresholds");
        }
        if (!root.path("preference").isObject())
        {
            errors.add("缺少 preference（偏好排序参数，见 seed/engine-config/README.md）");
        }
        if (errors.isEmpty())
        {
            dryRun(errors, root);
        }
        warnings(result, root, json);
        if (errors.size() > MAX_ERRORS)
        {
            result.setErrors(new ArrayList<>(errors.subList(0, MAX_ERRORS)));
        }
        return result;
    }

    /**
     * 规范化为紧凑 JSON 文本
     *
     * @param json 配置 JSON
     * @return 紧凑 JSON
     */
    static String normalize(String json)
    {
        return MAPPER.readTree(json).toString();
    }

    private static void weightGroup(List<String> errors, JsonNode group, String where, String country)
    {
        BigDecimal sum = BigDecimal.ZERO;
        for (String dim : SubScoreCalculator.DIMENSIONS)
        {
            BigDecimal w = number(group, dim);
            if (w == null || w.signum() < 0)
            {
                errors.add(where + "." + dim + " 应为 ≥ 0 的数值");
                continue;
            }
            if ("course_match".equals(dim) && w.signum() > 0 && !"DE".equals(country))
            {
                errors.add(where + ".course_match 只在德国大于 0");
            }
            sum = sum.add(w);
        }
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(TOLERANCE) > 0)
        {
            errors.add(where + " 权重之和应为 1（当前 " + sum.stripTrailingZeros().toPlainString() + "）");
        }
    }

    /**
     * 用示例档案和 D1–D5 五个示例项目跑一遍完整流程：缺参数时引擎会报错
     */
    private static void dryRun(List<String> errors, JsonNode root)
    {
        String country = text(root, "country");
        BackgroundProfile p = new BackgroundProfile();
        p.setUndergradTier("211");
        p.setGpaPercent(new BigDecimal("85"));
        p.setClassRank("top30");
        p.setMajorRelevance("same");
        p.setEnglishStatus("taken");
        p.setEnglishTest("ielts");
        p.setIeltsEquivTotal(new BigDecimal("6.5"));
        p.setIeltsEquivMinSub(new BigDecimal("6.0"));
        p.setStdTestType("gre");
        p.setStdTestTotal(315);
        p.setResearchLevel("none");
        p.setWorkMonths(3);
        p.setWorkCompanyTier("normal");
        p.setWorkRelevant("yes");
        p.setAwardsLevel("provincial");
        p.setAwardsCount(1);
        p.setTargetCountries(List.of(country));
        p.setTargetFieldGroup("business");
        p.setMonthsToIntake(12);
        p.setBudgetTotal("40_60");
        p.setRankVsEmployment("balanced");
        p.setCityPreference("metropolis");
        p.setDeApsStatus("passed");
        p.setCourseMatchRatio(new BigDecimal("0.8"));
        List<ProgramFacts> programs = new ArrayList<>();
        for (int i = 0; i < DIFFICULTIES.size(); i++)
        {
            programs.add(new ProgramFacts((long) i + 1, country, DIFFICULTIES.get(i), "en", false, null,
                    new ProgramFacts.Listing(new BigDecimal("30000"), "CNY", 12, null, Map.of("QS", 100))));
        }
        Map<String, JsonNode> configs = Map.of(country, root);
        try
        {
            SelectionEngine.Evaluation e = SelectionEngine.evaluate(p, programs, configs, Map.of(), false);
            RecommendationPlanner.plan(p, e, configs);
            RecommendationPlanner.plan(p, SelectionEngine.evaluate(p, programs, configs, Map.of(), true), configs);
        }
        catch (RuntimeException ex)
        {
            errors.add("试算失败：" + ex.getMessage());
        }
    }

    private static void warnings(AbrEngineConfigCheck result, JsonNode root, String json)
    {
        if (!"confirmed".equals(result.getFileStatus()))
        {
            result.getWarnings().add("文件状态为草案（draft）：只能导入为草稿供测试台试算，顾问确认并改为 confirmed 后才能启用");
            return;
        }
        if (json.contains("AI 草拟"))
        {
            result.getWarnings().add("状态为 confirmed，但仍有说明写着“AI 草拟”：请确认对应数字已由顾问核对，并把说明改为“顾问确认 + 日期”");
        }
        if (text(root, "confirmed_by") == null || text(root, "confirmed_at") == null)
        {
            result.getWarnings().add("状态为 confirmed，但缺少 confirmed_by 或 confirmed_at");
        }
        if (result.getVersion() != null && result.getVersion().contains("draft"))
        {
            result.getWarnings().add("状态为 confirmed，但版本号仍含 draft");
        }
    }

    private static void require(List<String> errors, String value, String name, int max)
    {
        if (value == null)
        {
            errors.add("缺少 " + name);
        }
        else if (value.length() > max)
        {
            errors.add(name + " 不能超过 " + max + " 个字符");
        }
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode v = node.get(key);
        return v == null || !v.isString() || v.asString().isBlank() ? null : v.asString().trim();
    }

    private static BigDecimal number(JsonNode node, String key)
    {
        JsonNode v = node.get(key);
        return v == null || !v.isNumber() ? null : v.decimalValue();
    }
}

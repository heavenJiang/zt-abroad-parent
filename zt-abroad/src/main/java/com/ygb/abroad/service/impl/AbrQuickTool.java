package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.vo.AppQuickResultVo;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.HardThresholdChecker;
import com.ygb.abroad.engine.RecommendationPlanner;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 30 秒快测的纯函数部分（不访问数据库）：答案校验、学生可见结果组装、计算留痕
 *
 * 学生可见结果只取引擎的分组与硬门槛明细，不含综合分、录取概率、参考线；院校层次不向学生展示。
 *
 * @author ygb
 */
final class AbrQuickTool
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 工具编码 */
    static final String TOOL = "quick";

    /** 每档展示的代表项目数 */
    static final int EXAMPLES = 3;

    /** 学校未收录（搜不到）时的提示码 */
    static final String NOTICE_SCHOOL_NOT_FOUND = "school_not_found";

    /** 向学生展示的方案提示 */
    static final Set<String> HINTS = Set.of("mix_application", "no_safety_in_first_choice");

    /** 院校背景类检查项：要求与学生情况都含院校层次，不向学生展示 */
    static final String ITEM_TIER = "tier_accepted";

    /** 学校名最大长度 */
    static final int MAX_NAME = 100;

    private AbrQuickTool()
    {
    }

    /**
     * 校验快测答案，只保留快测四题
     *
     * @param q 问卷（题目定义与国家 / 地区选项）
     * @param answers 前端提交的答案
     * @return 校验后的答案
     */
    static ObjectNode validate(AbrAssessmentAnswers.Questionnaire q, JsonNode answers)
    {
        if (answers == null || !answers.isObject())
        {
            throw new ServiceException("请填写快测信息");
        }
        for (String id : SelectionEngine.QUICK_QUESTIONS)
        {
            if (q.questions().get(id) == null)
            {
                throw new ServiceException("快测暂未开放，请稍后再试");
            }
            JsonNode v = answers.get(id);
            if (v == null || v.isNull())
            {
                throw new ServiceException("请完成：" + q.questions().get(id).path("title").asString(id));
            }
            if (AbrAssessmentAnswers.containsTag(v))
            {
                throw new ServiceException("填写内容不能包含 < 加字母的写法");
            }
        }
        ObjectNode out = MAPPER.createObjectNode();
        out.set("undergrad_school", school(answers.get("undergrad_school")));
        out.set("gpa", gpa(q.questions().get("gpa"), answers.get("gpa")));
        out.set("target_countries", countries(q, answers.get("target_countries")));
        out.set("target_field", field(q.questions().get("target_field"), answers.get("target_field")));
        return out;
    }

    static JsonNode school(JsonNode v)
    {
        String kind = v.path("kind").asString("");
        String name = v.path("name").isString() ? v.path("name").asString().trim() : "";
        ObjectNode out = MAPPER.createObjectNode();
        switch (kind)
        {
            case "cn":
                if (!v.path("university_id").canConvertToLong() || v.path("university_id").asLong() <= 0)
                {
                    throw new ServiceException("请从搜索结果中选择本科学校");
                }
                out.put("kind", kind);
                out.put("university_id", v.path("university_id").asLong());
                break;
            case "overseas":
            case "not_found":
                out.put("kind", kind);
                out.putNull("university_id");
                break;
            default:
                throw new ServiceException("请选择本科学校");
        }
        if (name.length() > MAX_NAME)
        {
            throw new ServiceException("学校名称过长");
        }
        out.put("name", name);
        return out;
    }

    private static JsonNode gpa(JsonNode question, JsonNode v)
    {
        String type = v.path("type").asString("");
        JsonNode scale = null;
        for (JsonNode t : question.path("score_types"))
        {
            if (type.equals(t.path("value").asString()))
            {
                scale = t;
            }
        }
        if (scale == null || !v.path("total").isNumber())
        {
            throw new ServiceException("请填写均分 / GPA");
        }
        BigDecimal total = v.path("total").decimalValue();
        BigDecimal min = number(scale.path("total").path("min"), BigDecimal.ZERO);
        BigDecimal max = number(scale.path("total").path("max"), null);
        ObjectNode out = MAPPER.createObjectNode();
        out.put("type", type);
        if (scale.path("requires_max").asBoolean(false))
        {
            if (!v.path("max").isNumber())
            {
                throw new ServiceException("请填写满分");
            }
            max = v.path("max").decimalValue();
            BigDecimal maxMin = number(scale.path("max_field").path("min"), BigDecimal.ONE);
            BigDecimal maxMax = number(scale.path("max_field").path("max"), null);
            if (max.compareTo(maxMin) < 0 || maxMax != null && max.compareTo(maxMax) > 0)
            {
                throw new ServiceException("满分不在合理范围内");
            }
            out.put("max", max);
        }
        if (total.compareTo(min) < 0 || max != null && total.compareTo(max) > 0)
        {
            throw new ServiceException("均分 / GPA 不在合理范围内");
        }
        out.put("total", total);
        return out;
    }

    static JsonNode countries(AbrAssessmentAnswers.Questionnaire q, JsonNode v)
    {
        Set<String> allowed = new HashSet<>();
        q.root().path("countries").forEach(c -> allowed.add(c.path("value").asString()));
        JsonNode rule = q.questions().get("target_countries").path("validation");
        int maxSelected = rule.path("max_selected").asInt(allowed.size());
        if (!v.isArray() || v.isEmpty())
        {
            throw new ServiceException("请选择目标国家 / 地区");
        }
        ArrayNode out = MAPPER.createArrayNode();
        Set<String> seen = new HashSet<>();
        for (JsonNode c : v)
        {
            String code = c.isString() ? c.asString() : "";
            if (!allowed.contains(code))
            {
                throw new ServiceException("目标国家 / 地区不正确");
            }
            if (seen.add(code))
            {
                out.add(code);
            }
        }
        if (out.size() > maxSelected)
        {
            throw new ServiceException("目标国家 / 地区最多选 " + maxSelected + " 个");
        }
        return out;
    }

    static JsonNode field(JsonNode question, JsonNode v)
    {
        String value = v.isString() ? v.asString() : "";
        for (JsonNode o : question.path("options"))
        {
            if (value.equals(o.path("value").asString()))
            {
                return MAPPER.getNodeFactory().stringNode(value);
            }
        }
        throw new ServiceException("请选择专业方向");
    }

    /**
     * 组装学生可见的快测结果
     *
     * @param run 计算结果
     * @param p 快测背景档案
     * @param countryLabels 国家 / 地区代码 → 名称
     * @param toolRunId 运行ID
     * @return 快测结果
     */
    static AppQuickResultVo build(IAbrSelectionService.QuickRun run, BackgroundProfile p, Map<String, String> countryLabels, Long toolRunId)
    {
        AppQuickResultVo vo = new AppQuickResultVo();
        vo.setToolRunId(toolRunId);
        List<String> notices = new ArrayList<>(run.plan().notices());
        if (p.getWarnings().contains("tier_unknown"))
        {
            notices.add(NOTICE_SCHOOL_NOT_FOUND);
        }
        vo.setNotices(notices);
        vo.setGpaPercent(p.getGpaPercent());
        List<AppQuickResultVo.CountryResult> countries = new ArrayList<>();
        for (String country : run.openCountries())
        {
            countries.add(country(country, run, countryLabels));
        }
        vo.setCountries(countries);
        vo.setClosedCountries(run.skippedCountries().stream()
                .map(c -> new AppQuickResultVo.CountryRef(c, countryLabels.getOrDefault(c, c))).toList());
        return vo;
    }

    private static AppQuickResultVo.CountryResult country(String country, IAbrSelectionService.QuickRun run, Map<String, String> labels)
    {
        RecommendationPlanner.Plan plan = run.plan();
        List<AppQuickResultVo.TierGroup> tiers = new ArrayList<>();
        for (String tier : RecommendationPlanner.TIERS)
        {
            List<RecommendationPlanner.Ranked> list = inCountry(plan.tiers().get(tier), country);
            tiers.add(new AppQuickResultVo.TierGroup(tier, list.size(),
                    list.stream().limit(EXAMPLES).map(r -> program(r.evaluation(), run.programs(), false)).toList()));
        }
        List<RecommendationPlanner.Ranked> blocked = inCountry(plan.needsImprovement(), country);
        int programCount = (int) run.evaluation().results().stream()
                .filter(e -> country.equals(e.program().country()) && !SelectionEngine.EXCLUDED.equals(e.group())).count();
        List<String> hints = new ArrayList<>();
        for (RecommendationPlanner.Hint h : plan.hints())
        {
            if (HINTS.contains(h.code()) && country.equals(h.country()) && !hints.contains(h.code()))
            {
                hints.add(h.code());
            }
        }
        return new AppQuickResultVo.CountryResult(country, labels.getOrDefault(country, country), programCount, tiers,
                inCountry(plan.unrated(), country).size(), blocked.size(),
                blocked.stream().limit(EXAMPLES).map(r -> program(r.evaluation(), run.programs(), true)).toList(), keyGaps(blocked), hints);
    }

    private static List<RecommendationPlanner.Ranked> inCountry(List<RecommendationPlanner.Ranked> list, String country)
    {
        return list.stream().filter(r -> country.equals(r.evaluation().program().country())).toList();
    }

    private static AppQuickResultVo.ProgramRef program(SelectionEngine.ProgramEvaluation e, Map<Long, AbrProgram> programs, boolean withGaps)
    {
        AbrProgram program = programs.get(e.program().programId());
        List<AppQuickResultVo.GapItem> gaps = new ArrayList<>();
        if (withGaps)
        {
            for (HardThresholdChecker.Item item : e.threshold().items())
            {
                if (HardThresholdChecker.NOT_MET.equals(item.outcome()))
                {
                    boolean tier = ITEM_TIER.equals(item.key());
                    gaps.add(new AppQuickResultVo.GapItem(item.key(), tier ? null : requiredText(item), tier ? null : item.actual(),
                            HardThresholdChecker.SOURCE_DEFAULT.equals(item.source())));
                }
            }
        }
        return new AppQuickResultVo.ProgramRef(e.program().programId(), program == null ? null : program.getSchoolName(),
                program == null ? null : program.getProgramName(), e.threshold().flags().contains("requirement_unverified"), gaps);
    }

    /**
     * 关键差距：补足后可申的项目按未达标项计数；均分给出最容易达到的那条要求
     */
    static List<AppQuickResultVo.KeyGap> keyGaps(List<RecommendationPlanner.Ranked> blocked)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        BigDecimal minGpa = null;
        String actualGpa = null;
        for (RecommendationPlanner.Ranked r : blocked)
        {
            for (HardThresholdChecker.Item item : r.evaluation().threshold().items())
            {
                if (!HardThresholdChecker.NOT_MET.equals(item.outcome()))
                {
                    continue;
                }
                counts.merge(item.key(), 1, Integer::sum);
                BigDecimal req = "gpa".equals(item.key()) ? requiredNumber(item.required()) : null;
                if (req != null && (minGpa == null || req.compareTo(minGpa) < 0))
                {
                    minGpa = req;
                    actualGpa = item.actual();
                }
            }
        }
        List<AppQuickResultVo.KeyGap> gaps = new ArrayList<>();
        BigDecimal gpaReq = minGpa;
        String gpaActual = actualGpa;
        counts.forEach((key, n) -> gaps.add("gpa".equals(key)
                ? new AppQuickResultVo.KeyGap(key, n, gpaReq == null ? null : "≥ " + gpaReq.stripTrailingZeros().toPlainString(), gpaActual)
                : new AppQuickResultVo.KeyGap(key, n, null, null)));
        gaps.sort((a, b) -> Integer.compare(b.programCount(), a.programCount()));
        return gaps;
    }

    /**
     * 要求文字去掉院校层次前缀（引擎写作“211 ≥ 85”，学生只看到“≥ 85”）
     */
    static String requiredText(HardThresholdChecker.Item item)
    {
        String required = item.required();
        if (required == null || !"gpa".equals(item.key()))
        {
            return required;
        }
        int i = required.indexOf('≥');
        return i < 0 ? required : required.substring(i);
    }

    private static BigDecimal requiredNumber(String required)
    {
        if (required == null || required.indexOf('≥') < 0)
        {
            return null;
        }
        try
        {
            return new BigDecimal(required.substring(required.indexOf('≥') + 1).trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * 计算留痕（只给管理端）：各项目分组、档位、未达标项、提示
     *
     * @param run 计算结果
     * @return 留痕 JSON
     */
    static ObjectNode trace(IAbrSelectionService.QuickRun run)
    {
        ObjectNode out = MAPPER.createObjectNode();
        ArrayNode rows = out.putArray("programs");
        for (SelectionEngine.ProgramEvaluation e : run.evaluation().results())
        {
            ObjectNode row = rows.addObject();
            row.put("programId", e.program().programId());
            row.put("country", e.program().country());
            row.put("group", e.group());
            row.put("tier", e.estimate().tier());
            row.put("c", e.composite().c());
            row.put("p", e.estimate().p());
            row.put("tSource", e.estimate().tSource());
            ArrayNode missing = row.putArray("missing");
            e.threshold().missing().forEach(missing::add);
            ArrayNode flags = row.putArray("flags");
            e.threshold().flags().forEach(flags::add);
        }
        ArrayNode skipped = out.putArray("skippedCountries");
        run.skippedCountries().forEach(skipped::add);
        ArrayNode hints = out.putArray("hints");
        run.plan().hints().forEach(h -> hints.add(h.code() + (h.country() == null ? "" : ":" + h.country())));
        return out;
    }

    /**
     * 取某项目在留痕中的分组
     *
     * @param trace 留痕 JSON 文本
     * @param programId 项目ID
     * @return 分组；留痕中没有该项目为 null
     */
    static String groupOf(String trace, Long programId)
    {
        JsonNode rows = AbrAssessmentAnswers.object(trace).path("programs");
        for (JsonNode row : rows)
        {
            if (programId.equals(row.path("programId").asLong()))
            {
                String group = row.path("group").asString("");
                return StringUtils.isBlank(group) || SelectionEngine.EXCLUDED.equals(group) ? null : group;
            }
        }
        return null;
    }

    private static BigDecimal number(JsonNode n, BigDecimal fallback)
    {
        return n != null && n.isNumber() ? n.decimalValue() : fallback;
    }
}

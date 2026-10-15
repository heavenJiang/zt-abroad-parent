package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.CaseBench;
import com.ygb.abroad.engine.NormalizationRules;
import com.ygb.abroad.engine.ProfileNormalizer;
import com.ygb.abroad.engine.ProgramFacts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 引擎测试案例 CSV（格式见 seed/engine-cases/README.md）→ 测试台案例
 *
 * 跳过 EXAMPLE- 开头的模板示例行；均分、雅思等效分留空时按问卷换算表换算（与学生测评同一套规则）。
 *
 * @author ygb
 */
final class AbrEngineCaseCsv
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 必填列 */
    static final List<String> REQUIRED = List.of("case_id", "country", "degree", "target_field_group", "program_difficulty", "undergrad_tier",
            "major_relevance", "gpa_raw", "gpa_scale", "english_test", "consultant_tier");

    /** 顾问档位 → 引擎档位 */
    static final Map<String, String> TIERS = Map.of("冲", "reach", "稳", "match", "保", "safety", "reach", "reach", "match", "match",
            "safety", "safety");

    /** 案例英语考试 → 问卷考试类型 */
    static final Map<String, String> ENGLISH = Map.of("ielts", "ielts", "toefl", "toefl_ibt", "toefl_ibt", "toefl_ibt", "pte", "pte",
            "duolingo", "duolingo");

    static final Set<String> UNDERGRAD_TIERS = Set.of("c9", "985", "211", "double_first_class", "non_key", "overseas");

    /** 单个文件最多行数 */
    static final int MAX_ROWS = 2000;

    private AbrEngineCaseCsv()
    {
    }

    /**
     * 解析
     *
     * @param csv CSV 文本（可带 BOM）
     * @param rules 问卷换算表（为 null 时均分、雅思等效分必须在 CSV 中填好）
     * @return 解析结果
     */
    static Parsed parse(String csv, NormalizationRules rules)
    {
        List<CaseBench.BenchCase> cases = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<List<String>> rows = records(csv.startsWith("﻿") ? csv.substring(1) : csv);
        if (rows.isEmpty())
        {
            errors.add("CSV 为空");
            return new Parsed(cases, errors, 0);
        }
        List<String> header = rows.get(0).stream().map(String::trim).toList();
        for (String col : REQUIRED)
        {
            if (!header.contains(col))
            {
                errors.add("缺少列 " + col);
            }
        }
        if (!errors.isEmpty())
        {
            return new Parsed(cases, errors, 0);
        }
        if (rows.size() - 1 > MAX_ROWS)
        {
            errors.add("案例不能超过 " + MAX_ROWS + " 行");
            return new Parsed(cases, errors, 0);
        }
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++)
        {
            Map<String, String> r = new HashMap<>();
            List<String> values = rows.get(i);
            if (values.stream().allMatch(String::isBlank))
            {
                continue;
            }
            for (int c = 0; c < header.size(); c++)
            {
                r.put(header.get(c), c < values.size() ? values.get(c).trim() : "");
            }
            String id = r.get("case_id");
            if (id.startsWith("EXAMPLE-"))
            {
                skipped++;
                continue;
            }
            try
            {
                cases.add(toCase(r, rules, (long) i));
            }
            catch (IllegalArgumentException e)
            {
                errors.add("第 " + (i + 1) + " 行（" + id + "）：" + e.getMessage());
            }
        }
        return new Parsed(cases, errors, skipped);
    }

    private static CaseBench.BenchCase toCase(Map<String, String> r, NormalizationRules rules, long rowNo)
    {
        for (String col : REQUIRED)
        {
            if (r.get(col).isEmpty())
            {
                throw new IllegalArgumentException(col + " 不能为空");
            }
        }
        String tier = r.get("undergrad_tier");
        if (!UNDERGRAD_TIERS.contains(tier))
        {
            throw new IllegalArgumentException("undergrad_tier 取值不正确：" + tier);
        }
        String consultant = TIERS.get(r.get("consultant_tier"));
        if (consultant == null)
        {
            throw new IllegalArgumentException("consultant_tier 应为 冲 / 稳 / 保");
        }
        BackgroundProfile p = new BackgroundProfile();
        p.setUndergradTier(tier);
        p.setMajorRelevance(r.get("major_relevance"));
        p.setMissingPrereqCount(integer(r, "missing_prereq_count", 0));
        p.setClassRank(blankToNull(r.get("class_rank")));
        p.setTargetCountries(List.of(r.get("country")));
        p.setTargetDegree(r.get("degree"));
        p.setTargetFieldGroup(r.get("target_field_group"));
        scores(p, r, rules);
        String std = r.getOrDefault("std_test_type", "");
        p.setStdTestType(std.isEmpty() ? "none" : std);
        p.setStdTestTotal(integer(r, "std_test_total", null));
        p.setResearchLevel(blankToNull(r.get("research_level")) == null ? "none" : r.get("research_level"));
        p.setResearchCount(integer(r, "research_count", 0));
        p.setResearchRelevant(blankToNull(r.get("research_relevant")));
        p.setWorkMonths(integer(r, "work_months", 0));
        p.setWorkFulltimeMonths(integer(r, "work_fulltime_months", 0));
        p.setWorkCompanyTier(blankToNull(r.get("work_company_tier")));
        p.setWorkRelevant(blankToNull(r.get("work_relevant")));
        p.setAwardsLevel(blankToNull(r.get("awards_level")) == null ? "none" : r.get("awards_level"));
        p.setAwardsCount(integer(r, "awards_count", 0));
        p.setCourseMatchRatio(decimal(r, "course_match_ratio"));
        p.setDeApsStatus(blankToNull(r.get("de_aps_status")));
        p.setJlptLevel(blankToNull(r.get("jlpt_level")));
        p.setTopikLevel(blankToNull(r.get("topik_level")));

        ProgramFacts.Requirement req = null;
        boolean noCutoff = "none".equals(r.getOrDefault("req_min_gpa", ""));
        BigDecimal minGpa = noCutoff ? null : decimal(r, "req_min_gpa");
        BigDecimal ieltsOverall = decimal(r, "req_ielts_overall");
        BigDecimal ieltsSub = decimal(r, "req_ielts_min_sub");
        String preSessional = switch (r.getOrDefault("req_pre_sessional", ""))
        {
            case "Y" -> "1";
            case "N" -> "0";
            default -> "2";
        };
        if (noCutoff || minGpa != null || ieltsOverall != null || ieltsSub != null || !"2".equals(preSessional))
        {
            Map<String, BigDecimal> gpa = minGpa == null ? Map.of() : Map.of(tier, minGpa);
            req = new ProgramFacts.Requirement(gpa, Set.of(), ieltsOverall, ieltsSub, null, null, null, false, null, "2", preSessional, noCutoff);
        }
        String lang = blankToNull(r.get("teaching_language"));
        ProgramFacts program = new ProgramFacts(rowNo, r.get("country"), r.get("program_difficulty"), lang == null ? "en" : lang, false, req);

        Boolean admitted = null;
        String outcome = r.getOrDefault("outcome", "");
        String finalOutcome = "waitlist".equals(outcome) ? r.getOrDefault("waitlist_final", "") : outcome;
        if ("admit".equals(finalOutcome))
        {
            admitted = true;
        }
        else if ("reject".equals(finalOutcome))
        {
            admitted = false;
        }
        String expected = blankToNull(r.get("expected_threshold"));
        if (expected != null && !Set.of("pass", "pre_sessional", "needs_improvement").contains(expected))
        {
            throw new IllegalArgumentException("expected_threshold 应为 pass、pre_sessional 或 needs_improvement");
        }
        return new CaseBench.BenchCase(r.get("case_id"), r.getOrDefault("verification_level", ""), p, program, consultant, admitted,
                "Y".equals(r.get("is_borderline")), expected);
    }

    /**
     * 均分与英语：已填换算值时直接用，否则按问卷换算表换算
     */
    private static void scores(BackgroundProfile p, Map<String, String> r, NormalizationRules rules)
    {
        String test = r.get("english_test");
        BigDecimal gpaPercent = decimal(r, "gpa_percent");
        BigDecimal ielts = decimal(r, "ielts_equiv_total");
        BigDecimal ieltsSub = decimal(r, "ielts_equiv_min_sub");
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        if (gpaPercent == null)
        {
            ObjectNode gpa = MAPPER.createObjectNode();
            String scale = r.get("gpa_scale");
            String type = "100".equals(scale) ? "percent" : "4".equals(scale) ? "gpa4" : "5".equals(scale) ? "gpa5" : "other";
            gpa.put("type", type);
            gpa.put("total", required(r, "gpa_raw"));
            if ("other".equals(type))
            {
                gpa.put("max", required(r, "gpa_scale"));
            }
            answers.put("gpa", gpa);
        }
        if ("none".equals(test))
        {
            answers.put("english_status", MAPPER.getNodeFactory().stringNode("none"));
        }
        else
        {
            String type = ENGLISH.get(test);
            if (type == null)
            {
                throw new IllegalArgumentException("english_test 取值不正确：" + test);
            }
            answers.put("english_status", MAPPER.getNodeFactory().stringNode("taken"));
            ObjectNode score = MAPPER.createObjectNode();
            score.put("type", type);
            score.put("total", required(r, "english_total"));
            BigDecimal sub = decimal(r, "english_min_sub");
            if (sub != null)
            {
                score.put("min_sub", sub);
            }
            answers.put("english_score", score);
        }
        BackgroundProfile n = null;
        if ((gpaPercent == null || ielts == null && !"none".equals(test)))
        {
            if (rules == null)
            {
                throw new IllegalArgumentException("没有启用的问卷换算表，请填写 gpa_percent 与 ielts_equiv_total");
            }
            n = ProfileNormalizer.normalize(answers, rules, id -> null, LocalDate.of(2000, 1, 1));
        }
        p.setGpaPercent(gpaPercent != null ? gpaPercent : n.getGpaPercent());
        p.setEnglishStatus("none".equals(test) ? "none" : "taken");
        p.setEnglishTest("none".equals(test) ? null : ENGLISH.get(test));
        p.setEnglishTotal(decimal(r, "english_total"));
        p.setEnglishMinSub(decimal(r, "english_min_sub"));
        p.setEnglishEstimated(false);
        if (!"none".equals(test))
        {
            p.setIeltsEquivTotal(ielts != null ? ielts : n.getIeltsEquivTotal());
            p.setIeltsEquivMinSub(ielts != null ? ieltsSub : n.getIeltsEquivMinSub());
        }
    }

    /**
     * 标准 CSV（RFC 4180）：逗号分隔，双引号包裹，引号内两个双引号表示一个
     */
    static List<List<String>> records(String text)
    {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (quoted)
            {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"')
                {
                    cell.append('"');
                    i++;
                }
                else if (ch == '"')
                {
                    quoted = false;
                }
                else
                {
                    cell.append(ch);
                }
            }
            else if (ch == '"')
            {
                quoted = true;
            }
            else if (ch == ',')
            {
                row.add(cell.toString());
                cell.setLength(0);
            }
            else if (ch == '\n' || ch == '\r')
            {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n')
                {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            }
            else
            {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty())
        {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static BigDecimal required(Map<String, String> r, String col)
    {
        BigDecimal v = decimal(r, col);
        if (v == null)
        {
            throw new IllegalArgumentException(col + " 不能为空");
        }
        return v;
    }

    private static BigDecimal decimal(Map<String, String> r, String col)
    {
        String v = r.getOrDefault(col, "");
        if (v.isEmpty())
        {
            return null;
        }
        try
        {
            return new BigDecimal(v);
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(col + " 不是数字：" + v);
        }
    }

    private static Integer integer(Map<String, String> r, String col, Integer fallback)
    {
        BigDecimal v = decimal(r, col);
        if (v == null)
        {
            return fallback;
        }
        return v.intValue();
    }

    private static String blankToNull(String v)
    {
        return v == null || v.isEmpty() ? null : v;
    }

    /**
     * 解析结果
     *
     * @param cases 案例
     * @param errors 行级错误（有错误的行不参与运行）
     * @param skipped 跳过的模板示例行数
     */
    record Parsed(List<CaseBench.BenchCase> cases, List<String> errors, int skipped)
    {
    }
}

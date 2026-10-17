package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.abroad.ai.AiJson;
import com.ygb.abroad.domain.AbrAssessmentFile;
import com.ygb.abroad.domain.AbrCnUniversity;
import com.ygb.abroad.domain.vo.AppCnUniversityVo;
import com.ygb.abroad.domain.vo.AppResumeParseVo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 简历解析（resume_parser）的纯函数：文字预处理、模型输入、输出逐项校验、多份文件合并为建议值
 * <p>
 * 预处理：去掉住址、出生日期、证件号、学号等与测评无关的值，找出姓名交给网关替换为占位符。
 * 校验：学校、专业须在原文中逐字出现；均分、语言分数须在原文中出现且符合问卷的分制范围；
 * 经历只接受“有”（简历里没写不等于没有），级别须是问卷选项；“是否相关”“是否头部公司”留给学生自己选。
 * 结果只是建议值，学生确认后才由前端按普通答案保存。
 * </p>
 *
 * @author ygb
 */
final class AbrResumeParser
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Agent 编码 */
    static final String AGENT_CODE = "resume_parser";

    /** 题目：本科学校 */
    static final String Q_SCHOOL = "undergrad_school";

    /** 题目：本科专业 */
    static final String Q_MAJOR = "undergrad_major";

    /** 题目：均分 */
    static final String Q_GPA = "gpa";

    /** 题目：英语考试情况 */
    static final String Q_ENGLISH_STATUS = "english_status";

    /** 题目：英语成绩 */
    static final String Q_ENGLISH = "english_score";

    /** 题目：科研 */
    static final String Q_RESEARCH = "exp_research";

    /** 题目：实习 / 工作 */
    static final String Q_WORK = "exp_work";

    /** 题目：竞赛、项目、证书 */
    static final String Q_AWARDS = "exp_awards";

    /** 建议值的展示顺序 */
    static final List<String> QUESTION_ORDER = List.of(Q_SCHOOL, Q_MAJOR, Q_GPA, Q_ENGLISH_STATUS, Q_ENGLISH, Q_RESEARCH, Q_WORK, Q_AWARDS);

    /** 学校、专业名称最大长度 */
    private static final int NAME_MAX = 50;

    /** 学校候选最多条数 */
    static final int SCHOOL_CANDIDATES = 5;

    /** 值要去掉的标签（只去掉紧跟的一个值） */
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(籍贯|民族|政治面貌|出生日期|出生年月|出生|生日|身份证号?|证件号码?|护照号?|学号|考生号|准考证号|婚姻状况|婚否|身高|体重|户口|户籍|QQ|qq)(\\s*[:：]\\s*)(\\S+)");

    /** 去掉到行尾的标签（地址、紧急联系人等值里可能有空格） */
    private static final Pattern SENSITIVE_LINE = Pattern.compile("(家庭住址|通讯地址|现住址|住址|地址|紧急联系人|家长电话|父亲|母亲)(\\s*[:：]\\s*)(.+)$");

    /** 带标签的中文姓名 */
    private static final Pattern NAME_LABEL = Pattern.compile(
            "姓\\s*名\\s*[:：]?\\s*([\\u4e00-\\u9fa5·]{2,6}?)(?=性别|电话|手机|邮箱|年龄|出生|民族|籍贯|政治|$|[^\\u4e00-\\u9fa5·])");

    /** 带标签的英文姓名（标签在行首，避免把 School Name: 后的校名当成姓名） */
    private static final Pattern NAME_LABEL_EN = Pattern.compile("(?i)^(?:full\\s*)?name\\s*[:：]\\s*([A-Za-z][A-Za-z.'\\-]*(?: [A-Za-z][A-Za-z.'\\-]*){0,3})");

    /** 首行的中文姓名（2–4 个汉字） */
    private static final Pattern FIRST_LINE_CN = Pattern.compile("^([\\u4e00-\\u9fa5]{2,4})(?:$|[\\s|｜/·,，]).*");

    /** 首行的英文姓名（2–3 个首字母大写的词） */
    private static final Pattern FIRST_LINE_EN = Pattern.compile("^([A-Z][a-z]+(?: [A-Z][a-z]+){1,2})$");

    /** 首行不是姓名的常见标题 */
    private static final Pattern NOT_NAME = Pattern.compile("(?i)简历|履历|成绩|报告|证明|个人|大学|学院|学校|中学|教育|信息|资料|resume|curriculum|vitae|transcript|record|profile");

    /** 数字 */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** 空白 */
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0\\u3000]+");

    private AbrResumeParser()
    {
    }

    /**
     * 预处理结果
     *
     * @param text 去除敏感值后的文字（已截断）
     * @param names 找到的姓名（交给网关替换为占位符）
     */
    record Prepared(String text, List<String> names)
    {
    }

    /**
     * 预处理：规范空白、去掉与测评无关的个人信息、找出姓名、截断
     *
     * @param raw 提取出的原文
     * @param maxChars 最大字符数
     * @return 预处理结果
     */
    static Prepared prepare(String raw, int maxChars)
    {
        List<String> names = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String line : (raw == null ? "" : raw).replace("\r", "\n").split("\n"))
        {
            String s = line.replace(' ', ' ').replace('　', ' ').replaceAll("[ \\t]+", " ").trim();
            if (s.isEmpty())
            {
                continue;
            }
            collectNames(s, first, names);
            first = false;
            s = SENSITIVE_LINE.matcher(s).replaceAll("$1$2[已去除]");
            s = SENSITIVE_VALUE.matcher(s).replaceAll("$1$2[已去除]");
            sb.append(s).append('\n');
        }
        String text = sb.toString().trim();
        if (text.length() > maxChars)
        {
            text = text.substring(0, maxChars);
        }
        return new Prepared(text, names);
    }

    private static void collectNames(String line, boolean firstLine, List<String> names)
    {
        Matcher m = NAME_LABEL.matcher(line);
        while (m.find())
        {
            addName(names, m.group(1));
        }
        m = NAME_LABEL_EN.matcher(line);
        while (m.find())
        {
            addName(names, m.group(1).trim());
        }
        if (firstLine && line.length() <= 40)
        {
            m = FIRST_LINE_CN.matcher(line);
            if (m.matches())
            {
                addName(names, m.group(1));
            }
            m = FIRST_LINE_EN.matcher(line);
            if (m.matches())
            {
                addName(names, m.group(1));
            }
        }
    }

    private static void addName(List<String> names, String name)
    {
        if (name != null && name.length() >= 2 && !NOT_NAME.matcher(name).find() && !names.contains(name))
        {
            names.add(name);
        }
    }

    /**
     * 模型输入
     *
     * @param kind 文件类别（resume / transcript）
     * @param prepared 预处理结果
     * @return 输入变量
     */
    static Map<String, Object> input(String kind, Prepared prepared)
    {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("kind", AbrAssessmentFile.KIND_TRANSCRIPT.equals(kind) ? "成绩单" : "简历");
        input.put("text", prepared.text());
        return input;
    }

    /**
     * 逐项校验模型输出，转成问卷答案形状的建议值
     *
     * @param q 问卷（取分制范围、选项）
     * @param ai 模型输出（已通过 Schema）
     * @param source 预处理后的原文（交给模型的同一份文字）
     * @param schoolSearch 国内高校搜索（校名 → 候选，按匹配度排序）
     * @return {suggestions: {题目: 值}, schoolCandidates: [...], dropped: [被丢弃的原因]}
     */
    static ObjectNode validate(AbrAssessmentAnswers.Questionnaire q, JsonNode ai, String source,
            Function<String, List<AbrCnUniversity>> schoolSearch)
    {
        ObjectNode suggestions = MAPPER.createObjectNode();
        ArrayNode candidates = MAPPER.createArrayNode();
        ArrayNode dropped = MAPPER.createArrayNode();
        String compact = compact(source);
        Set<String> numbers = numbers(source);

        school(ai.path("school"), compact, schoolSearch, suggestions, candidates, dropped);
        major(q, ai.path("major"), compact, suggestions, dropped);
        ObjectNode gpa = score(q.questions().get(Q_GPA), ai.path("gpa"), numbers, false, dropped);
        if (gpa != null)
        {
            suggestions.set(Q_GPA, gpa);
        }
        ObjectNode english = score(q.questions().get(Q_ENGLISH), ai.path("english"), numbers, true, dropped);
        if (english != null)
        {
            suggestions.put(Q_ENGLISH_STATUS, "taken");
            suggestions.set(Q_ENGLISH, english);
        }
        experience(q.questions().get(Q_RESEARCH), ai.path("research"), List.of("count", "level"), suggestions, dropped);
        experience(q.questions().get(Q_WORK), ai.path("work"), List.of("count", "total_months", "fulltime_months"), suggestions, dropped);
        experience(q.questions().get(Q_AWARDS), ai.path("awards"), List.of("count", "level"), suggestions, dropped);

        ObjectNode result = MAPPER.createObjectNode();
        result.set("suggestions", suggestions);
        result.set("schoolCandidates", candidates);
        result.set("dropped", dropped);
        return result;
    }

    private static void school(JsonNode ai, String compact, Function<String, List<AbrCnUniversity>> search, ObjectNode suggestions,
            ArrayNode candidates, ArrayNode dropped)
    {
        if (!ai.isObject())
        {
            return;
        }
        String name = ai.path("name").asString("").trim();
        if (name.isEmpty() || name.length() > NAME_MAX || !compact.contains(compact(name)))
        {
            dropped.add("school：校名不在原文中");
            return;
        }
        if (ai.path("overseas").asBoolean(false))
        {
            ObjectNode v = MAPPER.createObjectNode();
            v.put("kind", "overseas");
            v.putNull("university_id");
            v.put("name", name);
            suggestions.set(Q_SCHOOL, v);
            return;
        }
        List<AbrCnUniversity> found = search.apply(name);
        for (AbrCnUniversity u : found)
        {
            if (name.equals(u.getUniversityName()) || aliasOf(u, name))
            {
                ObjectNode v = MAPPER.createObjectNode();
                v.put("kind", "cn");
                v.put("university_id", u.getCnUniversityId());
                v.put("name", u.getUniversityName());
                suggestions.set(Q_SCHOOL, v);
                return;
            }
        }
        for (int i = 0; i < found.size() && i < SCHOOL_CANDIDATES; i++)
        {
            ObjectNode c = MAPPER.createObjectNode();
            c.put("universityId", found.get(i).getCnUniversityId());
            c.put("name", found.get(i).getUniversityName());
            candidates.add(c);
        }
        dropped.add(found.isEmpty() ? "school：国内高校库中没有该校名" : "school：校名与高校库不完全一致，只给候选");
    }

    private static boolean aliasOf(AbrCnUniversity u, String name)
    {
        if (u.getAliases() == null)
        {
            return false;
        }
        for (String alias : u.getAliases().split("\\|"))
        {
            if (alias.trim().equalsIgnoreCase(name))
            {
                return true;
            }
        }
        return false;
    }

    private static void major(AbrAssessmentAnswers.Questionnaire q, JsonNode ai, String compact, ObjectNode suggestions, ArrayNode dropped)
    {
        if (!ai.isString())
        {
            return;
        }
        String text = ai.asString().trim();
        if (text.isEmpty() || text.length() > NAME_MAX || !compact.contains(compact(text)))
        {
            dropped.add("major：专业不在原文中");
            return;
        }
        String best = null;
        int bestLen = 0;
        JsonNode field = q.questions().get("target_field");
        for (JsonNode option : field == null ? MAPPER.createArrayNode() : field.path("options"))
        {
            String value = option.path("value").asString("");
            if ("undecided".equals(value))
            {
                continue;
            }
            for (JsonNode kw : option.path("keywords"))
            {
                String k = kw.asString("");
                if (!k.isEmpty() && text.contains(k) && k.length() > bestLen)
                {
                    best = value;
                    bestLen = k.length();
                }
            }
        }
        ObjectNode v = MAPPER.createObjectNode();
        v.put("value", best == null ? "other" : best);
        v.put("text", text);
        v.put("matched", best != null);
        suggestions.set(Q_MAJOR, v);
    }

    /**
     * 分数类题目（均分、英语）：分制须是题目的选项，数值须在原文中出现、在范围内且符合步长
     */
    private static ObjectNode score(JsonNode question, JsonNode ai, Set<String> numbers, boolean withSub, ArrayNode dropped)
    {
        if (question == null || !ai.isObject())
        {
            return null;
        }
        String id = question.path("id").asString("");
        String type = ai.path("type").asString("");
        JsonNode scoreType = null;
        for (JsonNode t : question.path("score_types"))
        {
            if (type.equals(t.path("value").asString()))
            {
                scoreType = t;
            }
        }
        if (scoreType == null)
        {
            dropped.add(id + "：分制不在问卷选项中");
            return null;
        }
        BigDecimal max = null;
        if (scoreType.path("requires_max").asBoolean(false))
        {
            max = ai.path("max").isNumber() ? ai.path("max").decimalValue() : null;
            if (max == null || !inRange(max, scoreType.path("max_field")) || !numbers.contains(norm(max)))
            {
                dropped.add(id + "：满分不在原文中或超出范围");
                return null;
            }
        }
        BigDecimal total = ai.path("total").isNumber() ? ai.path("total").decimalValue() : null;
        if (total == null || !inRange(total, scoreType.path("total")) || (max != null && total.compareTo(max) > 0)
                || !numbers.contains(norm(total)))
        {
            dropped.add(id + "：分数不在原文中或超出范围");
            return null;
        }
        ObjectNode v = MAPPER.createObjectNode();
        v.put("type", type);
        v.put("total", total);
        if (max != null)
        {
            v.put("max", max);
        }
        if (withSub)
        {
            JsonNode sub = ai.path("min_sub");
            if (sub.isNumber() && scoreType.has("min_sub") && inRange(sub.decimalValue(), scoreType.path("min_sub"))
                    && sub.decimalValue().compareTo(total) <= 0 && numbers.contains(norm(sub.decimalValue())))
            {
                v.put("min_sub", sub.decimalValue());
            }
            else
            {
                v.putNull("min_sub");
            }
        }
        return v;
    }

    /**
     * 经历类题目：只建议“有”，数量与级别按题目字段的范围与选项逐项校验，不合格的字段不填
     */
    private static void experience(JsonNode question, JsonNode ai, List<String> keys, ObjectNode suggestions, ArrayNode dropped)
    {
        if (question == null || !ai.isObject())
        {
            return;
        }
        String id = question.path("id").asString("");
        ObjectNode v = MAPPER.createObjectNode();
        v.put("has", "yes");
        for (String key : keys)
        {
            JsonNode value = ai.path(key);
            if (value.isNull() || value.isMissingNode())
            {
                continue;
            }
            JsonNode field = fieldDef(question, key);
            if (field == null)
            {
                continue;
            }
            if (field.has("options"))
            {
                if (value.isString() && hasOption(field, value.asString()))
                {
                    v.put(key, value.asString());
                }
                else
                {
                    dropped.add(id + "." + key + "：不是问卷选项");
                }
                continue;
            }
            JsonNode rule = field.path("validation");
            boolean integer = value.isIntegralNumber() || (value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0);
            if (integer && inRange(value.decimalValue(), rule))
            {
                v.put(key, value.decimalValue().intValue());
            }
            else
            {
                dropped.add(id + "." + key + "：数量超出范围");
            }
        }
        if (v.has("fulltime_months") && (!v.has("total_months") || v.get("fulltime_months").asInt() > v.get("total_months").asInt()))
        {
            v.remove("fulltime_months");
            dropped.add(id + ".fulltime_months：全职月数大于累计月数");
        }
        suggestions.set(id, v);
    }

    private static JsonNode fieldDef(JsonNode question, String key)
    {
        for (JsonNode f : question.path("fields"))
        {
            if (key.equals(f.path("key").asString()))
            {
                return f;
            }
        }
        return null;
    }

    private static boolean hasOption(JsonNode field, String value)
    {
        for (JsonNode o : field.path("options"))
        {
            if (value.equals(o.path("value").asString()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 范围与步长（range 中的 min / max / step，缺省不限）
     */
    static boolean inRange(BigDecimal value, JsonNode range)
    {
        if (range.has("min") && value.compareTo(range.get("min").decimalValue()) < 0)
        {
            return false;
        }
        if (range.has("max") && value.compareTo(range.get("max").decimalValue()) > 0)
        {
            return false;
        }
        if (range.has("step") && range.get("step").decimalValue().signum() > 0)
        {
            BigDecimal base = range.has("min") ? range.get("min").decimalValue() : BigDecimal.ZERO;
            return value.subtract(base).remainder(range.get("step").decimalValue()).signum() == 0;
        }
        return true;
    }

    private static Set<String> numbers(String text)
    {
        Set<String> result = new HashSet<>();
        Matcher m = NUMBER.matcher(text == null ? "" : text);
        while (m.find())
        {
            result.add(norm(new BigDecimal(m.group())));
        }
        return result;
    }

    private static String norm(BigDecimal n)
    {
        return n.stripTrailingZeros().toPlainString();
    }

    private static String compact(String s)
    {
        return s == null ? "" : SPACES.matcher(s).replaceAll("").toLowerCase();
    }

    /**
     * 合并本测评各份文件的识别结果：均分优先取成绩单，其余优先取简历；与当前答案比较标出新填 / 一致 / 不一致
     *
     * @param files 有效文件（按上传时间）
     * @param answers 当前答案
     * @param messages 未识别原因 → 提示文字
     * @return 解析结果
     */
    static AppResumeParseVo merge(List<AbrAssessmentFile> files, ObjectNode answers, Map<String, String> messages)
    {
        List<AppResumeParseVo.File> fileVos = new ArrayList<>();
        Map<String, JsonNode> byKind = new LinkedHashMap<>();
        boolean running = false;
        boolean done = false;
        for (AbrAssessmentFile f : files)
        {
            String status = f.getParseStatus();
            running |= AbrAssessmentFile.STATUS_RUNNING.equals(status);
            String message = null;
            if (AbrAssessmentFile.STATUS_DONE.equals(status))
            {
                done = true;
                JsonNode parsed = AbrAssessmentAnswers.object(f.getParsed());
                byKind.put(f.getFileKind(), parsed);
                int n = parsed.path("suggestions").size();
                message = "已识别 " + n + " 项，请逐项确认";
            }
            else if (AbrAssessmentFile.STATUS_FAILED.equals(status))
            {
                message = messages.getOrDefault(f.getFailReason(), messages.get(null));
            }
            fileVos.add(new AppResumeParseVo.File(f.getFileId(), f.getFileKind(), f.getOriginalName(), f.getFileSize(), status, message,
                    f.getCreateTime()));
        }
        List<AppResumeParseVo.Suggestion> suggestions = new ArrayList<>();
        for (String id : QUESTION_ORDER)
        {
            List<String> order = Q_GPA.equals(id) ? List.of(AbrAssessmentFile.KIND_TRANSCRIPT, AbrAssessmentFile.KIND_RESUME)
                    : List.of(AbrAssessmentFile.KIND_RESUME, AbrAssessmentFile.KIND_TRANSCRIPT);
            for (String kind : order)
            {
                JsonNode parsed = byKind.get(kind);
                JsonNode value = parsed == null ? null : parsed.path("suggestions").get(id);
                if (value != null && !value.isNull())
                {
                    JsonNode current = answers.get(id);
                    String match = current == null || current.isNull() ? AppResumeParseVo.MATCH_NEW
                            : covered(value, current) ? AppResumeParseVo.MATCH_SAME : AppResumeParseVo.MATCH_CONFLICT;
                    suggestions.add(new AppResumeParseVo.Suggestion(id, value, current == null || current.isNull() ? null : current, match, kind));
                    break;
                }
            }
        }
        List<AppCnUniversityVo> candidates = new ArrayList<>();
        boolean hasSchool = suggestions.stream().anyMatch(s -> Q_SCHOOL.equals(s.questionId()));
        for (String kind : List.of(AbrAssessmentFile.KIND_RESUME, AbrAssessmentFile.KIND_TRANSCRIPT))
        {
            JsonNode parsed = byKind.get(kind);
            if (hasSchool || parsed == null || !candidates.isEmpty())
            {
                continue;
            }
            for (JsonNode c : parsed.path("schoolCandidates"))
            {
                candidates.add(new AppCnUniversityVo(c.path("universityId").asLong(), c.path("name").asString()));
            }
        }
        String status = files.isEmpty() ? AppResumeParseVo.STATUS_NONE
                : running ? AbrAssessmentFile.STATUS_RUNNING : done ? AbrAssessmentFile.STATUS_DONE : AbrAssessmentFile.STATUS_FAILED;
        return new AppResumeParseVo(status, fileVos, suggestions, candidates);
    }

    /**
     * 建议值是否已被当前答案包含（对象只比较建议中有的字段）
     */
    static boolean covered(JsonNode suggestion, JsonNode current)
    {
        if (suggestion.isObject() && current.isObject())
        {
            for (Map.Entry<String, JsonNode> e : suggestion.properties())
            {
                JsonNode c = current.get(e.getKey());
                boolean bothEmpty = e.getValue().isNull() && (c == null || c.isNull());
                if (!bothEmpty && (c == null || !AiJson.jsonEquals(e.getValue(), c)))
                {
                    return false;
                }
            }
            return true;
        }
        return AiJson.jsonEquals(suggestion, current);
    }
}

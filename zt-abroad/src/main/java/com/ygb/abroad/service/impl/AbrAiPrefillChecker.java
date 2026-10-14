package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import com.ygb.abroad.ai.AiJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI 预填结果的事后校验（“AI 不猜”的保证）
 * <ol>
 * <li>没有取值的字段：not_found（没有找到可靠来源）；</li>
 * <li>有取值的字段：引句必须在抓取到的页面文字中逐字出现（比对前统一全角 / 半角、去掉空白、弯引号与破折号归一、不区分大小写），
 * 否则丢弃取值，标记 discarded「原文中找不到引句，已丢弃」；</li>
 * <li>取值按人工保存的同一套规则校验（字典编码、数值范围、均分的本科层次编码、截止日日期格式），不合规的丢弃并写明原因。</li>
 * </ol>
 * 只提议取值，不写任何业务表。
 *
 * @author ygb
 */
final class AbrAiPrefillChecker
{
    /** 字段归属：项目基本信息 */
    static final String TARGET_PROGRAM = "program";

    /** 字段归属：申请要求 */
    static final String TARGET_REQUIREMENT = "requirement";

    /** 状态：找到（可确认） */
    static final String STATUS_FOUND = "found";

    /** 状态：页面中没有 */
    static final String STATUS_NOT_FOUND = "not_found";

    /** 状态：已丢弃（引句对不上或取值不合规） */
    static final String STATUS_DISCARDED = "discarded";

    /** 未找到的说明 */
    static final String REASON_NOT_FOUND = "没有找到可靠来源";

    /** 引句对不上的说明 */
    static final String REASON_QUOTE_MISSING = "原文中找不到引句，已丢弃";

    /** 引句规范化后的最少字符数（太短的引句如“6.5”不能作为依据） */
    static final int MIN_QUOTE_CHARS = 4;

    /** 引句最大长度 */
    static final int MAX_QUOTE_CHARS = 500;

    /** 其他语言考试（与前端 requirement.vue 的 LANGUAGE_TESTS 一致） */
    static final Set<String> LANGUAGE_TESTS = Set.of("toefl", "pte", "duolingo", "jlpt", "topik", "testdaf", "dsh", "goethe");

    /** 字典缺失（缓存未加载）时使用的内置编码，与 update_20261013_catalog.sql、update_20261014_degree_secondary.sql 一致 */
    private static final Map<String, Set<String>> DEFAULT_DICTS = Map.of(
            "abr_degree", Set.of("secondary", "bachelor", "master", "phd"),
            "abr_intake", Set.of("fall", "spring", "summer", "winter"),
            "abr_teaching_language", Set.of("en", "ja", "ko", "de", "zh", "other"),
            "abr_test_requirement", Set.of("none", "optional", "required"),
            "abr_undergrad_tier", AbrJsonFields.UNDERGRAD_TIERS,
            "abr_cross_major", Set.of("0", "1", "2"),
            "sys_yes_no", Set.of("Y", "N"));

    /** 字段定义（顺序即返回顺序） */
    private static final List<FieldDef> FIELDS = new ArrayList<>();

    static
    {
        FIELDS.add(new FieldDef("programNameEn", "英文名", TARGET_PROGRAM));
        FIELDS.add(new FieldDef("degree", "学位", TARGET_PROGRAM));
        FIELDS.add(new FieldDef("durationMonths", "学制（月）", TARGET_PROGRAM));
        FIELDS.add(new FieldDef("tuition", "学费（每年）", TARGET_PROGRAM));
        FIELDS.add(new FieldDef("currency", "币种", TARGET_PROGRAM));
        FIELDS.add(new FieldDef("intakes", "入学季", TARGET_PROGRAM));
        FIELDS.add(new FieldDef("teachingLanguage", "授课语言", TARGET_PROGRAM));
        FIELDS.add(new FieldDef("minGpa", "最低均分", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("tiersNotAccepted", "不接受层次", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("ieltsOverall", "雅思总分", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("ieltsMinSub", "雅思小分", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("otherLanguage", "其他语言", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("testRequirement", "标化要求", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("testDetail", "标化分数", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("prerequisites", "先修课", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("prerequisitesRequired", "先修课硬性要求", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("workYears", "工作年限", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("acceptCrossMajor", "跨专业", TARGET_REQUIREMENT));
        FIELDS.add(new FieldDef("deadlines", "截止日", TARGET_REQUIREMENT));
    }

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    /** 字典类型 → 有效编码（为 null 或空时用内置编码） */
    private final Function<String, Set<String>> dictValues;

    /**
     * 创建校验器
     *
     * @param dictValues 字典类型 → 有效编码；可为 null（全部用内置编码）
     */
    AbrAiPrefillChecker(Function<String, Set<String>> dictValues)
    {
        this.dictValues = dictValues;
    }

    /**
     * 校验 AI 输出
     *
     * @param output AI 输出（{字段: {value, quote}}）
     * @param pageText 交给模型的页面文字
     * @return 逐字段结果：fieldName、label、target、value、quote、status、reason
     */
    List<Map<String, Object>> check(JsonNode output, String pageText)
    {
        String page = normalizeForMatch(pageText);
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        Map<String, JsonNode> accepted = new LinkedHashMap<>();
        for (FieldDef def : FIELDS)
        {
            JsonNode field = output == null ? null : output.get(def.name);
            JsonNode value = field == null ? null : field.get("value");
            JsonNode quote = field == null ? null : field.get("quote");
            Map<String, Object> r = result(def);
            results.put(def.name, r);
            if (isEmpty(value))
            {
                notFound(r);
                continue;
            }
            String q = quote != null && quote.isString() ? quote.asString().trim() : "";
            if (q.isEmpty())
            {
                discard(r, "没有附原文引句，已丢弃");
                continue;
            }
            String nq = normalizeForMatch(q);
            if (nq.length() < MIN_QUOTE_CHARS)
            {
                discard(r, "引句过短，不能作为依据，已丢弃");
                continue;
            }
            if (q.length() > MAX_QUOTE_CHARS)
            {
                discard(r, "引句超过 " + MAX_QUOTE_CHARS + " 字，不是逐字引用，已丢弃");
                continue;
            }
            if (!page.contains(nq))
            {
                discard(r, REASON_QUOTE_MISSING);
                continue;
            }
            JsonNode normalized;
            try
            {
                normalized = validate(def.name, value);
            }
            catch (IllegalArgumentException e)
            {
                discard(r, "取值不符合录入规则（" + e.getMessage() + "），已丢弃");
                continue;
            }
            if (isEmpty(normalized))
            {
                notFound(r);
                continue;
            }
            r.put("value", toPlain(normalized));
            r.put("quote", q);
            r.put("status", STATUS_FOUND);
            r.put("reason", null);
            accepted.put(def.name, normalized);
        }
        // 跨字段规则：雅思小分不能高于总分（与人工保存一致）
        JsonNode overall = accepted.get("ieltsOverall");
        JsonNode minSub = accepted.get("ieltsMinSub");
        if (overall != null && minSub != null && minSub.decimalValue().compareTo(overall.decimalValue()) > 0)
        {
            discard(results.get("ieltsMinSub"), "取值不符合录入规则（雅思小分高于总分），已丢弃");
        }
        return new ArrayList<>(results.values());
    }

    /**
     * 引句比对用的规范化：NFKC（全角转半角）、弯引号 / 破折号归一、去掉全部空白与零宽字符、转小写
     *
     * @param text 文本
     * @return 规范化文本
     */
    static String normalizeForMatch(String text)
    {
        if (text == null)
        {
            return "";
        }
        String n = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(n.length());
        n.codePoints().forEach(c -> {
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x200B || c == 0x200C || c == 0x200D || c == 0xFEFF
                    || c == 0x00AD)
            {
                return;
            }
            switch (c)
            {
                case 0x2018: case 0x2019: case 0x201A: case 0x201B: case 0x2032: case 0x00B4: case 0x0060:
                    sb.append('\'');
                    break;
                case 0x201C: case 0x201D: case 0x201E: case 0x201F: case 0x2033: case 0x00AB: case 0x00BB:
                    sb.append('"');
                    break;
                case 0x2010: case 0x2011: case 0x2012: case 0x2013: case 0x2014: case 0x2015: case 0x2212:
                    sb.append('-');
                    break;
                default:
                    sb.appendCodePoint(c);
            }
        });
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * 取值校验与规范化
     *
     * @param field 字段名
     * @param v 取值（非空）
     * @return 规范化后的取值
     * @throws IllegalArgumentException 不合规（消息为原因）
     */
    JsonNode validate(String field, JsonNode v)
    {
        switch (field)
        {
            case "programNameEn":
                return text(v, 300);
            case "degree":
                return dict(v, "abr_degree");
            case "durationMonths":
                return F.numberNode(integer(v, 1, 120));
            case "tuition":
                BigDecimal tuition = number(v, BigDecimal.ZERO, new BigDecimal("9999999999.99")).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
                return num(tuition);
            case "currency":
                String currency = text(v, 3).asString().toUpperCase(Locale.ROOT);
                if (!currency.matches("^[A-Z]{3}$"))
                {
                    throw new IllegalArgumentException("币种应为 3 位字母代码");
                }
                return F.stringNode(currency);
            case "intakes":
                return codes(v, "abr_intake");
            case "teachingLanguage":
                return dict(v, "abr_teaching_language");
            case "minGpa":
                return minGpa(v);
            case "tiersNotAccepted":
                return codes(v, "abr_undergrad_tier");
            case "ieltsOverall":
            case "ieltsMinSub":
                return F.numberNode(ielts(v));
            case "otherLanguage":
                return otherLanguage(v);
            case "testRequirement":
                return dict(v, "abr_test_requirement");
            case "testDetail":
                return testDetail(v);
            case "prerequisites":
                return text(v, 1000);
            case "prerequisitesRequired":
                return dict(v, "sys_yes_no");
            case "workYears":
                return F.numberNode(integer(v, 0, 30));
            case "acceptCrossMajor":
                JsonNode cross = dict(v, "abr_cross_major");
                if ("2".equals(cross.asString()))
                {
                    throw new IllegalArgumentException("“未知”不需要预填");
                }
                return cross;
            case "deadlines":
                return deadlines(v);
            default:
                throw new IllegalArgumentException("未知字段 " + field);
        }
    }

    private static JsonNode text(JsonNode v, int max)
    {
        if (!v.isString())
        {
            throw new IllegalArgumentException("应为文本");
        }
        String s = v.asString().trim();
        if (s.length() > max)
        {
            throw new IllegalArgumentException("不能超过 " + max + " 个字符");
        }
        return F.stringNode(s);
    }

    private JsonNode dict(JsonNode v, String dictType)
    {
        if (!v.isString() && !v.isNumber())
        {
            throw new IllegalArgumentException("应为字典编码");
        }
        String code = v.asString().trim();
        if (!dictCodes(dictType).contains(code))
        {
            throw new IllegalArgumentException("“" + code + "”不是有效的编码");
        }
        return F.stringNode(code);
    }

    private JsonNode codes(JsonNode v, String dictType)
    {
        if (!v.isArray())
        {
            throw new IllegalArgumentException("应为编码数组");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : v)
        {
            seen.add(dict(item, dictType).asString());
        }
        ArrayNode out = F.arrayNode();
        seen.forEach(out::add);
        return out;
    }

    private static int integer(JsonNode v, int min, int max)
    {
        BigDecimal d = number(v, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
        if (d.stripTrailingZeros().scale() > 0)
        {
            throw new IllegalArgumentException("应为整数");
        }
        return d.intValueExact();
    }

    private static BigDecimal number(JsonNode v, BigDecimal min, BigDecimal max)
    {
        if (!v.isNumber())
        {
            throw new IllegalArgumentException("应为数字");
        }
        BigDecimal d = v.decimalValue();
        if (d.compareTo(min) < 0 || d.compareTo(max) > 0)
        {
            throw new IllegalArgumentException("应在 " + min.toPlainString() + "–" + max.toPlainString() + " 之间");
        }
        return d;
    }

    /**
     * 数字节点：整数值用整数节点（80 而不是 80.0），其余保留小数
     */
    private static JsonNode num(BigDecimal d)
    {
        BigDecimal s = d.stripTrailingZeros();
        return s.scale() <= 0 ? F.numberNode(s.longValueExact()) : F.numberNode(s);
    }

    private static BigDecimal ielts(JsonNode v)
    {
        BigDecimal d = number(v, BigDecimal.ZERO, BigDecimal.valueOf(9));
        if (d.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0)
        {
            throw new IllegalArgumentException("雅思分数应为 0.5 的整数倍");
        }
        return d.setScale(1, RoundingMode.UNNECESSARY);
    }

    private JsonNode minGpa(JsonNode v)
    {
        if (!v.isObject())
        {
            throw new IllegalArgumentException("最低均分应为对象");
        }
        ObjectNode out = F.objectNode();
        for (Map.Entry<String, JsonNode> e : v.properties())
        {
            if (!dictCodes("abr_undergrad_tier").contains(e.getKey()))
            {
                throw new IllegalArgumentException("本科层次“" + e.getKey() + "”不存在");
            }
            if (e.getValue() == null || e.getValue().isNull())
            {
                continue;
            }
            out.set(e.getKey(), num(number(e.getValue(), BigDecimal.ZERO, BigDecimal.valueOf(100))));
        }
        return out;
    }

    private static JsonNode otherLanguage(JsonNode v)
    {
        if (!v.isObject())
        {
            throw new IllegalArgumentException("其他语言要求应为对象");
        }
        ObjectNode out = F.objectNode();
        for (Map.Entry<String, JsonNode> e : v.properties())
        {
            String test = e.getKey().trim().toLowerCase(Locale.ROOT);
            if (!LANGUAGE_TESTS.contains(test))
            {
                throw new IllegalArgumentException("语言考试“" + e.getKey() + "”不在可选范围内");
            }
            JsonNode item = e.getValue();
            if (item == null || !item.isObject())
            {
                throw new IllegalArgumentException("语言考试“" + test + "”的要求应为对象");
            }
            ObjectNode o = F.objectNode();
            for (String key : new String[] { "overall", "minSub" })
            {
                JsonNode n = item.get(key);
                if (n != null && !n.isNull())
                {
                    o.set(key, num(number(n, BigDecimal.ZERO, BigDecimal.valueOf(1000))));
                }
            }
            for (String key : new String[] { "level", "note" })
            {
                JsonNode n = item.get(key);
                if (n != null && !n.isNull() && !n.asString().isBlank())
                {
                    o.put(key, text(n, "level".equals(key) ? 50 : 200).asString());
                }
            }
            if (!o.isEmpty())
            {
                out.set(test, o);
            }
        }
        return out;
    }

    private static JsonNode testDetail(JsonNode v)
    {
        if (!v.isObject())
        {
            throw new IllegalArgumentException("标化分数应为对象");
        }
        ObjectNode out = F.objectNode();
        int[][] ranges = { { 260, 340 }, { 205, 805 }, { 200, 800 } };
        String[] keys = { "gre", "gmat_focus", "gmat_classic" };
        for (int k = 0; k < keys.length; k++)
        {
            JsonNode n = v.get(keys[k]);
            if (n != null && !n.isNull())
            {
                out.put(keys[k], integer(n, ranges[k][0], ranges[k][1]));
            }
        }
        for (Map.Entry<String, JsonNode> e : v.properties())
        {
            if (!out.has(e.getKey()) && e.getValue() != null && !e.getValue().isNull() && !List.of(keys).contains(e.getKey()))
            {
                throw new IllegalArgumentException("标化分数“" + e.getKey() + "”不在可选范围内");
            }
        }
        return out;
    }

    private static JsonNode deadlines(JsonNode v)
    {
        if (!v.isObject())
        {
            throw new IllegalArgumentException("截止日应为对象");
        }
        JsonNode rolling = v.get("rolling");
        if (rolling != null && !rolling.isNull() && !rolling.isBoolean())
        {
            throw new IllegalArgumentException("rolling 应为布尔值");
        }
        ArrayNode rounds = F.arrayNode();
        JsonNode list = v.get("rounds");
        if (list != null && !list.isNull())
        {
            if (!list.isArray())
            {
                throw new IllegalArgumentException("rounds 应为数组");
            }
            for (JsonNode round : list)
            {
                String name = round.path("name").asString("").trim();
                String date = round.path("date").asString("").trim();
                if (name.isEmpty() || name.length() > 50)
                {
                    throw new IllegalArgumentException("每个轮次都要有名称（不超过 50 字）");
                }
                try
                {
                    LocalDate.parse(date);
                }
                catch (DateTimeParseException e)
                {
                    throw new IllegalArgumentException("截止日期“" + date + "”应为 YYYY-MM-DD");
                }
                rounds.addObject().put("name", name).put("date", date);
            }
        }
        boolean isRolling = rolling != null && rolling.asBoolean(false);
        if (!isRolling && rounds.isEmpty())
        {
            return F.nullNode();
        }
        ObjectNode out = F.objectNode();
        out.put("rolling", isRolling);
        out.set("rounds", rounds);
        return out;
    }

    private Set<String> dictCodes(String dictType)
    {
        Set<String> codes = dictValues == null ? null : dictValues.apply(dictType);
        return codes == null || codes.isEmpty() ? DEFAULT_DICTS.getOrDefault(dictType, Set.of()) : codes;
    }

    private static boolean isEmpty(JsonNode v)
    {
        if (v == null || v.isNull() || v.isMissingNode())
        {
            return true;
        }
        if (v.isString())
        {
            return v.asString().isBlank();
        }
        if (v.isArray() || v.isObject())
        {
            if (v.isObject() && v.has("rolling"))
            {
                // 截止日：不滚动且没有轮次视为空
                return !v.path("rolling").asBoolean(false) && (v.path("rounds").isMissingNode() || v.path("rounds").isEmpty());
            }
            return v.isEmpty();
        }
        return false;
    }

    private static Map<String, Object> result(FieldDef def)
    {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fieldName", def.name);
        r.put("label", def.label);
        r.put("target", def.target);
        r.put("value", null);
        r.put("quote", null);
        r.put("status", STATUS_NOT_FOUND);
        r.put("reason", REASON_NOT_FOUND);
        return r;
    }

    private static void notFound(Map<String, Object> r)
    {
        r.put("value", null);
        r.put("quote", null);
        r.put("status", STATUS_NOT_FOUND);
        r.put("reason", REASON_NOT_FOUND);
    }

    private static void discard(Map<String, Object> r, String reason)
    {
        r.put("value", null);
        r.put("quote", null);
        r.put("status", STATUS_DISCARDED);
        r.put("reason", reason);
    }

    /**
     * JSON 节点转普通 Java 对象（Map / List / 数字 / 字符串 / 布尔），便于统一序列化与写操作日志
     */
    private static Object toPlain(JsonNode node)
    {
        return AiJson.MAPPER.convertValue(node, Object.class);
    }

    /**
     * 字段定义
     */
    private static class FieldDef
    {
        private final String name;

        private final String label;

        private final String target;

        FieldDef(String name, String label, String target)
        {
            this.name = name;
            this.label = label;
            this.target = target;
        }
    }
}

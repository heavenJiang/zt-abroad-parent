package com.ygb.abroad.service.impl;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 内容规则（纯计算，不访问数据库）：内容编号与口令、工具链接、字段校验、改稿后是否须重新过闸门、闸门结果后的状态
 *
 * @author ygb
 */
final class AbrContentRules
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 内容编号字符（去掉易混淆的 0 o 1 l i） */
    private static final String CODE_CHARS = "23456789abcdefghjkmnpqrstuvwxyz";

    /** 自定义口令：汉字、字母、数字，2–16 位 */
    private static final Pattern CODEWORD = Pattern.compile("^[\\p{IsHan}A-Za-z0-9]{2,16}$");

    /** 话题标签 */
    private static final int MAX_HASHTAGS = 20;

    private static final int MAX_HASHTAG_CHARS = 30;

    /** 引用依据的类型 */
    static final Set<String> CITATION_TYPES = Set.of("school", "program", "requirement", "field_source", "policy", "case", "offer", "url");

    /** 钩子 → C 端页面（未列出的进首页） */
    static final Map<String, String> HOOK_PAGES = Map.of(
            "quick", "pages/quick/index",
            "recognition", "pages/tool/recognition",
            "ielts", "pages/tool/ielts",
            "open", "pages/tool/open",
            "plan", "pages/plan/index");

    private AbrContentRules()
    {
    }

    /**
     * 新的内容编号：c + 9 位随机字符（符合 C 端 ct 参数格式：字母、数字，最多 32 位）
     *
     * @return 内容编号
     */
    static String newContentCode()
    {
        StringBuilder sb = new StringBuilder("c");
        for (int i = 0; i < 9; i++)
        {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 新的默认口令：4 位数字（1000–9999）
     *
     * @return 口令
     */
    static String newCodeword()
    {
        return String.valueOf(1000 + RANDOM.nextInt(9000));
    }

    /**
     * 规范化并校验自定义口令（去掉首尾空白；为空返回 null，表示由系统生成）
     *
     * @param codeword 口令
     * @return 规范化后的口令
     */
    static String normalizeCodeword(String codeword)
    {
        String text = StringUtils.trimToNull(codeword);
        if (text == null)
        {
            return null;
        }
        if (!CODEWORD.matcher(text).matches())
        {
            throw new ServiceException("私信口令只能是 2–16 位汉字、字母或数字");
        }
        return text;
    }

    /**
     * 工具链接：C 端页面 + ch=账号编码 + ct=内容编号
     *
     * @param base C 端地址（以 / 结尾或不带）
     * @param hookTool 钩子
     * @param accountCode 账号编码
     * @param contentCode 内容编号
     * @return 链接
     */
    static String toolLink(String base, String hookTool, String accountCode, String contentCode)
    {
        String root = base.endsWith("/") ? base : base + "/";
        String page = hookTool == null ? "" : HOOK_PAGES.getOrDefault(hookTool, "");
        return root + page + "?ch=" + accountCode + "&ct=" + contentCode;
    }

    /**
     * 校验并规范化稿件中的 JSON 字段与选择项
     *
     * @param c 内容
     * @param kinds 可选内容类型
     * @param degrees 可选学段
     * @param countries 可选国家 / 地区
     * @param hooks 可选钩子
     */
    static void normalize(AbrContent c, Set<String> kinds, Set<String> degrees, Set<String> countries, Set<String> hooks)
    {
        if (!kinds.contains(c.getContentKind()))
        {
            throw new ServiceException("内容类型“" + c.getContentKind() + "”不是可选值");
        }
        c.setDegree(StringUtils.trimToNull(c.getDegree()));
        if (c.getDegree() != null && !degrees.contains(c.getDegree()))
        {
            throw new ServiceException("学段“" + c.getDegree() + "”不是可选值");
        }
        c.setHookTool(StringUtils.trimToNull(c.getHookTool()));
        if (c.getHookTool() != null && !hooks.contains(c.getHookTool()))
        {
            throw new ServiceException("文末工具“" + c.getHookTool() + "”不是可选值");
        }
        c.setCountries(codes(c.getCountries(), "涉及国家 / 地区", countries));
        c.setHashtags(hashtags(c.getHashtags()));
        c.setCitations(citations(c.getCitations()));
        c.setCards(array(c.getCards(), "图卡", true));
        c.setTitle(StringUtils.trim(c.getTitle()));
        c.setBody(c.getBody() == null ? "" : c.getBody());
        for (String text : new String[] { c.getTitle(), c.getBody(), c.getTopic(), c.getHashtags(), c.getRemark() })
        {
            if (StringUtils.isNotBlank(text) && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
            {
                throw new ServiceException("内容中不能包含 < 加字母的写法（类似 HTML 标签）；正文按纯文本保存与发布");
            }
        }
    }

    /**
     * 改稿后是否须重新过闸门：标题、正文、话题、图卡、引用、文末工具、账号任一变化
     *
     * @param before 改前
     * @param after 改后（已规范化）
     * @return 是否须重新检查
     */
    static boolean needsRegate(AbrContent before, AbrContent after)
    {
        return !Objects.equals(before.getTitle(), after.getTitle()) || !Objects.equals(before.getBody(), after.getBody())
                || !sameJson(before.getHashtags(), after.getHashtags()) || !sameJson(before.getCards(), after.getCards())
                || !sameJson(before.getCitations(), after.getCitations()) || !Objects.equals(before.getHookTool(), after.getHookTool())
                || !Objects.equals(before.getAccountId(), after.getAccountId()) || !Objects.equals(before.getCodeword(), after.getCodeword());
    }

    /**
     * 闸门结果后的状态：通过时须人工确认的进待人工确认，否则可发布；拦截回到草稿
     *
     * @param gateStatus 闸门结果
     * @param requiresConfirm 是否须人工确认
     * @return 状态
     */
    static String statusAfterGate(String gateStatus, boolean requiresConfirm)
    {
        if (AbrContent.GATE_PASSED.equals(gateStatus) || AbrContent.GATE_MANUAL_PASSED.equals(gateStatus))
        {
            return requiresConfirm ? AbrContent.STATUS_PENDING_CONFIRM : AbrContent.STATUS_READY;
        }
        return AbrContent.STATUS_DRAFT;
    }

    /**
     * AI 生成的内容通过闸门前须带 AI 生成标识
     *
     * @param c 内容
     * @param label 标识文字
     * @return 缺少时的说明，满足返回 null
     */
    static String missingAiLabel(AbrContent c, String label)
    {
        if (!"Y".equals(c.getAiGenerated()) || StringUtils.isBlank(label))
        {
            return null;
        }
        return StringUtils.defaultString(c.getBody()).contains(label) ? null : "AI 生成的内容须在正文中带标识“" + label + "”";
    }

    private static String codes(String json, String label, Set<String> allowed)
    {
        JsonNode node = parse(json, label, "[]");
        if (!node.isArray())
        {
            throw new ServiceException(label + "必须是 JSON 数组");
        }
        Set<String> set = new LinkedHashSet<>();
        for (JsonNode n : node)
        {
            String v = n.isString() ? n.asString().trim() : "";
            if (!allowed.contains(v))
            {
                throw new ServiceException(label + "中的“" + n.asString() + "”不是可选值");
            }
            set.add(v);
        }
        ArrayNode out = MAPPER.createArrayNode();
        set.forEach(out::add);
        return MAPPER.writeValueAsString(out);
    }

    private static String hashtags(String json)
    {
        JsonNode node = parse(json, "话题标签", "[]");
        if (!node.isArray())
        {
            throw new ServiceException("话题标签必须是 JSON 字符串数组");
        }
        Set<String> set = new LinkedHashSet<>();
        for (JsonNode n : node)
        {
            String v = n.isString() ? n.asString().trim().replaceFirst("^#", "") : "";
            if (v.isEmpty() || v.length() > MAX_HASHTAG_CHARS)
            {
                throw new ServiceException("话题标签每个须为 1–" + MAX_HASHTAG_CHARS + " 个字符");
            }
            set.add(v);
        }
        if (set.size() > MAX_HASHTAGS)
        {
            throw new ServiceException("话题标签最多 " + MAX_HASHTAGS + " 个");
        }
        ArrayNode out = MAPPER.createArrayNode();
        set.forEach(out::add);
        return MAPPER.writeValueAsString(out);
    }

    /**
     * 引用依据：对象数组，type 为可选类型；院校库记录须有 id，url 类须有 http(s) 地址
     */
    private static String citations(String json)
    {
        JsonNode node = parse(json, "引用依据", "[]");
        if (!node.isArray())
        {
            throw new ServiceException("引用依据必须是 JSON 数组");
        }
        for (JsonNode n : node)
        {
            String type = n.path("type").asString("");
            if (!n.isObject() || !CITATION_TYPES.contains(type))
            {
                throw new ServiceException("引用依据每项须为对象，type 为 " + String.join(" / ", CITATION_TYPES.stream().sorted().toList()));
            }
            if ("url".equals(type) ? !n.path("url").asString("").matches("^https?://.+") : !n.path("id").isIntegralNumber())
            {
                throw new ServiceException("引用依据：院校库、政策、案例记录须有数字 id，网页须有 http(s) 地址");
            }
        }
        return MAPPER.writeValueAsString(node);
    }

    private static String array(String json, String label, boolean objects)
    {
        JsonNode node = parse(json, label, "[]");
        if (!node.isArray())
        {
            throw new ServiceException(label + "必须是 JSON 数组");
        }
        for (JsonNode n : node)
        {
            if (objects && !n.isObject())
            {
                throw new ServiceException(label + "每项须为对象");
            }
        }
        return MAPPER.writeValueAsString(node);
    }

    private static JsonNode parse(String json, String label, String empty)
    {
        try
        {
            return MAPPER.readTree(StringUtils.isBlank(json) ? empty : json);
        }
        catch (JacksonException e)
        {
            throw new ServiceException(label + "不是合法的 JSON");
        }
    }

    private static boolean sameJson(String a, String b)
    {
        try
        {
            return MAPPER.readTree(StringUtils.isBlank(a) ? "[]" : a).equals(MAPPER.readTree(StringUtils.isBlank(b) ? "[]" : b));
        }
        catch (JacksonException e)
        {
            return false;
        }
    }
}

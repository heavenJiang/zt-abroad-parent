package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ygb.abroad.ai.AiFilterHit;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.growth.compliance.ComplianceChecker;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * 写稿规则（纯计算，不访问数据库）：给大模型的院校库事实、文末引导、正文拼装、重写时的拦截说明
 *
 * @author ygb
 */
final class AbrContentWriterRules
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 过期或未录入时的写法（与项目页、图卡一致） */
    static final String UNVERIFIED = "以官网为准";

    /** 币种 → 中文名称（写成“35,000 英镑 / 年”，合规闸门可核对币种与金额） */
    static final Map<String, String> CURRENCY_NAMES = Map.of("GBP", "英镑", "AUD", "澳元", "SGD", "新币", "HKD", "港币", "USD", "美元",
            "EUR", "欧元", "JPY", "日元", "KRW", "韩元", "CNY", "人民币");

    /** 重写时最多带回的拦截项 */
    static final int MAX_FEEDBACK = 10;

    private AbrContentWriterRules()
    {
    }

    /**
     * 一个项目可写进稿件的事实：90 天内核对过的给雅思、截止、学费、学制（没录入的不给）；过期的只给名称与“以官网为准”
     *
     * @param p 项目
     * @param r 最新申请要求（可为 null）
     * @param country 国家 / 地区名称
     * @param today 今天（北京时间）
     * @return 事实
     */
    static Map<String, Object> programFacts(AbrProgram p, AbrRequirement r, String country, LocalDate today)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("programId", p.getProgramId());
        m.put("school", p.getSchoolName());
        m.put("program", p.getProgramName());
        m.put("country", country);
        LocalDate verified = MarketingCalendar.toLocalDate(p.getVerifiedTime());
        m.put("verified", verified == null ? "" : verified.toString());
        if (AbrLookupTools.stale(p, today))
        {
            m.put("facts", UNVERIFIED);
            return m;
        }
        Map<String, String> facts = new LinkedHashMap<>();
        if (r != null && r.getIeltsOverall() != null)
        {
            facts.put("雅思", "雅思 " + score(r.getIeltsOverall()) + (r.getIeltsMinSub() == null ? "" : "（小分 " + score(r.getIeltsMinSub()) + "）"));
        }
        String deadlines = deadlines(r == null ? null : r.getDeadlines());
        if (deadlines != null)
        {
            facts.put("截止", deadlines);
        }
        if (p.getTuition() != null)
        {
            String cur = CURRENCY_NAMES.get(StringUtils.trimToEmpty(p.getCurrency()).toUpperCase());
            facts.put("学费", new DecimalFormat("#,##0").format(p.getTuition()) + " " + (cur == null ? StringUtils.defaultString(p.getCurrency()) : cur) + " / 年");
        }
        if (p.getDurationMonths() != null)
        {
            facts.put("学制", p.getDurationMonths() + " 个月");
        }
        m.put("facts", facts.isEmpty() ? UNVERIFIED : facts);
        return m;
    }

    /**
     * 截止轮次：“2026-11-15（Round 1）；2027-01-31（Round 2）”，滚动录取写明；没有时为 null
     */
    static String deadlines(String json)
    {
        JsonNode d;
        try
        {
            d = MAPPER.readTree(StringUtils.isBlank(json) ? "{}" : json);
        }
        catch (JacksonException e)
        {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode r : d.path("rounds"))
        {
            try
            {
                LocalDate date = LocalDate.parse(r.path("date").asString(""));
                String name = r.path("name").asString("").trim();
                parts.add(date + (name.isEmpty() ? "" : "（" + name + "）"));
            }
            catch (DateTimeParseException e)
            {
                // 日期格式不对的轮次不写
            }
        }
        if (d.path("rolling").asBoolean(false))
        {
            parts.add("滚动录取");
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private static String score(BigDecimal v)
    {
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 文末引导：模板中的 {codeword}、{hook} 替换为私信口令和工具名称
     *
     * @param template 模板（为空时不加引导）
     * @param codeword 私信口令
     * @param hook 工具名称
     * @return 引导文字
     */
    static String closing(String template, String codeword, String hook)
    {
        if (StringUtils.isBlank(template))
        {
            return "";
        }
        return template.replace("{codeword}", StringUtils.defaultString(codeword)).replace("{hook}", StringUtils.defaultIfBlank(hook, "30 秒快测"));
    }

    /**
     * 拼装正文：模型正文 + 文末引导 + AI 生成标识（模型自己写的标识先去掉，避免重复）
     *
     * @param body 模型正文
     * @param closing 文末引导
     * @param aiLabel AI 生成标识
     * @return 正文
     */
    static String assemble(String body, String closing, String aiLabel)
    {
        String text = StringUtils.defaultString(body).trim();
        if (StringUtils.isNotBlank(aiLabel))
        {
            text = text.replace(aiLabel, "").trim();
        }
        StringBuilder sb = new StringBuilder(text);
        if (StringUtils.isNotBlank(closing))
        {
            sb.append("\n\n").append(closing.trim());
        }
        if (StringUtils.isNotBlank(aiLabel))
        {
            sb.append("\n\n").append(aiLabel);
        }
        return sb.toString();
    }

    /**
     * 重写时带回的拦截说明：类别、命中的文字、修改建议
     *
     * @param result 合规检查结果
     * @return 说明
     */
    static List<String> feedback(ComplianceChecker.Result result)
    {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> b : result.blocked())
        {
            if (out.size() >= MAX_FEEDBACK)
            {
                break;
            }
            String matched = b.get("matched") == null ? "" : "命中「" + b.get("matched") + "」";
            String detail = b.get("detail") == null ? "" : String.valueOf(b.get("detail"));
            String text = (b.get("category") + "：" + matched + (matched.isEmpty() || detail.isEmpty() ? "" : "，") + detail).trim();
            out.add(text + "（" + b.get("suggestion") + "）");
        }
        return out;
    }

    /**
     * 网关输出过滤命中项转为重写说明
     *
     * @param flags 命中项
     * @return 说明
     */
    static List<String> filterFeedback(List<AiFilterHit> flags)
    {
        List<String> out = new ArrayList<>();
        for (AiFilterHit f : flags)
        {
            if (out.size() >= MAX_FEEDBACK)
            {
                break;
            }
            out.add(f.getLabel() + "：" + StringUtils.defaultString(f.getPath()).replaceFirst("^/", "") + " 中出现「" + f.getWord() + "」（删去或改写）");
        }
        return out;
    }

    /**
     * 网关输出过滤命中项转为例外队列的命中项（字段、命中的文字），供回流评测集生成断言
     *
     * @param flags 命中项
     * @return JSON 数组
     */
    static String filterFlags(List<AiFilterHit> flags)
    {
        ArrayNode arr = MAPPER.createArrayNode();
        for (AiFilterHit f : flags)
        {
            arr.addObject().put("category", f.getType()).put("field", StringUtils.defaultString(f.getPath()).replaceFirst("^/", ""))
                    .put("matched", f.getWord());
        }
        return arr.toString();
    }

    /**
     * 话题标签：去掉 #、空白与重复，最多 10 个、每个不超过 30 字
     *
     * @param node 模型输出的话题
     * @return 话题
     */
    static List<String> hashtags(JsonNode node)
    {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray())
        {
            for (JsonNode n : node)
            {
                String t = n.isString() ? n.asString().trim().replaceFirst("^#", "").trim() : "";
                if (!t.isEmpty() && t.length() <= 30 && !out.contains(t) && out.size() < 10)
                {
                    out.add(t);
                }
            }
        }
        return out;
    }
}

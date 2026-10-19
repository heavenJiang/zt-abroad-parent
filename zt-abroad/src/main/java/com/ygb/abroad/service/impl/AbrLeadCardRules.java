package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * 线索卡片规则（纯计算）：档位与英语的说明、AI 输出校验
 *
 * @author ygb
 */
final class AbrLeadCardRules
{
    /** 测评档位说明 */
    static final Map<String, String> TIERS = Map.of("reach", "冲刺", "match", "主攻", "safety", "保底", "needs_improvement", "补足后可申");

    /** AI 文字中不允许出现的写法：百分比、概率、几成把握、手机号 */
    static final Pattern FORBIDDEN = Pattern.compile("[%％]|概率|几成|[一二三四五六七八九十\\d]成把握|(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    static final int SUMMARY_MAX = 200;

    static final int ITEM_MAX = 60;

    static final int OPENER_MAX = 80;

    private AbrLeadCardRules()
    {
    }

    /**
     * 英语情况：有雅思等效分时写分数，否则写考试状态
     *
     * @param total 雅思等效总分
     * @param minSub 雅思等效小分
     * @param statusLabel 考试状态说明
     * @return 说明
     */
    static String english(Object total, Object minSub, String statusLabel)
    {
        if (total instanceof Number t)
        {
            String s = "雅思 " + new BigDecimal(t.toString()).setScale(1, RoundingMode.HALF_UP).toPlainString();
            if (minSub instanceof Number m)
            {
                s += "（小分 " + new BigDecimal(m.toString()).setScale(1, RoundingMode.HALF_UP).toPlainString() + "）";
            }
            return s;
        }
        return statusLabel == null ? "" : statusLabel;
    }

    /**
     * 测评结果中每档最多 2 个代表项目的ID（按结果中的顺序）
     *
     * @param programs 结果中的项目（jsonb 数组）
     * @return [项目ID, 档位]
     */
    static List<Object[]> representatives(JsonNode programs)
    {
        List<Object[]> out = new ArrayList<>();
        Map<String, Integer> per = new HashMap<>();
        if (programs != null && programs.isArray())
        {
            for (JsonNode p : programs)
            {
                String tier = p.path("tier").asString("");
                if (!TIERS.containsKey(tier) || !p.path("programId").isIntegralNumber() || per.merge(tier, 1, Integer::sum) > 2)
                {
                    continue;
                }
                List<String> missing = new ArrayList<>();
                for (JsonNode m : p.path("missing"))
                {
                    if (m.isString())
                    {
                        missing.add(m.asString());
                    }
                }
                out.add(new Object[] { p.path("programId").asLong(), TIERS.get(tier), missing });
            }
        }
        return out;
    }

    /**
     * 校验 AI 输出：摘要与各条的长度、条数 1–3、不得有百分比 / 概率 / 手机号
     *
     * @param out 输出
     * @return 不合格原因，合格为 null
     */
    static String validate(JsonNode out)
    {
        if (out == null || !out.isObject())
        {
            return "输出不是对象";
        }
        String summary = out.path("summary").asString("").trim();
        if (summary.length() < 10 || summary.length() > SUMMARY_MAX)
        {
            return "摘要须为 10–" + SUMMARY_MAX + " 字";
        }
        List<String> texts = new ArrayList<>();
        texts.add(summary);
        JsonNode concerns = out.path("concerns");
        if (!concerns.isArray() || concerns.isEmpty() || concerns.size() > 3)
        {
            return "顾虑须为 1–3 条";
        }
        for (JsonNode c : concerns)
        {
            String t = c.path("text").asString("").trim();
            String b = c.path("basis").asString("").trim();
            if (t.isEmpty() || t.length() > ITEM_MAX || b.isEmpty() || b.length() > ITEM_MAX)
            {
                return "每条顾虑与依据须为 1–" + ITEM_MAX + " 字";
            }
            texts.add(t);
            texts.add(b);
        }
        JsonNode openers = out.path("openers");
        if (!openers.isArray() || openers.isEmpty() || openers.size() > 3)
        {
            return "开场话题须为 1–3 条";
        }
        for (JsonNode o : openers)
        {
            String t = o.asString("").trim();
            if (t.length() < 4 || t.length() > OPENER_MAX)
            {
                return "每条开场话题须为 4–" + OPENER_MAX + " 字";
            }
            texts.add(t);
        }
        for (String t : texts)
        {
            if (FORBIDDEN.matcher(t).find())
            {
                return "出现了百分比、概率或手机号：" + t;
            }
        }
        return null;
    }
}

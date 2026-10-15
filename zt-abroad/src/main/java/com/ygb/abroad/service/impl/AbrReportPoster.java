package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ygb.abroad.domain.vo.AppReportVo;
import com.ygb.abroad.growth.card.Card;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 定位海报的图卡数据（纯函数）：只取报告结构化部分中的档位数量与代表校名，不含 AI 文字
 *
 * 隐私默认：不显示姓名、本科学校、具体均分；学生主动勾选才显示本科学校、均分，未成年人勾选无效（分享内容不含孩子的学校）。
 *
 * @author ygb
 */
final class AbrReportPoster
{
    /** 分享对象类型 */
    static final String KIND = "report_poster";

    /** 每档最多显示的校名数 */
    static final int SCHOOLS = 3;

    /** 二维码说明 */
    static final String QR_CAPTION = "扫码测测你能申哪些学校";

    private AbrReportPoster()
    {
    }

    /**
     * 组装图卡
     *
     * @param report 报告（须为 full 模式）
     * @param answers 测评答案（只在学生勾选时取本科学校、均分）
     * @param minor 是否未成年
     * @param showSchool 学生勾选显示本科学校
     * @param showGpa 学生勾选显示均分
     * @param countryLabels 已出结果的国家 / 地区名称
     * @param fieldLabel 专业方向名称
     * @param degreeLabel 学段名称
     * @param brand 品牌名
     * @param qrUrl 二维码链接（带分享码）
     * @return 图卡
     */
    static Card card(AppReportVo report, JsonNode answers, boolean minor, boolean showSchool, boolean showGpa, List<String> countryLabels,
            String fieldLabel, String degreeLabel, String brand, String qrUrl)
    {
        List<String> kicker = new ArrayList<>();
        if (!countryLabels.isEmpty())
        {
            kicker.add(String.join(" / ", countryLabels));
        }
        if (StringUtils.isNotBlank(fieldLabel))
        {
            kicker.add(fieldLabel);
        }
        if (StringUtils.isNotBlank(degreeLabel))
        {
            kicker.add(degreeLabel);
        }
        int match = 0;
        List<Card.Section> sections = new ArrayList<>();
        for (AppReportVo.Tier t : report.tiers())
        {
            if ("match".equals(t.tier()))
            {
                match = t.count();
            }
            Set<String> schools = new LinkedHashSet<>();
            t.programs().forEach(p -> {
                if (StringUtils.isNotBlank(p.schoolName()) && schools.size() < SCHOOLS)
                {
                    schools.add(p.schoolName());
                }
            });
            List<String> lines = schools.isEmpty() ? List.of("暂无") : List.copyOf(schools);
            sections.add(new Card.Section(AbrReportExplainer.TIER_LABELS.get(t.tier()), "共 " + t.count() + " 个项目", lines));
        }
        List<String> extras = new ArrayList<>();
        if (!minor && showSchool)
        {
            String name = answers.path("undergrad_school").path("name").asString("").trim();
            if (!name.isEmpty())
            {
                extras.add("本科：" + name);
            }
        }
        if (!minor && showGpa)
        {
            String gpa = gpaText(answers.path("gpa"));
            if (gpa != null)
            {
                extras.add("均分：" + gpa);
            }
        }
        String footnote = "结果由选校引擎按院校库计算，仅供参考，不代表录取承诺"
                + (report.dataNote() != null && report.dataNote().verifiedTo() != null ? "；院校信息更新于 " + report.dataNote().verifiedTo() : "");
        return new Card(brand, String.join(" · ", kicker), "主攻档 " + match + " 个项目", sections, extras, qrUrl, QR_CAPTION, footnote);
    }

    /**
     * 均分展示：百分制“85”，4 / 5 分制“3.6 / 4”，其他“8.5 / 10”
     */
    static String gpaText(JsonNode gpa)
    {
        if (!gpa.path("total").isNumber())
        {
            return null;
        }
        String total = plain(gpa.path("total").decimalValue());
        String type = gpa.path("type").asString("");
        if (gpa.path("max").isNumber())
        {
            return total + " / " + plain(gpa.path("max").decimalValue());
        }
        if (type.startsWith("gpa") && type.length() > 3 && Character.isDigit(type.charAt(3)))
        {
            return total + " / " + type.substring(3);
        }
        return total;
    }

    private static String plain(BigDecimal v)
    {
        return v.stripTrailingZeros().toPlainString();
    }
}

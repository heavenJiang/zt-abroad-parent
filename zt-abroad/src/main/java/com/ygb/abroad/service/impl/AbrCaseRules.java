package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrCase;
import com.ygb.abroad.growth.card.Card;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 录取案例规则（纯计算）：校验与规范、对外泛化（均分区间）、能否公开展示、offer 卡内容
 *
 * @author ygb
 */
final class AbrCaseRules
{
    static final Set<String> RESULTS = Set.of("admit", "reject", "waitlist", "withdrawn");

    static final Set<String> VERIFY_LEVELS = Set.of("A", "B", "C", "D");

    static final Set<String> SOURCES = Set.of("signed", "self_report", "manual");

    /** 能公开展示的核验等级 */
    static final Set<String> PUBLIC_LEVELS = Set.of("A", "B");

    static final Pattern SEASON = Pattern.compile("^20\\d{2}-(fall|spring|summer|winter)$");

    static final Map<String, String> INTAKES = Map.of("fall", "秋", "spring", "春", "summer", "夏", "winter", "冬");

    /** 均分区间宽度 */
    static final int GPA_BAND = 3;

    /** 区间下界：低于此值只显示“75 以下” */
    static final int GPA_FLOOR = 75;

    private AbrCaseRules()
    {
    }

    /**
     * 校验并规范：申请季、国家、学段、院校与项目名称、结果、核验等级、来源、均分与雅思范围；授权字段只取 Y / N
     *
     * @param c 案例
     */
    static void validate(AbrCase c)
    {
        if (StringUtils.isBlank(c.getSeason()) || !SEASON.matcher(c.getSeason().trim()).matches())
        {
            throw new ServiceException("申请季格式为 年份-入学季，如 2027-fall");
        }
        c.setSeason(c.getSeason().trim());
        if (StringUtils.isBlank(c.getCountry()) || StringUtils.isBlank(c.getDegree()))
        {
            throw new ServiceException("请选择国家 / 地区与学段");
        }
        if (StringUtils.isBlank(c.getSchoolName()) || StringUtils.isBlank(c.getProgramName()))
        {
            throw new ServiceException("请填写院校与项目名称");
        }
        c.setSchoolName(StringUtils.substring(c.getSchoolName().trim(), 0, 200));
        c.setProgramName(StringUtils.substring(c.getProgramName().trim(), 0, 200));
        if (!RESULTS.contains(c.getResult()))
        {
            throw new ServiceException("结果只能是 admit / reject / waitlist / withdrawn");
        }
        c.setVerifyLevel(StringUtils.defaultIfBlank(c.getVerifyLevel(), "C"));
        if (!VERIFY_LEVELS.contains(c.getVerifyLevel()))
        {
            throw new ServiceException("核验等级只能是 A / B / C / D");
        }
        c.setSource(StringUtils.defaultIfBlank(c.getSource(), "manual"));
        if (!SOURCES.contains(c.getSource()))
        {
            throw new ServiceException("来源只能是 signed / self_report / manual");
        }
        if (c.getGpa() != null && (c.getGpa().signum() < 0 || c.getGpa().compareTo(BigDecimal.valueOf(100)) > 0))
        {
            throw new ServiceException("均分须为 0–100（百分制）");
        }
        if (c.getIelts() != null && (c.getIelts().signum() < 0 || c.getIelts().compareTo(BigDecimal.valueOf(9)) > 0))
        {
            throw new ServiceException("雅思须为 0–9");
        }
        c.setAlgoConsent("Y".equals(c.getAlgoConsent()) ? "Y" : "N");
        c.setPublicityConsent("Y".equals(c.getPublicityConsent()) ? "Y" : "N");
        c.setStatus("1".equals(c.getStatus()) ? "1" : "0");
        if (StringUtils.length(c.getExperience()) > 500 || StringUtils.length(c.getTestScores()) > 200 || StringUtils.length(c.getScholarship()) > 200)
        {
            throw new ServiceException("经历概要最多 500 字，标化与奖学金最多 200 字");
        }
    }

    /**
     * 均分对外显示的区间（3 分一档，75 以下合并）
     *
     * @param gpa 均分
     * @return 区间（没有均分为 null）
     */
    static String gpaBand(BigDecimal gpa)
    {
        if (gpa == null)
        {
            return null;
        }
        int g = gpa.intValue();
        if (g < GPA_FLOOR)
        {
            return GPA_FLOOR + " 以下";
        }
        int low = GPA_FLOOR + (g - GPA_FLOOR) / GPA_BAND * GPA_BAND;
        return low + "–" + Math.min(100, low + GPA_BAND);
    }

    /**
     * 均分区间的两个端点（75 以下时只有 75）
     *
     * @param gpa 均分
     * @return 端点
     */
    static List<BigDecimal> gpaBounds(BigDecimal gpa)
    {
        if (gpa == null)
        {
            return List.of();
        }
        int g = gpa.intValue();
        if (g < GPA_FLOOR)
        {
            return List.of(BigDecimal.valueOf(GPA_FLOOR));
        }
        int low = GPA_FLOOR + (g - GPA_FLOOR) / GPA_BAND * GPA_BAND;
        return List.of(BigDecimal.valueOf(low), BigDecimal.valueOf(Math.min(100, low + GPA_BAND)));
    }

    /**
     * 申请季的中文写法（2027-fall → 2027 秋）
     *
     * @param season 申请季
     * @return 文字
     */
    static String seasonLabel(String season)
    {
        String s = StringUtils.defaultString(season);
        int i = s.indexOf('-');
        return i < 0 ? s : s.substring(0, i) + " " + INTAKES.getOrDefault(s.substring(i + 1), s.substring(i + 1));
    }

    /**
     * 能否公开展示（offer 卡、offer 墙、内容引用）：录取、核验 A / B、宣传授权且未撤回、状态正常
     *
     * @param c 案例
     * @return 是否
     */
    static boolean publicEligible(AbrCase c)
    {
        return c != null && "admit".equals(c.getResult()) && PUBLIC_LEVELS.contains(c.getVerifyLevel()) && "Y".equals(c.getPublicityConsent())
                && c.getRevokeTime() == null && "0".equals(c.getStatus());
    }

    /**
     * 对外显示的背景（只用泛化后的信息：院校层次、专业方向、均分区间、语言）
     *
     * @param c 案例
     * @param label 字典标签（字典类型, 值 → 标签）
     * @return 行
     */
    static List<String> background(AbrCase c, BiFunction<String, String, String> label)
    {
        List<String> lines = new ArrayList<>();
        String tier = StringUtils.isBlank(c.getUndergradTier()) ? null : label.apply("abr_undergrad_tier", c.getUndergradTier());
        String major = StringUtils.isBlank(c.getMajorDirection()) ? null : label.apply("abr_major_direction", c.getMajorDirection());
        if (tier != null || major != null)
        {
            lines.add(String.join(" · ", List.of(StringUtils.defaultString(tier, "本科"), StringUtils.defaultString(major, "专业未填"))));
        }
        String band = gpaBand(c.getGpa());
        if (band != null)
        {
            lines.add("均分 " + band);
        }
        if (c.getIelts() != null)
        {
            lines.add("雅思 " + c.getIelts().setScale(1, RoundingMode.HALF_UP).toPlainString());
        }
        return lines;
    }

    /**
     * offer 卡
     *
     * @param c 案例（须可公开）
     * @param label 字典标签
     * @param brand 品牌名
     * @param qrLink 二维码链接
     * @return 图卡
     */
    static Card card(AbrCase c, BiFunction<String, String, String> label, String brand, String qrLink)
    {
        String kicker = seasonLabel(c.getSeason()) + " · " + label.apply("abr_country", c.getCountry()) + " · " + label.apply("abr_degree", c.getDegree());
        List<Card.Section> sections = new ArrayList<>();
        List<String> program = new ArrayList<>(List.of(c.getProgramName()));
        if (StringUtils.isNotBlank(c.getScholarship()))
        {
            program.add("奖学金：" + c.getScholarship());
        }
        sections.add(new Card.Section("项目", null, program));
        List<String> bg = background(c, label);
        if (!bg.isEmpty())
        {
            sections.add(new Card.Section("背景", null, bg));
        }
        return new Card(brand, kicker, "录取 · " + c.getSchoolName(), sections, List.of(), qrLink, "测一下你能申哪些", "已获本人授权展示，背景信息已泛化；录取结果因人而异");
    }
}

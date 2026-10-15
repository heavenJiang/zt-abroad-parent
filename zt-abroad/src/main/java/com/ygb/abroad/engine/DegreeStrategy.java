package com.ygb.abroad.engine;

/**
 * 按学段选择引擎策略（《获客系统设计》第四节「各学段的差异」；开发规范 14.1）
 *
 * 1a 只实现硕士（硬门槛 + 综合分 + 分档）；美高、美本（学术匹配）与博士（只做匹配、不分档）在学段扩展路线 M11–M13 实现，
 * 实现前一律返回“由顾问评估”，不给档位。
 *
 * @author ygb
 */
public enum DegreeStrategy
{
    /** 美高：学术匹配（SSAT 分位、托福与学校公开录取数据），M12 */
    SECONDARY("secondary", "academic_match", false, true),

    /** 美本：学术匹配（Common Data Set），M11 */
    BACHELOR("bachelor", "academic_match", false, true),

    /** 硕士：硬门槛 + 综合分 + 录取可能性 + 冲稳保 */
    MASTER("master", "tiered", true, true),

    /** 博士：研究方向匹配清单，不分冲稳保，M13 */
    PHD("phd", "match_only", false, false);

    /** 未实现策略的学段在结果上的提示码 */
    public static final String NOTICE_CONSULTANT_REVIEW = "degree_consultant_review";

    private final String degree;

    private final String method;

    private final boolean implemented;

    private final boolean tiered;

    DegreeStrategy(String degree, String method, boolean implemented, boolean tiered)
    {
        this.degree = degree;
        this.method = method;
        this.implemented = implemented;
        this.tiered = tiered;
    }

    /**
     * 按学段取策略
     *
     * @param degree 学段（abr_degree）
     * @return 策略；未知学段返回 null
     */
    public static DegreeStrategy of(String degree)
    {
        for (DegreeStrategy s : values())
        {
            if (s.degree.equals(degree))
            {
                return s;
            }
        }
        return null;
    }

    /**
     * 学段编码
     *
     * @return abr_degree 字典值
     */
    public String degree()
    {
        return degree;
    }

    /**
     * 评估方式
     *
     * @return tiered 分档 / academic_match 学术匹配 / match_only 只做匹配
     */
    public String method()
    {
        return method;
    }

    /**
     * 引擎是否已实现该学段（未实现时结果由顾问评估）
     *
     * @return 是否已实现
     */
    public boolean implemented()
    {
        return implemented;
    }

    /**
     * 结果是否分冲 / 稳 / 保（博士不分档）
     *
     * @return 是否分档
     */
    public boolean tiered()
    {
        return tiered;
    }
}

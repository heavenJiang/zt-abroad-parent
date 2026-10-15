package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端首屏：学段及其开放情况
 * 
 * @author ygb
 */
public class AppDegreeVo
{
    /** 开放程度：可测评并出选校结果 */
    public static final String MODE_ASSESSMENT = "assessment";

    /** 开放程度：可测评，结果由顾问评估 */
    public static final String MODE_QUESTIONNAIRE = "questionnaire";

    /** 开放程度：暂无问卷，由顾问评估 */
    public static final String MODE_CONSULTANT = "consultant";

    /** 学段（secondary / bachelor / master / phd） */
    private String degree;

    /** 学段名称 */
    private String label;

    /** 开放程度（assessment / questionnaire / consultant） */
    private String mode;

    /** 结果形式（tiered 冲稳保 / academic_match 学术匹配 / match_only 匹配清单不分档） */
    private String method;

    /** 已能出选校结果的国家 / 地区 */
    private List<String> openCountries;

    public String getDegree()
    {
        return degree;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public String getMethod()
    {
        return method;
    }

    public void setMethod(String method)
    {
        this.method = method;
    }

    public List<String> getOpenCountries()
    {
        return openCountries;
    }

    public void setOpenCountries(List<String> openCountries)
    {
        this.openCountries = openCountries;
    }
}

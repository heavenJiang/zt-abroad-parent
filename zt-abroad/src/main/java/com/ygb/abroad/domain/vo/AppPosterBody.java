package com.ygb.abroad.domain.vo;

/**
 * C 端：生成定位海报的请求（默认只显示档位和校名；学生主动勾选才显示更多，未成年人勾选无效）
 *
 * @author ygb
 */
public class AppPosterBody
{
    /** 显示本科学校 */
    private boolean showSchool;

    /** 显示均分 */
    private boolean showGpa;

    public boolean isShowSchool()
    {
        return showSchool;
    }

    public void setShowSchool(boolean showSchool)
    {
        this.showSchool = showSchool;
    }

    public boolean isShowGpa()
    {
        return showGpa;
    }

    public void setShowGpa(boolean showGpa)
    {
        this.showGpa = showGpa;
    }
}

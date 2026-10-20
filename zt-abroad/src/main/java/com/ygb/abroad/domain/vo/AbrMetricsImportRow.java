package com.ygb.abroad.domain.vo;

import com.ygb.common.annotation.Excel;

/**
 * 效果数据导入的一行（M7-06：没有接口的平台由账号负责人每周导入；按发布链接或内容编号找发布记录）
 *
 * @author ygb
 */
public class AbrMetricsImportRow
{
    /** 发布链接（优先按它匹配） */
    @Excel(name = "发布链接")
    private String postUrl;

    /** 内容编号（没有链接时按它匹配最近一次已发布的记录） */
    @Excel(name = "内容编号")
    private String contentCode;

    /** 阅读 / 播放 */
    @Excel(name = "阅读或播放")
    private String views;

    /** 点赞 */
    @Excel(name = "点赞")
    private String likes;

    /** 收藏 */
    @Excel(name = "收藏")
    private String favorites;

    /** 评论 */
    @Excel(name = "评论")
    private String comments;

    /** 分享 */
    @Excel(name = "分享")
    private String shares;

    /** 私信 */
    @Excel(name = "私信")
    private String dms;

    /** 新增关注 */
    @Excel(name = "新增关注")
    private String follows;

    public String getPostUrl()
    {
        return postUrl;
    }

    public void setPostUrl(String postUrl)
    {
        this.postUrl = postUrl;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getViews()
    {
        return views;
    }

    public void setViews(String views)
    {
        this.views = views;
    }

    public String getLikes()
    {
        return likes;
    }

    public void setLikes(String likes)
    {
        this.likes = likes;
    }

    public String getFavorites()
    {
        return favorites;
    }

    public void setFavorites(String favorites)
    {
        this.favorites = favorites;
    }

    public String getComments()
    {
        return comments;
    }

    public void setComments(String comments)
    {
        this.comments = comments;
    }

    public String getShares()
    {
        return shares;
    }

    public void setShares(String shares)
    {
        this.shares = shares;
    }

    public String getDms()
    {
        return dms;
    }

    public void setDms(String dms)
    {
        this.dms = dms;
    }

    public String getFollows()
    {
        return follows;
    }

    public void setFollows(String follows)
    {
        this.follows = follows;
    }
}

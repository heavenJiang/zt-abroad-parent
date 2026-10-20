package com.ygb.abroad.domain.vo;

import java.io.Serializable;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 归因漏斗行（M9-03：按访客首次进入的渠道码与内容编号归因，按渠道 / 账号 / 内容 / 剧本分组）
 * 
 * @author ygb
 */
public class AbrAttributionRow implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 分组键（渠道码 / 账号ID / 内容编号 / 剧本编码；没有时为空串） */
    private String groupKey;

    /** 分组名称（账号名称、内容标题、剧本名称；渠道码由前端按字典显示） */
    private String groupLabel;

    /** 新访客（期间首次进入的主访客） */
    private Long visitors;

    /** 用过工具（快测、查询工具或提交了完整测评） */
    private Long toolUsers;

    /** 加入过选校单 */
    private Long planUsers;

    /** 登录 */
    private Long loginUsers;

    /** 出现转化时刻 */
    private Long momentUsers;

    /** 预约（未取消） */
    private Long bookedUsers;

    /** 签约 */
    private Long signedUsers;

    /** 期间发布的内容数 */
    private Long published;

    /** 阅读或播放（发布记录回填的效果数据） */
    private Long views;

    /** 互动（点赞 + 收藏 + 评论 + 分享） */
    private Long interactions;

    /** 私信 */
    private Long dms;

    /** 新增关注 */
    private Long follows;

    public String getGroupKey()
    {
        return groupKey;
    }

    public void setGroupKey(String groupKey)
    {
        this.groupKey = groupKey;
    }

    public String getGroupLabel()
    {
        return groupLabel;
    }

    public void setGroupLabel(String groupLabel)
    {
        this.groupLabel = groupLabel;
    }

    public Long getVisitors()
    {
        return visitors;
    }

    public void setVisitors(Long visitors)
    {
        this.visitors = visitors;
    }

    public Long getToolUsers()
    {
        return toolUsers;
    }

    public void setToolUsers(Long toolUsers)
    {
        this.toolUsers = toolUsers;
    }

    public Long getPlanUsers()
    {
        return planUsers;
    }

    public void setPlanUsers(Long planUsers)
    {
        this.planUsers = planUsers;
    }

    public Long getLoginUsers()
    {
        return loginUsers;
    }

    public void setLoginUsers(Long loginUsers)
    {
        this.loginUsers = loginUsers;
    }

    public Long getMomentUsers()
    {
        return momentUsers;
    }

    public void setMomentUsers(Long momentUsers)
    {
        this.momentUsers = momentUsers;
    }

    public Long getBookedUsers()
    {
        return bookedUsers;
    }

    public void setBookedUsers(Long bookedUsers)
    {
        this.bookedUsers = bookedUsers;
    }

    public Long getSignedUsers()
    {
        return signedUsers;
    }

    public void setSignedUsers(Long signedUsers)
    {
        this.signedUsers = signedUsers;
    }

    public Long getPublished()
    {
        return published;
    }

    public void setPublished(Long published)
    {
        this.published = published;
    }

    public Long getViews()
    {
        return views;
    }

    public void setViews(Long views)
    {
        this.views = views;
    }

    public Long getInteractions()
    {
        return interactions;
    }

    public void setInteractions(Long interactions)
    {
        this.interactions = interactions;
    }

    public Long getDms()
    {
        return dms;
    }

    public void setDms(Long dms)
    {
        this.dms = dms;
    }

    public Long getFollows()
    {
        return follows;
    }

    public void setFollows(Long follows)
    {
        this.follows = follows;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("groupKey", getGroupKey())
            .append("groupLabel", getGroupLabel())
            .append("visitors", getVisitors())
            .append("toolUsers", getToolUsers())
            .append("planUsers", getPlanUsers())
            .append("loginUsers", getLoginUsers())
            .append("momentUsers", getMomentUsers())
            .append("bookedUsers", getBookedUsers())
            .append("signedUsers", getSignedUsers())
            .append("published", getPublished())
            .append("views", getViews())
            .append("interactions", getInteractions())
            .append("dms", getDms())
            .append("follows", getFollows())
            .toString();
    }
}

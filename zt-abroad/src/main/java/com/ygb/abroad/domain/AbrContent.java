package com.ygb.abroad.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 内容表 abr_content（数组、对象类字段为 JSON 文本）
 * 
 * @author ygb
 */
public class AbrContent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：草稿 */
    public static final String STATUS_DRAFT = "draft";

    /** 状态：待人工确认 */
    public static final String STATUS_PENDING_CONFIRM = "pending_confirm";

    /** 状态：可发布 */
    public static final String STATUS_READY = "ready";

    /** 状态：已发布 */
    public static final String STATUS_PUBLISHED = "published";

    /** 状态：已归档 */
    public static final String STATUS_ARCHIVED = "archived";

    /** 闸门：未检查 */
    public static final String GATE_PENDING = "pending";

    /** 闸门：通过 */
    public static final String GATE_PASSED = "passed";

    /** 闸门：人工审核通过 */
    public static final String GATE_MANUAL_PASSED = "manual_passed";

    /** 闸门：拦截 */
    public static final String GATE_BLOCKED = "blocked";

    /** 内容ID */
    private Long contentId;

    /** 内容编号 */
    private String contentCode;

    /** 私信口令 */
    @Size(max = 16, message = "私信口令最多 16 个字符")
    private String codeword;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多 100 个字符")
    private String title;

    /** 内容类型 */
    @NotBlank(message = "内容类型不能为空")
    private String contentKind;

    /** 发布账号ID */
    @NotNull(message = "发布账号不能为空")
    private Long accountId;

    /** 发布账号编码（查询带出） */
    private String accountCode;

    /** 发布账号名称（查询带出） */
    private String accountName;

    /** 平台（查询带出） */
    private String platform;

    /** 选题 */
    @Size(max = 300, message = "选题最多 300 个字符")
    private String topic;

    /** 选题引用 */
    @Size(max = 64, message = "选题引用最多 64 个字符")
    private String topicRef;

    /** 所属剧本编码 */
    @Size(max = 64, message = "剧本编码最多 64 个字符")
    private String playbookCode;

    /** 学段 */
    private String degree;

    /** 涉及国家 / 地区（JSON 数组） */
    private String countries;

    /** 正文 */
    @Size(max = 20000, message = "正文最多 20000 个字符")
    private String body;

    /** 话题标签（JSON 数组） */
    private String hashtags;

    /** 图卡（JSON 数组） */
    private String cards;

    /** 文末接的工具 */
    private String hookTool;

    /** 工具链接 */
    private String toolLink;

    /** 引用依据（JSON 数组） */
    private String citations;

    /** 是否 AI 生成（Y / N） */
    private String aiGenerated;

    /** 生成的 Agent */
    private String agentCode;

    /** AI 调用ID */
    private Long aiCallId;

    /** 是否须人工确认（Y / N） */
    private String requiresConfirm;

    /** 是否被抽检（Y / N） */
    private String sampled;

    /** 生成稿件的模型输入（JSON 文本） */
    private String aiInput;

    public String getAiInput()
    {
        return aiInput;
    }

    public void setAiInput(String aiInput)
    {
        this.aiInput = aiInput;
    }

    public String getSampled()
    {
        return sampled;
    }

    public void setSampled(String sampled)
    {
        this.sampled = sampled;
    }

    /** 合规闸门结果 */
    private String gateStatus;

    /** 闸门明细（JSON） */
    private String gateResult;

    /** 闸门检查人 */
    private String gateBy;

    /** 闸门检查时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date gateTime;

    /** 人工确认人 */
    private String confirmBy;

    /** 人工确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date confirmTime;

    /** 状态 */
    private String status;

    public void setContentId(Long contentId)
    {
        this.contentId = contentId;
    }

    public Long getContentId()
    {
        return contentId;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setCodeword(String codeword)
    {
        this.codeword = codeword;
    }

    public String getCodeword()
    {
        return codeword;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

    public void setContentKind(String contentKind)
    {
        this.contentKind = contentKind;
    }

    public String getContentKind()
    {
        return contentKind;
    }

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public void setAccountCode(String accountCode)
    {
        this.accountCode = accountCode;
    }

    public String getAccountCode()
    {
        return accountCode;
    }

    public void setAccountName(String accountName)
    {
        this.accountName = accountName;
    }

    public String getAccountName()
    {
        return accountName;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setTopic(String topic)
    {
        this.topic = topic;
    }

    public String getTopic()
    {
        return topic;
    }

    public void setTopicRef(String topicRef)
    {
        this.topicRef = topicRef;
    }

    public String getTopicRef()
    {
        return topicRef;
    }

    public void setPlaybookCode(String playbookCode)
    {
        this.playbookCode = playbookCode;
    }

    public String getPlaybookCode()
    {
        return playbookCode;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    public String getDegree()
    {
        return degree;
    }

    public void setCountries(String countries)
    {
        this.countries = countries;
    }

    public String getCountries()
    {
        return countries;
    }

    public void setBody(String body)
    {
        this.body = body;
    }

    public String getBody()
    {
        return body;
    }

    public void setHashtags(String hashtags)
    {
        this.hashtags = hashtags;
    }

    public String getHashtags()
    {
        return hashtags;
    }

    public void setCards(String cards)
    {
        this.cards = cards;
    }

    public String getCards()
    {
        return cards;
    }

    public void setHookTool(String hookTool)
    {
        this.hookTool = hookTool;
    }

    public String getHookTool()
    {
        return hookTool;
    }

    public void setToolLink(String toolLink)
    {
        this.toolLink = toolLink;
    }

    public String getToolLink()
    {
        return toolLink;
    }

    public void setCitations(String citations)
    {
        this.citations = citations;
    }

    public String getCitations()
    {
        return citations;
    }

    public void setAiGenerated(String aiGenerated)
    {
        this.aiGenerated = aiGenerated;
    }

    public String getAiGenerated()
    {
        return aiGenerated;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAiCallId(Long aiCallId)
    {
        this.aiCallId = aiCallId;
    }

    public Long getAiCallId()
    {
        return aiCallId;
    }

    public void setRequiresConfirm(String requiresConfirm)
    {
        this.requiresConfirm = requiresConfirm;
    }

    public String getRequiresConfirm()
    {
        return requiresConfirm;
    }

    public void setGateStatus(String gateStatus)
    {
        this.gateStatus = gateStatus;
    }

    public String getGateStatus()
    {
        return gateStatus;
    }

    public void setGateResult(String gateResult)
    {
        this.gateResult = gateResult;
    }

    public String getGateResult()
    {
        return gateResult;
    }

    public void setGateBy(String gateBy)
    {
        this.gateBy = gateBy;
    }

    public String getGateBy()
    {
        return gateBy;
    }

    public void setGateTime(Date gateTime)
    {
        this.gateTime = gateTime;
    }

    public Date getGateTime()
    {
        return gateTime;
    }

    public void setConfirmBy(String confirmBy)
    {
        this.confirmBy = confirmBy;
    }

    public String getConfirmBy()
    {
        return confirmBy;
    }

    public void setConfirmTime(Date confirmTime)
    {
        this.confirmTime = confirmTime;
    }

    public Date getConfirmTime()
    {
        return confirmTime;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("contentId", getContentId())
            .append("contentCode", getContentCode())
            .append("codeword", getCodeword())
            .append("title", getTitle())
            .append("contentKind", getContentKind())
            .append("accountId", getAccountId())
            .append("accountCode", getAccountCode())
            .append("accountName", getAccountName())
            .append("platform", getPlatform())
            .append("topic", getTopic())
            .append("topicRef", getTopicRef())
            .append("playbookCode", getPlaybookCode())
            .append("degree", getDegree())
            .append("countries", getCountries())
            .append("body", getBody())
            .append("hashtags", getHashtags())
            .append("cards", getCards())
            .append("hookTool", getHookTool())
            .append("toolLink", getToolLink())
            .append("citations", getCitations())
            .append("aiGenerated", getAiGenerated())
            .append("agentCode", getAgentCode())
            .append("aiCallId", getAiCallId())
            .append("requiresConfirm", getRequiresConfirm())
            .append("sampled", getSampled())
            .append("gateStatus", getGateStatus())
            .append("gateResult", getGateResult())
            .append("gateBy", getGateBy())
            .append("gateTime", getGateTime())
            .append("confirmBy", getConfirmBy())
            .append("confirmTime", getConfirmTime())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

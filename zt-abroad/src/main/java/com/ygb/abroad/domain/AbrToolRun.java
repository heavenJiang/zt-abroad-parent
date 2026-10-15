package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 入口工具运行记录表 abr_tool_run
 * 
 * @author ygb
 */
public class AbrToolRun extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 运行ID */
    private Long toolRunId;

    /** 工具（quick 30 秒快测） */
    private String tool;

    /** 访客ID */
    private Long visitorId;

    /** 学段 */
    private String degree;

    /** 题目与换算表所用的问卷版本ID */
    private Long questionnaireId;

    /** 输入（JSON：题目 id → 答案） */
    private String input;

    /** 计算留痕（JSON，含内部字段，只给管理端） */
    private String result;

    /** 所用引擎配置版本（JSON） */
    private String engineVersions;

    /** 渠道码 */
    private String channelCode;

    /** 内容编号 */
    private String contentCode;

    /** 端（h5 / mp_weixin） */
    private String clientPlatform;

    /** 同意的协议编码 */
    private String policyCode;

    /** 同意的协议版本 */
    private String policyVersion;

    public void setToolRunId(Long toolRunId)
    {
        this.toolRunId = toolRunId;
    }

    public Long getToolRunId()
    {
        return toolRunId;
    }

    public void setTool(String tool)
    {
        this.tool = tool;
    }

    public String getTool()
    {
        return tool;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    public String getDegree()
    {
        return degree;
    }

    public void setQuestionnaireId(Long questionnaireId)
    {
        this.questionnaireId = questionnaireId;
    }

    public Long getQuestionnaireId()
    {
        return questionnaireId;
    }

    public void setInput(String input)
    {
        this.input = input;
    }

    public String getInput()
    {
        return input;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getResult()
    {
        return result;
    }

    public void setEngineVersions(String engineVersions)
    {
        this.engineVersions = engineVersions;
    }

    public String getEngineVersions()
    {
        return engineVersions;
    }

    public void setChannelCode(String channelCode)
    {
        this.channelCode = channelCode;
    }

    public String getChannelCode()
    {
        return channelCode;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setClientPlatform(String clientPlatform)
    {
        this.clientPlatform = clientPlatform;
    }

    public String getClientPlatform()
    {
        return clientPlatform;
    }

    public void setPolicyCode(String policyCode)
    {
        this.policyCode = policyCode;
    }

    public String getPolicyCode()
    {
        return policyCode;
    }

    public void setPolicyVersion(String policyVersion)
    {
        this.policyVersion = policyVersion;
    }

    public String getPolicyVersion()
    {
        return policyVersion;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("toolRunId", getToolRunId())
            .append("tool", getTool())
            .append("visitorId", getVisitorId())
            .append("degree", getDegree())
            .append("questionnaireId", getQuestionnaireId())
            .append("input", getInput())
            .append("result", getResult())
            .append("engineVersions", getEngineVersions())
            .append("channelCode", getChannelCode())
            .append("contentCode", getContentCode())
            .append("clientPlatform", getClientPlatform())
            .append("policyCode", getPolicyCode())
            .append("policyVersion", getPolicyVersion())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}

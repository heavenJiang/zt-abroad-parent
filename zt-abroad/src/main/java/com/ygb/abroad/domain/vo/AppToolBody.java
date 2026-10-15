package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * C 端：查询类入口工具请求体（本校认可度、雅思够不够、现在还能申）
 * 
 * @author ygb
 */
public class AppToolBody
{
    /** 学段（不传按硕士） */
    private String degree;

    /** 答案（题目 id → 答案，格式与问卷一致） */
    private JsonNode answers;

    /** 学生看到并同意的隐私政策版本（采集个人信息的工具必填） */
    private String policyVersion;

    /** 渠道码 */
    private String channelCode;

    /** 内容编号 */
    private String contentCode;

    /** 端（h5 / mp_weixin） */
    private String clientPlatform;

    @Pattern(regexp = "^(secondary|bachelor|master|phd)$", message = "学段不正确")
    public String getDegree()
    {
        return degree;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    @NotNull(message = "请填写查询信息")
    public JsonNode getAnswers()
    {
        return answers;
    }

    public void setAnswers(JsonNode answers)
    {
        this.answers = answers;
    }

    @Size(max = 32, message = "隐私政策版本不能超过32个字符")
    public String getPolicyVersion()
    {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion)
    {
        this.policyVersion = policyVersion;
    }

    public String getChannelCode()
    {
        return channelCode;
    }

    public void setChannelCode(String channelCode)
    {
        this.channelCode = channelCode;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getClientPlatform()
    {
        return clientPlatform;
    }

    public void setClientPlatform(String clientPlatform)
    {
        this.clientPlatform = clientPlatform;
    }
}

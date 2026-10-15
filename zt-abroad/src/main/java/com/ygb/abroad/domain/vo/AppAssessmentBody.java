package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * C 端：同意隐私说明后创建测评的请求体
 * 
 * @author ygb
 */
public class AppAssessmentBody
{
    /** 前端生成的请求ID（重复提交返回同一测评） */
    private String requestId;

    /** 学生看到并同意的隐私政策版本 */
    private String policyVersion;

    /** 渠道码（不传时用访客首次进入的渠道码） */
    private String channelCode;

    /** 学段（首屏选择：secondary / bachelor / master / phd；不传按硕士） */
    private String degree;

    @Pattern(regexp = "^(secondary|bachelor|master|phd)$", message = "学段不正确")
    public String getDegree()
    {
        return degree;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    /** 内容编号（最近一次进入时链接上的 ct；格式不对时忽略） */
    private String contentCode;

    public String getContentCode()
    {
        return contentCode;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    /** 答题端（h5 / mp_weixin） */
    private String clientPlatform;

    @NotBlank(message = "缺少请求ID")
    @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "请求ID格式不正确")
    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    @NotBlank(message = "请先同意隐私说明")
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

    public String getClientPlatform()
    {
        return clientPlatform;
    }

    public void setClientPlatform(String clientPlatform)
    {
        this.clientPlatform = clientPlatform;
    }
}

package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 消息模板（业务编码 × 渠道） abr_message_template
 *
 * @author ygb
 */
public class AbrMessageTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long templateId;

    /** 业务编码 */
    private String templateCode;

    /** 渠道 */
    private String channel;

    /** 名称 */
    private String templateName;

    /** 类别（marketing / service） */
    private String category;

    /** 平台模板 ID */
    private String platformTemplateId;

    /** 字段映射（JSON 对象文本） */
    private String fieldMap;

    /** 消息文字 */
    private String content;

    /** 点击跳转 */
    private String link;

    /** 状态（0 启用 1 停用） */
    private String status;

    public Long getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(Long templateId)
    {
        this.templateId = templateId;
    }

    public String getTemplateCode()
    {
        return templateCode;
    }

    public void setTemplateCode(String templateCode)
    {
        this.templateCode = templateCode;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getTemplateName()
    {
        return templateName;
    }

    public void setTemplateName(String templateName)
    {
        this.templateName = templateName;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getPlatformTemplateId()
    {
        return platformTemplateId;
    }

    public void setPlatformTemplateId(String platformTemplateId)
    {
        this.platformTemplateId = platformTemplateId;
    }

    public String getFieldMap()
    {
        return fieldMap;
    }

    public void setFieldMap(String fieldMap)
    {
        this.fieldMap = fieldMap;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getLink()
    {
        return link;
    }

    public void setLink(String link)
    {
        this.link = link;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("templateId", getTemplateId())
            .append("templateCode", getTemplateCode())
            .append("channel", getChannel())
            .append("templateName", getTemplateName())
            .append("category", getCategory())
            .append("platformTemplateId", getPlatformTemplateId())
            .append("fieldMap", getFieldMap())
            .append("content", getContent())
            .append("link", getLink())
            .append("status", getStatus())
            .toString();
    }
}

package com.ygb.abroad.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * C 端：当前启用的协议（隐私政策等）
 * 
 * @author ygb
 */
public class AppPolicyVo
{
    /** 协议编码 */
    private String policyCode;

    /** 版本号（创建测评时原样回传，表示同意的是这个版本） */
    private String version;

    /** 标题 */
    private String title;

    /** 全文（Markdown，按纯文本段落展示） */
    private String content;

    /** 是否未经律师审阅的草案（C 端显示“草案”标识） */
    private Boolean draft;

    /** 生效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    public String getPolicyCode()
    {
        return policyCode;
    }

    public void setPolicyCode(String policyCode)
    {
        this.policyCode = policyCode;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Boolean getDraft()
    {
        return draft;
    }

    public void setDraft(Boolean draft)
    {
        this.draft = draft;
    }

    public Date getEffectiveDate()
    {
        return effectiveDate;
    }

    public void setEffectiveDate(Date effectiveDate)
    {
        this.effectiveDate = effectiveDate;
    }
}

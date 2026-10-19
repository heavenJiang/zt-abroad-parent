package com.ygb.abroad.domain.vo;

import java.util.Date;
import com.ygb.common.annotation.Excel;

/**
 * 线索导入的一行（讲座报名、合作方、转介绍、平台私信等表格）
 *
 * @author ygb
 */
public class AbrLeadImportRow
{
    /** 称呼 */
    @Excel(name = "称呼")
    private String displayName;

    /** 手机号 */
    @Excel(name = "手机号", cellType = Excel.ColumnType.STRING)
    private String phone;

    /** 来源编码（lecture / partner / referral / dm / manual） */
    @Excel(name = "来源", prompt = "lecture 讲座报名 / partner 合作方 / referral 转介绍 / dm 平台私信 / manual 员工录入")
    private String source;

    /** 渠道码 */
    @Excel(name = "渠道码")
    private String channelCode;

    /** 内容编号 */
    @Excel(name = "内容编号")
    private String contentCode;

    /** 发生时间 */
    @Excel(name = "时间", dateFormat = "yyyy-MM-dd HH:mm")
    private Date occurredTime;

    /** 说明（不写手机号等个人信息） */
    @Excel(name = "说明")
    private String summary;

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
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

    public Date getOccurredTime()
    {
        return occurredTime;
    }

    public void setOccurredTime(Date occurredTime)
    {
        this.occurredTime = occurredTime;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }
}

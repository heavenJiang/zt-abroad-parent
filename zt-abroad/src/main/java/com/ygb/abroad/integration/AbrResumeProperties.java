package com.ygb.abroad.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 简历 / 成绩单上传与解析配置（application.yml 的 abroad.resume）
 *
 * @author ygb
 */
@Component
@ConfigurationProperties(prefix = "abroad.resume")
public class AbrResumeProperties
{
    /** 原件存放目录：必须是私有目录，不能在 zt.profile 下（/profile/** 是公开静态资源） */
    private String dir;

    /** 单份文件大小上限（MB） */
    private int maxSizeMb = 10;

    /** 每份测评累计最多上传次数（含重新上传、删除的；控制解析成本） */
    private int maxUploads = 6;

    /** 原件保存天数（到期由定时任务删除） */
    private int retentionDays = 30;

    /** PDF 最多读取的页数 */
    private int maxPages = 10;

    /** 去除敏感行后交给模型的最大字符数（超过截断） */
    private int maxChars = 12000;

    /** PDF 文字层少于该字数时视为扫描件，改走文字识别 */
    private int minTextChars = 30;

    public String getDir()
    {
        return dir;
    }

    public void setDir(String dir)
    {
        this.dir = dir;
    }

    public int getMaxSizeMb()
    {
        return maxSizeMb;
    }

    public void setMaxSizeMb(int maxSizeMb)
    {
        this.maxSizeMb = maxSizeMb;
    }

    public int getMaxUploads()
    {
        return maxUploads;
    }

    public void setMaxUploads(int maxUploads)
    {
        this.maxUploads = maxUploads;
    }

    public int getRetentionDays()
    {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays)
    {
        this.retentionDays = retentionDays;
    }

    public int getMaxPages()
    {
        return maxPages;
    }

    public void setMaxPages(int maxPages)
    {
        this.maxPages = maxPages;
    }

    public int getMaxChars()
    {
        return maxChars;
    }

    public void setMaxChars(int maxChars)
    {
        this.maxChars = maxChars;
    }

    public int getMinTextChars()
    {
        return minTextChars;
    }

    public void setMinTextChars(int minTextChars)
    {
        this.minTextChars = minTextChars;
    }
}

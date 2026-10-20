package com.ygb.abroad.integration;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 抓取到的网页（已转为纯文本）
 *
 * @author ygb
 */
public class AbrWebPage
{
    /** 请求的链接 */
    private String requestUrl;

    /** 跟随重定向后的最终链接（引句所在页面） */
    private String finalUrl;

    /** 内容类型（不含参数，如 text/html） */
    private String contentType;

    /** 解码使用的字符集 */
    private String charset;

    /** 页面标题 */
    private String title;

    /** 纯文本正文（已截断到最大字符数） */
    private String text;

    /** 原始 HTML（纯文本页面为 null；不序列化给前端） */
    @JsonIgnore
    private String html;

    /** 正文是否被截断 */
    private boolean truncated;

    /** 响应体字节数 */
    private int bytes;

    /** 重定向次数 */
    private int redirects;

    /** 抓取完成时间 */
    private Date fetchedAt;

    /** 抓取耗时（毫秒） */
    private long elapsedMs;

    public String getRequestUrl()
    {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl)
    {
        this.requestUrl = requestUrl;
    }

    public String getFinalUrl()
    {
        return finalUrl;
    }

    public void setFinalUrl(String finalUrl)
    {
        this.finalUrl = finalUrl;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public String getCharset()
    {
        return charset;
    }

    public void setCharset(String charset)
    {
        this.charset = charset;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getHtml()
    {
        return html;
    }

    public void setHtml(String html)
    {
        this.html = html;
    }

    public String getText()
    {
        return text;
    }

    public void setText(String text)
    {
        this.text = text;
    }

    public boolean isTruncated()
    {
        return truncated;
    }

    public void setTruncated(boolean truncated)
    {
        this.truncated = truncated;
    }

    public int getBytes()
    {
        return bytes;
    }

    public void setBytes(int bytes)
    {
        this.bytes = bytes;
    }

    public int getRedirects()
    {
        return redirects;
    }

    public void setRedirects(int redirects)
    {
        this.redirects = redirects;
    }

    public Date getFetchedAt()
    {
        return fetchedAt;
    }

    public void setFetchedAt(Date fetchedAt)
    {
        this.fetchedAt = fetchedAt;
    }

    public long getElapsedMs()
    {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs)
    {
        this.elapsedMs = elapsedMs;
    }
}

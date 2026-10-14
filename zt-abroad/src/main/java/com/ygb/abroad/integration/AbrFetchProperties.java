package com.ygb.abroad.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 官网页面抓取配置（application.yml 的 abroad.ai.fetch）
 *
 * @author ygb
 */
@Component
@ConfigurationProperties(prefix = "abroad.ai.fetch")
public class AbrFetchProperties
{
    /** 默认正文最大字符数（超过截断后再交给模型） */
    public static final int DEFAULT_MAX_CHARS = 30000;

    /** 默认响应体最大字节数（2 MB） */
    public static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;

    /**
     * 允许抓取内网、本机地址（默认 false）。只有测试环境为了用本机 HTTP 服务提供样例页面才可以设为 true，
     * 生产环境必须为 false，否则预填接口可被用来探测内网（SSRF）
     */
    private boolean allowPrivateHosts = false;

    /** 正文最大字符数 */
    private int maxChars = DEFAULT_MAX_CHARS;

    /** 响应体最大字节数 */
    private int maxBytes = DEFAULT_MAX_BYTES;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 总超时（毫秒，含重定向与读取正文） */
    private int totalTimeoutMs = 15000;

    /** 最多跟随的重定向次数 */
    private int maxRedirects = 3;

    public boolean isAllowPrivateHosts()
    {
        return allowPrivateHosts;
    }

    public void setAllowPrivateHosts(boolean allowPrivateHosts)
    {
        this.allowPrivateHosts = allowPrivateHosts;
    }

    public int getMaxChars()
    {
        return maxChars;
    }

    public void setMaxChars(int maxChars)
    {
        this.maxChars = maxChars;
    }

    public int getMaxBytes()
    {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes)
    {
        this.maxBytes = maxBytes;
    }

    public int getConnectTimeoutMs()
    {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs)
    {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getTotalTimeoutMs()
    {
        return totalTimeoutMs;
    }

    public void setTotalTimeoutMs(int totalTimeoutMs)
    {
        this.totalTimeoutMs = totalTimeoutMs;
    }

    public int getMaxRedirects()
    {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects)
    {
        this.maxRedirects = maxRedirects;
    }
}

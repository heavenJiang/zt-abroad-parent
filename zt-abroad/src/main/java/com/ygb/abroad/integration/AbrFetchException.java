package com.ygb.abroad.integration;

/**
 * 页面抓取失败（消息为给录入者看的中文原因）
 *
 * @author ygb
 */
public class AbrFetchException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** 链接格式不正确 */
    public static final String INVALID_URL = "invalid_url";

    /** 指向内网、本机等不允许的地址 */
    public static final String BLOCKED = "blocked";

    /** 页面打不开（域名解析失败、连接失败、4xx/5xx、重定向过多） */
    public static final String OPEN_FAILED = "open_failed";

    /** 需要登录或被拒绝（401/403/407/429） */
    public static final String DENIED = "denied";

    /** 不是网页（PDF、图片等） */
    public static final String NOT_HTML = "not_html";

    /** 超时 */
    public static final String TIMEOUT = "timeout";

    /** 页面太大 */
    public static final String TOO_LARGE = "too_large";

    /** 原因编码 */
    private final String reason;

    public AbrFetchException(String reason, String message)
    {
        super(message);
        this.reason = reason;
    }

    public String getReason()
    {
        return reason;
    }
}

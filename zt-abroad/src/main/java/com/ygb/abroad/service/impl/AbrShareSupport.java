package com.ygb.abroad.service.impl;

import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import com.ygb.abroad.growth.card.Card;
import com.ygb.abroad.growth.card.CardRenderer;
import com.ygb.abroad.mapper.AbrShareMapper;

/**
 * 分享公共部分：分享码、带分享码的 C 端链接、图卡渲染（定位海报、选校单分享卡、家长只读视图共用）
 *
 * @author ygb
 */
@Component
public class AbrShareSupport
{
    /** 分享码字符（去掉易混的 0 O 1 l I） */
    private static final String SHARE_CODE_CHARS = "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

    /** 分享码长度 */
    private static final int SHARE_CODE_LENGTH = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private AbrShareMapper shareMapper;

    /** C 端地址（分享二维码、家长只读视图链接） */
    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    /** 品牌名（图卡顶部） */
    @Value("${abroad.app.brand-name:留学工作室}")
    private String brandName;

    /** 图卡字体族 */
    @Value("${abroad.card.font-family:}")
    private String cardFontFamily;

    private volatile CardRenderer renderer;

    /**
     * 取（或建立）分享码：同一访客同一对象分享码不变，撤销后再分享换新码
     *
     * @param kind 分享对象类型
     * @param visitorId 访客ID
     * @param refId 分享对象ID
     * @param options 显示项 JSON
     * @return 分享码
     */
    public String shareCode(String kind, Long visitorId, Long refId, String options)
    {
        try
        {
            return shareMapper.upsertAbrShare(kind, visitorId, refId, newShareCode(), options);
        }
        catch (DuplicateKeyException e)
        {
            // 随机分享码与已有记录相同（极少见）：换一个再试
            return shareMapper.upsertAbrShare(kind, visitorId, refId, newShareCode(), options);
        }
    }

    /**
     * C 端地址加路径与参数
     *
     * @param pathAndQuery 路径与参数（如 pages/share/plan?code=x；为空时为首页）
     * @return 完整链接
     */
    public String appLink(String pathAndQuery)
    {
        String base = appUrl.endsWith("/") ? appUrl : appUrl + "/";
        return base + (pathAndQuery == null ? "" : pathAndQuery.replaceFirst("^/", ""));
    }

    /**
     * 分享二维码链接：C 端首页，带渠道 share 与内容编号（分享码）
     *
     * @param shareCode 分享码
     * @return 链接
     */
    public String landingLink(String shareCode)
    {
        return appUrl + (appUrl.contains("?") ? "&" : "?") + "ch=share&ct=" + shareCode;
    }

    /**
     * 品牌名
     *
     * @return 品牌名
     */
    public String brandName()
    {
        return brandName;
    }

    /**
     * 渲染图卡为 PNG（首次使用时选字体）
     *
     * @param card 图卡
     * @return PNG 字节
     */
    public byte[] png(Card card)
    {
        CardRenderer r = renderer;
        if (r == null)
        {
            r = new CardRenderer(cardFontFamily);
            renderer = r;
        }
        return r.png(card);
    }

    /**
     * 新分享码：10 位随机字母数字
     */
    static String newShareCode()
    {
        StringBuilder sb = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++)
        {
            sb.append(SHARE_CODE_CHARS.charAt(RANDOM.nextInt(SHARE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}

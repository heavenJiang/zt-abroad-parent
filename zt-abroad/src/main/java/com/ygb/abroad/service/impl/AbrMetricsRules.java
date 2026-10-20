package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.vo.AbrMetricsImportRow;
import com.ygb.common.exception.ServiceException;

/**
 * 效果数据规则（纯计算）：允许的指标、规范化、导入行转指标
 *
 * @author ygb
 */
final class AbrMetricsRules
{
    /** 指标（顺序即展示顺序） */
    static final List<String> KEYS = List.of("views", "likes", "favorites", "comments", "shares", "dms", "follows");

    /** 指标名称（提示用） */
    static final List<String> LABELS = List.of("阅读或播放", "点赞", "收藏", "评论", "分享", "私信", "新增关注");

    /** 单个指标上限（防止填错单位） */
    static final long MAX = 1_000_000_000L;

    private AbrMetricsRules()
    {
    }

    /**
     * 规范化：只保留约定的指标，值为 0–10 亿的整数；没有任何指标时报错
     *
     * @param input 指标（键为 views、likes 等）
     * @return 规范后的指标
     */
    static Map<String, Long> normalize(Map<String, ?> input)
    {
        Map<String, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < KEYS.size(); i++)
        {
            String k = KEYS.get(i);
            String label = LABELS.get(i);
            Object v = input == null ? null : input.get(k);
            if (v == null || "".equals(v))
            {
                continue;
            }
            long n;
            if (v instanceof Number num)
            {
                if (num.doubleValue() != Math.floor(num.doubleValue()))
                {
                    throw new ServiceException("“" + label + "”须为整数");
                }
                n = num.longValue();
            }
            else
            {
                try
                {
                    n = Long.parseLong(String.valueOf(v).trim().replace(",", ""));
                }
                catch (NumberFormatException e)
                {
                    throw new ServiceException("“" + label + "”须为整数");
                }
            }
            if (n < 0 || n > MAX)
            {
                throw new ServiceException("“" + label + "”须在 0–10 亿之间");
            }
            out.put(k, n);
        }
        if (out.isEmpty())
        {
            throw new ServiceException("没有任何效果数据");
        }
        return out;
    }

    /**
     * 导入行转指标
     *
     * @param row 导入行
     * @return 指标（未填的不含）
     */
    static Map<String, Object> fromRow(AbrMetricsImportRow row)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("views", row.getViews());
        m.put("likes", row.getLikes());
        m.put("favorites", row.getFavorites());
        m.put("comments", row.getComments());
        m.put("shares", row.getShares());
        m.put("dms", row.getDms());
        m.put("follows", row.getFollows());
        return m;
    }
}

package com.ygb.abroad.growth;

import java.util.Date;

/**
 * 触达前的判断结果（频控、免打扰、退订、未成年人、暂停）
 *
 * @param kind 结果
 * @param reason 原因（拦下、推迟时）
 * @param deferUntil 推迟到（免打扰结束）
 * @author ygb
 */
public record TouchDecision(Kind kind, String reason, Date deferUntil)
{
    /**
     * 结果类型
     */
    public enum Kind
    {
        /** 可以发 */
        ALLOW,
        /** 免打扰中，推迟到 deferUntil */
        DEFER,
        /** 不能发（已记为被拦下的触达） */
        DENY
    }

    public static TouchDecision allow()
    {
        return new TouchDecision(Kind.ALLOW, null, null);
    }

    public static TouchDecision defer(Date until, String reason)
    {
        return new TouchDecision(Kind.DEFER, reason, until);
    }

    public static TouchDecision deny(String reason)
    {
        return new TouchDecision(Kind.DENY, reason, null);
    }

    /**
     * 是否可以发
     *
     * @return 是否可以发
     */
    public boolean allowed()
    {
        return kind == Kind.ALLOW;
    }
}

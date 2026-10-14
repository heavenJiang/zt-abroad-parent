package com.ygb.common.enums;

/**
 * 限流类型
 *
 * @author ruoyi
 */

public enum LimitType
{
    /**
     * 默认策略全局限流
     */
    DEFAULT,

    /**
     * 根据请求者IP进行限流
     */
    IP,

    /**
     * 根据登录用户进行限流（未登录时按IP）
     */
    USER
}

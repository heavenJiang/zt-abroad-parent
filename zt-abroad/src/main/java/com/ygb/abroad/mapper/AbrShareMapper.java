package com.ygb.abroad.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 分享记录 数据层
 * 
 * @author ygb
 */
public interface AbrShareMapper
{
    /**
     * 新增或更新分享记录（同一访客同一对象只有一条，分享码不变），返回分享码
     * 
     * @param kind 分享对象类型
     * @param visitorId 访客ID
     * @param refId 分享对象ID
     * @param shareCode 新记录使用的分享码（已有记录时忽略）
     * @param options 显示项（JSON）
     * @return 分享码
     */
    public String upsertAbrShare(@Param("kind") String kind, @Param("visitorId") Long visitorId, @Param("refId") Long refId,
            @Param("shareCode") String shareCode, @Param("options") String options);
}

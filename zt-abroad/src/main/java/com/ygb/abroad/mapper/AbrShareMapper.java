package com.ygb.abroad.mapper;

import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrShare;

/**
 * 分享记录 数据层
 * 
 * @author ygb
 */
public interface AbrShareMapper
{
    /**
     * 新增或更新分享记录（同一访客同一对象只有一条，分享码不变；已撤销的换用新分享码并恢复），返回分享码
     * 
     * @param kind 分享对象类型
     * @param visitorId 访客ID
     * @param refId 分享对象ID
     * @param shareCode 新分享码（已有且未撤销时忽略）
     * @param options 显示项（JSON）
     * @return 分享码
     */
    public String upsertAbrShare(@Param("kind") String kind, @Param("visitorId") Long visitorId, @Param("refId") Long refId,
            @Param("shareCode") String shareCode, @Param("options") String options);

    /**
     * 按分享码查询
     * 
     * @param shareCode 分享码
     * @return 分享记录，没有返回 null
     */
    public AbrShare selectAbrShareByCode(String shareCode);

    /**
     * 查询某访客某对象的分享记录
     * 
     * @param kind 分享对象类型
     * @param visitorId 访客ID
     * @param refId 分享对象ID
     * @return 分享记录，没有返回 null
     */
    public AbrShare selectAbrShareByRef(@Param("kind") String kind, @Param("visitorId") Long visitorId, @Param("refId") Long refId);

    /**
     * 撤销分享
     * 
     * @param kind 分享对象类型
     * @param visitorId 访客ID
     * @param refId 分享对象ID
     * @return 结果
     */
    public int revokeAbrShare(@Param("kind") String kind, @Param("visitorId") Long visitorId, @Param("refId") Long refId);

    /**
     * 记录一次查看
     * 
     * @param shareId 分享ID
     * @return 结果
     */
    public int touchAbrShareView(Long shareId);
}

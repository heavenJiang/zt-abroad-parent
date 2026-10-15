package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 材料完成情况 数据层
 * 
 * @author ygb
 */
public interface AbrPlanMaterialMapper
{
    /**
     * 查询访客已完成的材料键
     * 
     * @param visitorId 访客ID
     * @return 材料键
     */
    public List<String> selectDoneMaterialKeys(Long visitorId);

    /**
     * 设置某项材料的完成状态（没有记录时新增）
     * 
     * @param visitorId 访客ID
     * @param appUserId 学生账号ID（未登录为 null）
     * @param materialKey 材料键
     * @param done 是否完成
     * @return 结果
     */
    public int upsertPlanMaterial(@Param("visitorId") Long visitorId, @Param("appUserId") Long appUserId, @Param("materialKey") String materialKey,
            @Param("done") boolean done);
}

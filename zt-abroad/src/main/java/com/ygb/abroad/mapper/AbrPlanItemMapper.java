package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrPlanItem;

/**
 * 我的选校单 数据层
 * 
 * @author ygb
 */
public interface AbrPlanItemMapper
{
    /**
     * 查询访客的选校单（关联院校、项目名称；已删除或停用的项目不返回）
     * 
     * @param visitorId 访客ID
     * @return 条目（按加入先后）
     */
    public List<AbrPlanItem> selectAbrPlanItemsByVisitor(Long visitorId);

    /**
     * 统计访客的选校单条目数
     * 
     * @param visitorId 访客ID
     * @return 条目数
     */
    public int countAbrPlanItemsByVisitor(Long visitorId);

    /**
     * 加入选校单（已存在时更新来源与档位）
     * 
     * @param item 条目
     * @return 结果
     */
    public int upsertAbrPlanItem(AbrPlanItem item);

    /**
     * 从选校单移除
     * 
     * @param visitorId 访客ID
     * @param programId 项目ID
     * @return 结果
     */
    public int deleteAbrPlanItem(@Param("visitorId") Long visitorId, @Param("programId") Long programId);
}

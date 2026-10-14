package com.ygb.abroad.mapper;

import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrVisitor;

/**
 * 访客 数据层
 * 
 * @author ygb
 */
public interface AbrVisitorMapper
{
    /**
     * 按令牌哈希查询访客
     * 
     * @param tokenHash 令牌 SHA-256
     * @return 访客，不存在返回 null
     */
    public AbrVisitor selectAbrVisitorByTokenHash(String tokenHash);

    /**
     * 按ID查询访客
     * 
     * @param visitorId 访客ID
     * @return 访客
     */
    public AbrVisitor selectAbrVisitorById(Long visitorId);

    /**
     * 新增访客
     * 
     * @param visitor 访客
     * @return 结果
     */
    public int insertAbrVisitor(AbrVisitor visitor);

    /**
     * 刷新最近访问时间（10 分钟内不重复写）
     * 
     * @param visitorId 访客ID
     * @return 结果
     */
    public int touchAbrVisitor(Long visitorId);

    /**
     * 合并渐进档案（新答案覆盖同一题目的旧答案）
     * 
     * @param visitorId 访客ID
     * @param patch 档案增量（JSON 对象）
     * @return 结果
     */
    public int mergeAbrVisitorProfile(@Param("visitorId") Long visitorId, @Param("patch") String patch);

    /**
     * 清空渐进档案（如未满 14 周岁终止测评时）
     * 
     * @param visitorId 访客ID
     * @return 结果
     */
    public int clearAbrVisitorProfile(Long visitorId);
}

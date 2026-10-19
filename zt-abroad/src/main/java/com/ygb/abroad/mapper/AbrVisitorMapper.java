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

    /**
     * 访客关联学生账号（只在还没关联时）
     *
     * @param visitorId 访客ID
     * @param appUserId 学生账号ID
     * @return 影响行数
     */
    public int bindAppUser(@Param("visitorId") Long visitorId, @Param("appUserId") Long appUserId);

    /**
     * 把访客的渐进档案并入主访客（同一题以被合并的访客为准：它是刚登录的这台设备上最近的回答）
     *
     * @param fromId 被合并的访客
     * @param toId 主访客
     * @return 影响行数
     */
    public int mergeProfileInto(@Param("fromId") Long fromId, @Param("toId") Long toId);

    /**
     * 记录合并去向并关联账号
     *
     * @param fromId 被合并的访客
     * @param toId 主访客
     * @param appUserId 学生账号ID
     * @return 影响行数
     */
    public int markMerged(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    /*
     * 合并：把被合并访客名下的测评、档案、材料文件、工具记录、选校单、材料完成情况、分享、事件移到主访客；
     * 与主访客冲突的（同一项目、同一材料、同类分享、去重事件）保留主访客的，选校单与材料删除被合并的、分享撤销被合并的。
     * 参数：fromId 被合并的访客，toId 主访客，appUserId 学生账号ID；返回影响行数。
     */
    public int moveAssessments(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int moveProfiles(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int moveAssessmentFiles(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int moveToolRuns(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int movePlanItems(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int deletePlanItems(@Param("fromId") Long fromId);

    public int mergeMaterialDone(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int moveMaterials(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int deleteMaterials(@Param("fromId") Long fromId);

    public int moveShares(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    public int revokeShares(@Param("fromId") Long fromId);

    public int moveEvents(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("appUserId") Long appUserId);

    /**
     * 主访客名下的选校单、材料、事件补上学生账号ID
     *
     * @param visitorId 主访客
     * @param appUserId 学生账号ID
     * @return 影响行数
     */
    public int tagAppUser(@Param("visitorId") Long visitorId, @Param("appUserId") Long appUserId);
}

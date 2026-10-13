package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrRequirement;

/**
 * 项目申请要求 数据层
 * 
 * @author ygb
 */
public interface AbrRequirementMapper
{
    /**
     * 查询项目的申请要求（按申请季倒序）
     * 
     * @param programId 项目ID
     * @return 申请要求集合
     */
    public List<AbrRequirement> selectAbrRequirementByProgramId(Long programId);

    /**
     * 按ID查询申请要求
     * 
     * @param requirementId 要求ID
     * @return 申请要求
     */
    public AbrRequirement selectAbrRequirementById(Long requirementId);

    /**
     * 校验同一项目下申请季是否唯一
     * 
     * @param requirement 申请要求（programId、season）
     * @return 已存在的记录，不存在返回 null
     */
    public AbrRequirement checkAbrRequirementUnique(AbrRequirement requirement);

    /**
     * 新增申请要求
     * 
     * @param requirement 申请要求
     * @return 影响行数
     */
    public int insertAbrRequirement(AbrRequirement requirement);

    /**
     * 修改申请要求（整行覆盖，允许把字段清空）
     * 
     * @param requirement 申请要求
     * @return 影响行数
     */
    public int updateAbrRequirement(AbrRequirement requirement);

    /**
     * 批量逻辑删除申请要求
     * 
     * @param requirementIds 要求ID数组
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int deleteAbrRequirementByIds(@Param("requirementIds") Long[] requirementIds, @Param("updateBy") String updateBy);

    /**
     * 按项目批量逻辑删除申请要求
     * 
     * @param programIds 项目ID数组
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int deleteAbrRequirementByProgramIds(@Param("programIds") Long[] programIds, @Param("updateBy") String updateBy);
}

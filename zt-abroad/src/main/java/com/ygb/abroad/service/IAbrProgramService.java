package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;

/**
 * 项目与申请要求 服务层
 * 
 * @author ygb
 */
public interface IAbrProgramService
{
    /**
     * 查询项目列表
     * 
     * @param program 查询条件
     * @return 项目集合
     */
    public List<AbrProgram> selectAbrProgramList(AbrProgram program);

    /**
     * 按ID查询项目
     * 
     * @param programId 项目ID
     * @return 项目
     */
    public AbrProgram selectAbrProgramById(Long programId);

    /**
     * 校验同一院校下英文名 + 学位是否唯一
     * 
     * @param program 项目
     * @return true 唯一
     */
    public boolean checkAbrProgramUnique(AbrProgram program);

    /**
     * 新增项目
     * 
     * @param program 项目
     * @return 影响行数
     */
    public int insertAbrProgram(AbrProgram program);

    /**
     * 修改项目
     * 
     * @param program 项目
     * @return 影响行数
     */
    public int updateAbrProgram(AbrProgram program);

    /**
     * 批量删除项目（连同其申请要求）
     * 
     * @param programIds 项目ID数组
     * @param operator 操作人
     * @return 影响行数
     */
    public int deleteAbrProgramByIds(Long[] programIds, String operator);

    /**
     * 查询项目的申请要求（含字段来源）
     * 
     * @param programId 项目ID
     * @return 申请要求集合
     */
    public List<AbrRequirement> selectAbrRequirementByProgramId(Long programId);

    /**
     * 按ID查询申请要求（含字段来源）
     * 
     * @param requirementId 要求ID
     * @return 申请要求
     */
    public AbrRequirement selectAbrRequirementById(Long requirementId);

    /**
     * 校验同一项目下申请季是否唯一
     * 
     * @param requirement 申请要求
     * @return true 唯一
     */
    public boolean checkAbrRequirementUnique(AbrRequirement requirement);

    /**
     * 新增申请要求并保存字段来源；均分、语言、截止日等关键字段有值时必须附来源 URL
     * 
     * @param requirement 申请要求（含 sources）
     * @return 影响行数
     */
    public int insertAbrRequirement(AbrRequirement requirement);

    /**
     * 修改申请要求并保存字段来源；规则同新增
     * 
     * @param requirement 申请要求（含 sources）
     * @return 影响行数
     */
    public int updateAbrRequirement(AbrRequirement requirement);

    /**
     * 批量删除申请要求
     * 
     * @param requirementIds 要求ID数组
     * @param operator 操作人
     * @return 影响行数
     */
    public int deleteAbrRequirementByIds(Long[] requirementIds, String operator);
}

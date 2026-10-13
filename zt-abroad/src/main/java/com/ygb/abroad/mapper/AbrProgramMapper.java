package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrProgram;

/**
 * 项目 数据层
 * 
 * @author ygb
 */
public interface AbrProgramMapper
{
    /**
     * 查询项目列表（关联院校）
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
     * @param program 项目（schoolId、programNameEn、degree）
     * @return 已存在的项目，不存在返回 null
     */
    public AbrProgram checkAbrProgramUnique(AbrProgram program);

    /**
     * 统计院校下未删除的项目数
     * 
     * @param schoolIds 院校ID数组
     * @return 项目数
     */
    public int countAbrProgramBySchoolIds(@Param("schoolIds") Long[] schoolIds);

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
     * 更新最近核对人与核对时间
     * 
     * @param programId 项目ID
     * @param verifiedBy 核对人
     * @return 影响行数
     */
    public int updateAbrProgramVerified(@Param("programId") Long programId, @Param("verifiedBy") String verifiedBy);

    /**
     * 批量逻辑删除项目
     * 
     * @param programIds 项目ID数组
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int deleteAbrProgramByIds(@Param("programIds") Long[] programIds, @Param("updateBy") String updateBy);
}

package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrSchool;

/**
 * 院校 数据层
 * 
 * @author ygb
 */
public interface AbrSchoolMapper
{
    /**
     * 查询院校列表
     * 
     * @param school 查询条件
     * @return 院校集合
     */
    public List<AbrSchool> selectAbrSchoolList(AbrSchool school);

    /**
     * 按ID查询院校
     * 
     * @param schoolId 院校ID
     * @return 院校
     */
    public AbrSchool selectAbrSchoolById(Long schoolId);

    /**
     * 校验同一国家下英文名是否唯一
     * 
     * @param school 院校（country、schoolNameEn）
     * @return 已存在的院校，不存在返回 null
     */
    public AbrSchool checkAbrSchoolUnique(AbrSchool school);

    /**
     * 新增院校
     * 
     * @param school 院校
     * @return 影响行数
     */
    public int insertAbrSchool(AbrSchool school);

    /**
     * 修改院校
     * 
     * @param school 院校
     * @return 影响行数
     */
    public int updateAbrSchool(AbrSchool school);

    /**
     * 批量逻辑删除院校
     * 
     * @param schoolIds 院校ID数组
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int deleteAbrSchoolByIds(@Param("schoolIds") Long[] schoolIds, @Param("updateBy") String updateBy);
}

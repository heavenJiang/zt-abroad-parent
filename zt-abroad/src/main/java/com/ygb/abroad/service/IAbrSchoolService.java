package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrSchool;

/**
 * 院校 服务层
 * 
 * @author ygb
 */
public interface IAbrSchoolService
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
     * @param school 院校
     * @return true 唯一
     */
    public boolean checkAbrSchoolUnique(AbrSchool school);

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
     * 批量删除院校（有项目的院校不能删除）
     * 
     * @param schoolIds 院校ID数组
     * @param operator 操作人
     * @return 影响行数
     */
    public int deleteAbrSchoolByIds(Long[] schoolIds, String operator);
}

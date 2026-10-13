package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrCnUniversity;

/**
 * 国内高校层次 服务层
 * 
 * @author ygb
 */
public interface IAbrCnUniversityService
{
    /**
     * 查询国内高校列表
     * 
     * @param university 查询条件
     * @return 高校集合
     */
    public List<AbrCnUniversity> selectAbrCnUniversityList(AbrCnUniversity university);

    /**
     * 按校名或别名模糊搜索（问卷自动带出层次用）
     * 
     * @param keyword 关键字（至少 2 个字符，否则返回空）
     * @param limit 返回条数上限（1–20）
     * @return 高校集合，按匹配程度排序
     */
    public List<AbrCnUniversity> searchAbrCnUniversity(String keyword, int limit);

    /**
     * 按ID查询国内高校
     * 
     * @param cnUniversityId 高校ID
     * @return 高校
     */
    public AbrCnUniversity selectAbrCnUniversityById(Long cnUniversityId);

    /**
     * 校验高校全称是否唯一
     * 
     * @param university 高校
     * @return true 唯一
     */
    public boolean checkAbrCnUniversityUnique(AbrCnUniversity university);

    /**
     * 新增国内高校
     * 
     * @param university 高校
     * @return 影响行数
     */
    public int insertAbrCnUniversity(AbrCnUniversity university);

    /**
     * 修改国内高校
     * 
     * @param university 高校
     * @return 影响行数
     */
    public int updateAbrCnUniversity(AbrCnUniversity university);

    /**
     * 批量删除国内高校
     * 
     * @param cnUniversityIds 高校ID数组
     * @return 影响行数
     */
    public int deleteAbrCnUniversityByIds(Long[] cnUniversityIds);
}

package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrCnUniversity;

/**
 * 国内高校层次 数据层
 * 
 * @author ygb
 */
public interface AbrCnUniversityMapper
{
    /**
     * 查询国内高校列表
     * 
     * @param university 查询条件
     * @return 高校集合
     */
    public List<AbrCnUniversity> selectAbrCnUniversityList(AbrCnUniversity university);

    /**
     * 按校名或别名模糊搜索（全称完全匹配 > 别名完全匹配 > 前缀匹配 > 包含）
     * 
     * @param keyword 关键字
     * @param limit 返回条数上限
     * @return 高校集合
     */
    public List<AbrCnUniversity> searchAbrCnUniversity(@Param("keyword") String keyword, @Param("limit") int limit);

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
     * @param universityName 高校全称
     * @return 已存在的高校，不存在返回 null
     */
    public AbrCnUniversity checkAbrCnUniversityUnique(String universityName);

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

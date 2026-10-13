package com.ygb.abroad.service.impl;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrCnUniversity;
import com.ygb.abroad.mapper.AbrCnUniversityMapper;
import com.ygb.abroad.service.IAbrCnUniversityService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 国内高校层次 服务层处理
 * 
 * @author ygb
 */
@Service
public class AbrCnUniversityServiceImpl implements IAbrCnUniversityService
{
    /** 搜索返回条数上限 */
    private static final int MAX_SEARCH_LIMIT = 20;

    @Autowired
    private AbrCnUniversityMapper universityMapper;

    /**
     * 查询国内高校列表
     * 
     * @param university 查询条件
     * @return 高校集合
     */
    @Override
    public List<AbrCnUniversity> selectAbrCnUniversityList(AbrCnUniversity university)
    {
        return universityMapper.selectAbrCnUniversityList(university);
    }

    /**
     * 按校名或别名模糊搜索（问卷自动带出层次用）
     * 
     * @param keyword 关键字（至少 2 个字符，否则返回空）
     * @param limit 返回条数上限（1–20）
     * @return 高校集合，按匹配程度排序
     */
    @Override
    public List<AbrCnUniversity> searchAbrCnUniversity(String keyword, int limit)
    {
        String kw = StringUtils.trim(keyword);
        if (StringUtils.isEmpty(kw) || kw.length() < 2)
        {
            return Collections.emptyList();
        }
        // 转义 LIKE 通配符，避免用户输入 % _ 匹配全部
        String escaped = kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        int size = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        return universityMapper.searchAbrCnUniversity(escaped, size);
    }

    /**
     * 按ID查询国内高校
     * 
     * @param cnUniversityId 高校ID
     * @return 高校
     */
    @Override
    public AbrCnUniversity selectAbrCnUniversityById(Long cnUniversityId)
    {
        return universityMapper.selectAbrCnUniversityById(cnUniversityId);
    }

    /**
     * 校验高校全称是否唯一
     * 
     * @param university 高校
     * @return true 唯一
     */
    @Override
    public boolean checkAbrCnUniversityUnique(AbrCnUniversity university)
    {
        Long id = StringUtils.isNull(university.getCnUniversityId()) ? -1L : university.getCnUniversityId();
        AbrCnUniversity info = universityMapper.checkAbrCnUniversityUnique(university.getUniversityName());
        return StringUtils.isNull(info) || info.getCnUniversityId().longValue() == id.longValue();
    }

    /**
     * 新增国内高校
     * 
     * @param university 高校
     * @return 影响行数
     */
    @Override
    public int insertAbrCnUniversity(AbrCnUniversity university)
    {
        checkTier(university);
        return universityMapper.insertAbrCnUniversity(university);
    }

    /**
     * 修改国内高校
     * 
     * @param university 高校
     * @return 影响行数
     */
    @Override
    public int updateAbrCnUniversity(AbrCnUniversity university)
    {
        checkTier(university);
        return universityMapper.updateAbrCnUniversity(university);
    }

    /**
     * 批量删除国内高校
     * 
     * @param cnUniversityIds 高校ID数组
     * @return 影响行数
     */
    @Override
    public int deleteAbrCnUniversityByIds(Long[] cnUniversityIds)
    {
        return universityMapper.deleteAbrCnUniversityByIds(cnUniversityIds);
    }

    /**
     * 层次必须是已知编码；双非与海本不进本表（搜不到即按双非处理）
     */
    private void checkTier(AbrCnUniversity university)
    {
        String tier = university.getTier();
        if (!AbrJsonFields.UNDERGRAD_TIERS.contains(tier) || "non_key".equals(tier) || "overseas".equals(tier))
        {
            throw new ServiceException("层次只能是 C9、985、211、双一流；双非院校不需要录入");
        }
    }
}

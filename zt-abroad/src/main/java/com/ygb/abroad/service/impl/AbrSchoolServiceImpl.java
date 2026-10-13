package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.service.IAbrSchoolService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 院校 服务层处理
 * 
 * @author ygb
 */
@Service
public class AbrSchoolServiceImpl implements IAbrSchoolService
{
    @Autowired
    private AbrSchoolMapper schoolMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    /**
     * 查询院校列表
     * 
     * @param school 查询条件
     * @return 院校集合
     */
    @Override
    public List<AbrSchool> selectAbrSchoolList(AbrSchool school)
    {
        return schoolMapper.selectAbrSchoolList(school);
    }

    /**
     * 按ID查询院校
     * 
     * @param schoolId 院校ID
     * @return 院校
     */
    @Override
    public AbrSchool selectAbrSchoolById(Long schoolId)
    {
        return schoolMapper.selectAbrSchoolById(schoolId);
    }

    /**
     * 校验同一国家下英文名是否唯一
     * 
     * @param school 院校
     * @return true 唯一
     */
    @Override
    public boolean checkAbrSchoolUnique(AbrSchool school)
    {
        Long schoolId = StringUtils.isNull(school.getSchoolId()) ? -1L : school.getSchoolId();
        AbrSchool info = schoolMapper.checkAbrSchoolUnique(school);
        return StringUtils.isNull(info) || info.getSchoolId().longValue() == schoolId.longValue();
    }

    /**
     * 新增院校
     * 
     * @param school 院校
     * @return 影响行数
     */
    @Override
    public int insertAbrSchool(AbrSchool school)
    {
        school.setRankings(AbrJsonFields.requireArray(school.getRankings(), "榜单名次"));
        return schoolMapper.insertAbrSchool(school);
    }

    /**
     * 修改院校
     * 
     * @param school 院校
     * @return 影响行数
     */
    @Override
    public int updateAbrSchool(AbrSchool school)
    {
        school.setRankings(AbrJsonFields.requireArray(school.getRankings(), "榜单名次"));
        return schoolMapper.updateAbrSchool(school);
    }

    /**
     * 批量删除院校（有项目的院校不能删除）
     * 
     * @param schoolIds 院校ID数组
     * @param operator 操作人
     * @return 影响行数
     */
    @Override
    public int deleteAbrSchoolByIds(Long[] schoolIds, String operator)
    {
        if (programMapper.countAbrProgramBySchoolIds(schoolIds) > 0)
        {
            throw new ServiceException("院校下还有项目，不能删除");
        }
        return schoolMapper.deleteAbrSchoolByIds(schoolIds, operator);
    }
}

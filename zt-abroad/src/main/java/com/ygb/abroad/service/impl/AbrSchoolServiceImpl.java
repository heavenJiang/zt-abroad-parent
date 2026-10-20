package com.ygb.abroad.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.service.IAbrSchoolService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
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
    public static final String SCHOOL_TABLE = "abr_school";

    /** 记录字段历史的院校字段（M2-09） */
    static final List<String> SCHOOL_FIELDS = List.of("schoolName", "schoolNameEn", "country", "city", "rankings", "moeRecognized", "website",
            "aliases", "extIds", "status");

    @Autowired
    private IAbrSourceArchiveService archiveService;

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
    @Transactional
    public int insertAbrSchool(AbrSchool school)
    {
        normalize(school);
        int rows = schoolMapper.insertAbrSchool(school);
        archiveService.recordChanges(SCHOOL_TABLE, school.getSchoolId(), null, school, SCHOOL_FIELDS, sources(school),
                AbrProgramServiceImpl.changeMode(school), school.getCreateBy());
        return rows;
    }

    private static void normalize(AbrSchool school)
    {
        school.setRankings(AbrJsonFields.requireArray(school.getRankings(), "榜单名次"));
        if (school.getAliases() != null)
        {
            school.setAliases(AbrJsonFields.requireArray(school.getAliases(), "别名"));
        }
        if (school.getExtIds() != null)
        {
            school.setExtIds(AbrJsonFields.requireObject(school.getExtIds(), "外部编号"));
        }
    }

    /**
     * 调用方随修改带来的字段来源（params.sources：字段名 → 来源），用于历史中带出依据
     */
    @SuppressWarnings("unchecked")
    private static Map<String, AbrFieldSource> sources(AbrSchool school)
    {
        Object v = school.getParams().get("sources");
        return v instanceof Map<?, ?> m ? (Map<String, AbrFieldSource>) m : null;
    }

    /**
     * 修改院校
     * 
     * @param school 院校
     * @return 影响行数
     */
    @Override
    @Transactional
    public int updateAbrSchool(AbrSchool school)
    {
        normalize(school);
        AbrSchool old = schoolMapper.selectAbrSchoolById(school.getSchoolId());
        int rows = schoolMapper.updateAbrSchool(school);
        if (old != null && rows > 0)
        {
            List<String> fields = SCHOOL_FIELDS.stream().filter(f -> !("aliases".equals(f) && school.getAliases() == null)
                    && !("extIds".equals(f) && school.getExtIds() == null)).toList();
            archiveService.recordChanges(SCHOOL_TABLE, school.getSchoolId(), old, school, fields, sources(school),
                    AbrProgramServiceImpl.changeMode(school), school.getUpdateBy());
        }
        return rows;
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

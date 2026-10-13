package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.mapper.AbrFieldSourceMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.service.IAbrProgramService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 项目与申请要求 服务层处理
 *
 * @author ygb
 */
@Service
public class AbrProgramServiceImpl implements IAbrProgramService
{
    /** 申请要求所在表名（字段来源用） */
    public static final String REQUIREMENT_TABLE = "abr_requirement";

    /**
     * 保存时必须附来源 URL 的关键字段（均分、语言、截止日）：属性名 → 中文名
     */
    private static final Map<String, String> KEY_FIELDS = new LinkedHashMap<>();

    /**
     * 可以附来源的字段：属性名 → 取值（值为空的字段不保留来源）
     */
    private static final Map<String, Function<AbrRequirement, Object>> SOURCE_FIELDS = new LinkedHashMap<>();

    static
    {
        KEY_FIELDS.put("minGpa", "最低均分");
        KEY_FIELDS.put("ieltsOverall", "雅思总分");
        KEY_FIELDS.put("ieltsMinSub", "雅思小分");
        KEY_FIELDS.put("otherLanguage", "其他语言要求");
        KEY_FIELDS.put("deadlines", "截止日");

        SOURCE_FIELDS.put("minGpa", AbrRequirement::getMinGpa);
        SOURCE_FIELDS.put("tiersNotAccepted", AbrRequirement::getTiersNotAccepted);
        SOURCE_FIELDS.put("ieltsOverall", AbrRequirement::getIeltsOverall);
        SOURCE_FIELDS.put("ieltsMinSub", AbrRequirement::getIeltsMinSub);
        SOURCE_FIELDS.put("otherLanguage", AbrRequirement::getOtherLanguage);
        SOURCE_FIELDS.put("testRequirement", AbrRequirement::getTestRequirement);
        SOURCE_FIELDS.put("testDetail", AbrRequirement::getTestDetail);
        SOURCE_FIELDS.put("prerequisites", AbrRequirement::getPrerequisites);
        SOURCE_FIELDS.put("workYears", AbrRequirement::getWorkYears);
        SOURCE_FIELDS.put("acceptCrossMajor", AbrRequirement::getAcceptCrossMajor);
        SOURCE_FIELDS.put("deadlines", AbrRequirement::getDeadlines);
    }

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private AbrFieldSourceMapper sourceMapper;

    /**
     * 查询项目列表
     *
     * @param program 查询条件
     * @return 项目集合
     */
    @Override
    public List<AbrProgram> selectAbrProgramList(AbrProgram program)
    {
        return programMapper.selectAbrProgramList(program);
    }

    /**
     * 按ID查询项目
     *
     * @param programId 项目ID
     * @return 项目
     */
    @Override
    public AbrProgram selectAbrProgramById(Long programId)
    {
        return programMapper.selectAbrProgramById(programId);
    }

    /**
     * 校验同一院校下英文名 + 学位是否唯一
     *
     * @param program 项目
     * @return true 唯一
     */
    @Override
    public boolean checkAbrProgramUnique(AbrProgram program)
    {
        Long programId = StringUtils.isNull(program.getProgramId()) ? -1L : program.getProgramId();
        AbrProgram info = programMapper.checkAbrProgramUnique(program);
        return StringUtils.isNull(info) || info.getProgramId().longValue() == programId.longValue();
    }

    /**
     * 新增项目
     *
     * @param program 项目
     * @return 影响行数
     */
    @Override
    public int insertAbrProgram(AbrProgram program)
    {
        return programMapper.insertAbrProgram(program);
    }

    /**
     * 修改项目
     *
     * @param program 项目
     * @return 影响行数
     */
    @Override
    public int updateAbrProgram(AbrProgram program)
    {
        return programMapper.updateAbrProgram(program);
    }

    /**
     * 批量删除项目（连同其申请要求）
     *
     * @param programIds 项目ID数组
     * @param operator 操作人
     * @return 影响行数
     */
    @Override
    @Transactional
    public int deleteAbrProgramByIds(Long[] programIds, String operator)
    {
        requirementMapper.deleteAbrRequirementByProgramIds(programIds, operator);
        return programMapper.deleteAbrProgramByIds(programIds, operator);
    }

    /**
     * 查询项目的申请要求（含字段来源）
     *
     * @param programId 项目ID
     * @return 申请要求集合
     */
    @Override
    public List<AbrRequirement> selectAbrRequirementByProgramId(Long programId)
    {
        List<AbrRequirement> list = requirementMapper.selectAbrRequirementByProgramId(programId);
        for (AbrRequirement requirement : list)
        {
            requirement.setSources(sourceMapper.selectAbrFieldSourceByRecord(REQUIREMENT_TABLE, requirement.getRequirementId()));
        }
        return list;
    }

    /**
     * 按ID查询申请要求（含字段来源）
     *
     * @param requirementId 要求ID
     * @return 申请要求
     */
    @Override
    public AbrRequirement selectAbrRequirementById(Long requirementId)
    {
        AbrRequirement requirement = requirementMapper.selectAbrRequirementById(requirementId);
        if (requirement != null)
        {
            requirement.setSources(sourceMapper.selectAbrFieldSourceByRecord(REQUIREMENT_TABLE, requirementId));
        }
        return requirement;
    }

    /**
     * 校验同一项目下申请季是否唯一
     *
     * @param requirement 申请要求
     * @return true 唯一
     */
    @Override
    public boolean checkAbrRequirementUnique(AbrRequirement requirement)
    {
        Long requirementId = StringUtils.isNull(requirement.getRequirementId()) ? -1L : requirement.getRequirementId();
        AbrRequirement info = requirementMapper.checkAbrRequirementUnique(requirement);
        return StringUtils.isNull(info) || info.getRequirementId().longValue() == requirementId.longValue();
    }

    /**
     * 新增申请要求并保存字段来源
     *
     * @param requirement 申请要求（含 sources）
     * @return 影响行数
     */
    @Override
    @Transactional
    public int insertAbrRequirement(AbrRequirement requirement)
    {
        if (programMapper.selectAbrProgramById(requirement.getProgramId()) == null)
        {
            throw new ServiceException("项目不存在或已删除");
        }
        Map<String, AbrFieldSource> sources = normalizeAndCheck(requirement);
        int rows = requirementMapper.insertAbrRequirement(requirement);
        saveSources(requirement, sources, requirement.getCreateBy());
        return rows;
    }

    /**
     * 修改申请要求并保存字段来源
     *
     * @param requirement 申请要求（含 sources）
     * @return 影响行数
     */
    @Override
    @Transactional
    public int updateAbrRequirement(AbrRequirement requirement)
    {
        AbrRequirement old = requirementMapper.selectAbrRequirementById(requirement.getRequirementId());
        if (old == null)
        {
            throw new ServiceException("申请要求不存在或已删除");
        }
        // 申请要求不能改挂到其他项目
        requirement.setProgramId(old.getProgramId());
        Map<String, AbrFieldSource> sources = normalizeAndCheck(requirement);
        int rows = requirementMapper.updateAbrRequirement(requirement);
        saveSources(requirement, sources, requirement.getUpdateBy());
        return rows;
    }

    /**
     * 批量删除申请要求
     *
     * @param requirementIds 要求ID数组
     * @param operator 操作人
     * @return 影响行数
     */
    @Override
    public int deleteAbrRequirementByIds(Long[] requirementIds, String operator)
    {
        return requirementMapper.deleteAbrRequirementByIds(requirementIds, operator);
    }

    /**
     * 规范化 jsonb 字段，校验小分不高于总分，并检查关键字段的来源
     *
     * @param requirement 申请要求
     * @return 需要保存的来源（字段名 → 来源），只含有值的字段
     */
    private Map<String, AbrFieldSource> normalizeAndCheck(AbrRequirement requirement)
    {
        requirement.setMinGpa(AbrJsonFields.requireMinGpa(requirement.getMinGpa()));
        requirement.setOtherLanguage(AbrJsonFields.requireObject(requirement.getOtherLanguage(), "其他语言要求"));
        requirement.setTestDetail(AbrJsonFields.requireObject(requirement.getTestDetail(), "标化分数要求"));
        requirement.setDeadlines(AbrJsonFields.requireObject(requirement.getDeadlines(), "截止日"));
        checkTiers(requirement.getTiersNotAccepted());
        BigDecimal overall = requirement.getIeltsOverall();
        BigDecimal minSub = requirement.getIeltsMinSub();
        if (overall != null && minSub != null && minSub.compareTo(overall) > 0)
        {
            throw new ServiceException("雅思小分要求不能高于总分要求");
        }

        Map<String, AbrFieldSource> given = new LinkedHashMap<>();
        if (requirement.getSources() != null)
        {
            for (AbrFieldSource source : requirement.getSources())
            {
                if (source != null && StringUtils.isNotBlank(source.getSourceUrl()))
                {
                    given.put(source.getFieldName(), source);
                }
            }
        }
        Map<String, AbrFieldSource> result = new LinkedHashMap<>();
        for (Map.Entry<String, Function<AbrRequirement, Object>> e : SOURCE_FIELDS.entrySet())
        {
            String field = e.getKey();
            if (isEmpty(e.getValue().apply(requirement)))
            {
                continue;
            }
            AbrFieldSource source = given.get(field);
            if (source == null)
            {
                if (KEY_FIELDS.containsKey(field))
                {
                    throw new ServiceException("「" + KEY_FIELDS.get(field) + "」必须填写来源链接");
                }
                continue;
            }
            String url = StringUtils.trim(source.getSourceUrl());
            if (!StringUtils.startsWithAny(url.toLowerCase(), "http://", "https://"))
            {
                throw new ServiceException("「" + KEY_FIELDS.getOrDefault(field, field) + "」的来源链接应以 http:// 或 https:// 开头");
            }
            source.setSourceUrl(url);
            result.put(field, source);
        }
        return result;
    }

    /**
     * 保存来源：删除已清空字段的来源，其余 upsert；有来源时更新项目的最近核对人与时间
     */
    private void saveSources(AbrRequirement requirement, Map<String, AbrFieldSource> sources, String operator)
    {
        Long recordId = requirement.getRequirementId();
        sourceMapper.deleteAbrFieldSourceExcept(REQUIREMENT_TABLE, recordId, new ArrayList<>(sources.keySet()));
        for (Map.Entry<String, AbrFieldSource> e : sources.entrySet())
        {
            AbrFieldSource source = e.getValue();
            source.setTableName(REQUIREMENT_TABLE);
            source.setRecordId(recordId);
            source.setFieldName(e.getKey());
            source.setOrigin("1".equals(source.getOrigin()) ? "1" : "0");
            source.setVerifiedBy(operator);
            sourceMapper.upsertAbrFieldSource(source);
        }
        if (!sources.isEmpty())
        {
            programMapper.updateAbrProgramVerified(requirement.getProgramId(), operator);
        }
    }

    /**
     * 不接受的本科层次必须是已知编码
     */
    private void checkTiers(String tiers)
    {
        if (StringUtils.isBlank(tiers))
        {
            return;
        }
        for (String tier : tiers.split(","))
        {
            if (!AbrJsonFields.UNDERGRAD_TIERS.contains(tier.trim()))
            {
                throw new ServiceException("不接受的本科层次 '" + tier.trim() + "' 不存在");
            }
        }
    }

    private static boolean isEmpty(Object value)
    {
        return value == null || (value instanceof String && StringUtils.isBlank((String) value));
    }
}

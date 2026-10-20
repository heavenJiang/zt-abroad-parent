package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.mapper.AbrFieldSourceMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.service.IAbrProgramService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 采集结果写入院校库（M2-08）：按项目页新建或更新项目与当季申请要求，字段来源指向页面存档（来源方式“自动采集”）
 * <ul>
 * <li>员工改过的字段（来源方式不是自动采集、或没有来源但已有值）不覆盖；</li>
 * <li>新项目：中文名先用英文名（接入大模型后再补），学科与专业方向按名称关键词归类；</li>
 * <li>均分（或注明不设均分线）、语言（雅思或其他语言）、截止日三类关键字段都有来源时上架（状态正常），否则停用待补，原因写进采集记录。</li>
 * </ul>
 *
 * @author ygb
 */
@Component
public class AbrProgramCrawlWriter
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final String OPERATOR = "program_crawler";

    /** 采集写入的项目字段 */
    static final Set<String> PROGRAM_FIELDS = Set.of("programNameEn", "degree", "durationMonths", "tuition", "currency", "intakes", "teachingLanguage");

    @Autowired
    private IAbrProgramService programService;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrFieldSourceMapper sourceMapper;

    /** 采集写入的申请季（为空时按日期推算：8 月起为下一年秋季） */
    @Value("${abroad.crawl.season:}")
    private String season;

    /**
     * 写入结果
     *
     * @param programId 项目
     * @param requirementId 申请要求
     * @param created 是否新建项目
     * @param listed 是否上架
     * @param reason 未上架原因或说明
     */
    record Result(Long programId, Long requirementId, boolean created, boolean listed, String reason)
    {
    }

    /**
     * 写入
     *
     * @param school 学校
     * @param url 项目页网址
     * @param snapshotId 页面存档
     * @param found 通过校验的字段（字段名 → {value, quote}）
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result write(AbrSchool school, String url, Long snapshotId, Map<String, Map<String, Object>> found)
    {
        Map<String, Object> nameField = found.get("programNameEn");
        String nameEn = nameField == null ? null : String.valueOf(nameField.get("value"));
        if (StringUtils.isBlank(nameEn))
        {
            return new Result(null, null, false, false, "没有项目名称");
        }
        AbrProgram probe = new AbrProgram();
        probe.setSchoolId(school.getSchoolId());
        probe.setProgramNameEn(nameEn);
        probe.setDegree("master");
        AbrProgram existing = programMapper.checkAbrProgramUnique(probe);
        boolean created = existing == null;
        // 改副本：同一事务内 MyBatis 一级缓存会把同一个对象交给修改流程，直接改会让字段历史比不出变化
        AbrProgram p = created ? new AbrProgram() : copy(programService.selectAbrProgramById(existing.getProgramId()), new AbrProgram());
        Map<String, AbrFieldSource> programSources = sourcesOf(AbrProgramServiceImpl.PROGRAM_TABLE, created ? null : p.getProgramId());
        if (created)
        {
            AbrProgramClassifier.Result c = AbrProgramClassifier.classify(nameEn);
            p.setSchoolId(school.getSchoolId());
            p.setProgramName(nameEn);
            p.setFieldGroup(c.fieldGroup());
            p.setMajorDirection(c.majorDirection());
            p.setDegree("master");
            p.setTeachingLanguage("en");
            p.setWebsite(StringUtils.substring(url, 0, 500));
            p.setApsRequired("N");
            p.setStatus("1");
            p.setCreateBy(OPERATOR);
        }
        boolean ours = created || isOurs(programSources.get("programNameEn"));
        Map<String, AbrFieldSource> newProgramSources = new LinkedHashMap<>();
        BeanWrapperImpl pb = new BeanWrapperImpl(p);
        for (Map.Entry<String, Map<String, Object>> e : found.entrySet())
        {
            String field = e.getKey();
            if (!PROGRAM_FIELDS.contains(field) || !mayWrite(pb.getPropertyValue(field), programSources.get(field)))
            {
                continue;
            }
            pb.setPropertyValue(field, programValue(field, e.getValue().get("value")));
            newProgramSources.put(field, source(AbrProgramServiceImpl.PROGRAM_TABLE, field, url, e.getValue(), snapshotId));
        }
        p.setUpdateBy(OPERATOR);
        p.getParams().put(IAbrSourceArchiveService.PARAM_CHANGE_MODE, "auto");
        if (created)
        {
            programService.insertAbrProgram(p);
        }
        else if (!newProgramSources.isEmpty())
        {
            programService.updateAbrProgram(p);
        }
        for (AbrFieldSource s : newProgramSources.values())
        {
            s.setRecordId(p.getProgramId());
            sourceMapper.upsertAbrFieldSource(s);
        }

        // 当季申请要求
        String season = season();
        AbrRequirement r = null;
        for (AbrRequirement x : programService.selectAbrRequirementByProgramId(p.getProgramId()))
        {
            if (season.equals(x.getSeason()))
            {
                r = copy(programService.selectAbrRequirementById(x.getRequirementId()), new AbrRequirement());
            }
        }
        boolean newReq = r == null;
        if (newReq)
        {
            r = new AbrRequirement();
            r.setProgramId(p.getProgramId());
            r.setSeason(season);
            r.setStatus("0");
            r.setCreateBy(OPERATOR);
        }
        Map<String, AbrFieldSource> reqSources = new LinkedHashMap<>();
        if (r.getSources() != null)
        {
            r.getSources().forEach(s -> reqSources.put(s.getFieldName(), s));
        }
        BeanWrapperImpl rb = new BeanWrapperImpl(r);
        boolean reqChanged = false;
        for (Map.Entry<String, Map<String, Object>> e : found.entrySet())
        {
            String field = e.getKey();
            if (PROGRAM_FIELDS.contains(field) || !rb.isWritableProperty(field) || !mayWrite(rb.getPropertyValue(field), reqSources.get(field)))
            {
                continue;
            }
            rb.setPropertyValue(field, requirementValue(e.getValue().get("value")));
            reqSources.put(field, source(AbrProgramServiceImpl.REQUIREMENT_TABLE, field, url, e.getValue(), snapshotId));
            reqChanged = true;
        }
        r.setSources(new ArrayList<>(reqSources.values()));
        r.setUpdateBy(OPERATOR);
        r.getParams().put(IAbrSourceArchiveService.PARAM_CHANGE_MODE, "auto");
        if (newReq && reqChanged)
        {
            programService.insertAbrRequirement(r);
        }
        else if (reqChanged)
        {
            programService.updateAbrRequirement(r);
        }

        // 上架判断（只改由采集建立的项目的状态）
        List<String> missing = missing(r);
        boolean listed = missing.isEmpty();
        if (ours)
        {
            String status = listed ? "0" : "1";
            if (!status.equals(p.getStatus()))
            {
                AbrProgram fresh = copy(programService.selectAbrProgramById(p.getProgramId()), new AbrProgram());
                fresh.setStatus(status);
                fresh.setUpdateBy(OPERATOR);
                fresh.getParams().put(IAbrSourceArchiveService.PARAM_CHANGE_MODE, "auto");
                programService.updateAbrProgram(fresh);
            }
        }
        String reason = listed ? "已上架" : "缺" + String.join("、", missing) + "，暂不上架";
        return new Result(p.getProgramId(), newReq && !reqChanged ? null : r.getRequirementId(), created, listed, reason);
    }

    /**
     * 页面不再是可采集的项目页（线上、往年、页面变成别的内容）：采集建立的项目停用，员工建立的不动
     *
     * @param programId 项目
     * @return 是否停用
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean retire(Long programId)
    {
        AbrProgram p = programId == null ? null : programService.selectAbrProgramById(programId);
        if (p == null || !OPERATOR.equals(p.getCreateBy()) || "1".equals(p.getStatus()))
        {
            return false;
        }
        AbrProgram c = copy(p, new AbrProgram());
        c.setStatus("1");
        c.setUpdateBy(OPERATOR);
        c.getParams().put(IAbrSourceArchiveService.PARAM_CHANGE_MODE, "auto");
        programService.updateAbrProgram(c);
        return true;
    }

    private static <T> T copy(T from, T to)
    {
        BeanUtils.copyProperties(from, to, "params");
        return to;
    }

    /**
     * 缺哪些关键字段：均分（或不设均分线）、语言、截止日
     */
    static List<String> missing(AbrRequirement r)
    {
        List<String> m = new ArrayList<>();
        if (StringUtils.isBlank(r.getMinGpa()) && !"Y".equals(r.getGpaNoCutoff()))
        {
            m.add("均分要求");
        }
        if (r.getIeltsOverall() == null && StringUtils.isBlank(r.getOtherLanguage()))
        {
            m.add("语言要求");
        }
        if (StringUtils.isBlank(r.getDeadlines()))
        {
            m.add("截止日");
        }
        return m;
    }

    /**
     * 能否写：当前为空，或当前来源为自动采集（员工改过的不覆盖）
     */
    static boolean mayWrite(Object current, AbrFieldSource source)
    {
        if (current == null || (current instanceof String s && s.isBlank()))
        {
            return true;
        }
        return isOurs(source);
    }

    private static boolean isOurs(AbrFieldSource source)
    {
        return source != null && "2".equals(source.getOrigin());
    }

    private Map<String, AbrFieldSource> sourcesOf(String table, Long id)
    {
        Map<String, AbrFieldSource> m = new LinkedHashMap<>();
        if (id != null)
        {
            sourceMapper.selectAbrFieldSourceByRecord(table, id).forEach(s -> m.put(s.getFieldName(), s));
        }
        return m;
    }

    private static AbrFieldSource source(String table, String field, String url, Map<String, Object> f, Long snapshotId)
    {
        AbrFieldSource s = new AbrFieldSource();
        s.setTableName(table);
        s.setFieldName(field);
        // 换算类字段（如英语等级）的依据是另一页：用字段自带的网址与存档
        if (f.get("sourceUrl") != null)
        {
            url = String.valueOf(f.get("sourceUrl"));
            snapshotId = f.get("snapshotId") instanceof Number n ? n.longValue() : snapshotId;
        }
        s.setSourceUrl(StringUtils.substring(url, 0, 1000));
        s.setQuote(StringUtils.substring(String.valueOf(f.get("quote")), 0, 2000));
        s.setOrigin("2");
        s.setSnapshotId(snapshotId);
        s.setVerifiedBy(OPERATOR);
        return s;
    }

    /**
     * 项目字段取值：入学季数组转逗号分隔，数字转对应类型
     */
    static Object programValue(String field, Object v)
    {
        if (v == null)
        {
            return null;
        }
        return switch (field)
        {
            case "intakes" -> v instanceof Collection<?> c ? String.join(",", c.stream().map(String::valueOf).toList()) : String.valueOf(v);
            case "durationMonths" -> ((Number) v).intValue();
            case "tuition" -> new BigDecimal(String.valueOf(v));
            default -> String.valueOf(v);
        };
    }

    /**
     * 申请要求字段取值：对象与数组转 JSON 文本，数字转 BigDecimal（整数字段由 BeanWrapper 转换）
     */
    static Object requirementValue(Object v)
    {
        if (v instanceof Map<?, ?> || v instanceof Collection<?>)
        {
            return MAPPER.writeValueAsString(v);
        }
        if (v instanceof Number n)
        {
            return new BigDecimal(String.valueOf(n));
        }
        return v == null ? null : String.valueOf(v);
    }

    String season()
    {
        if (StringUtils.isNotBlank(season))
        {
            return season.trim();
        }
        LocalDate today = LocalDate.now(AbrLookupTools.BEIJING);
        int year = today.getMonthValue() >= 8 ? today.getYear() + 1 : today.getYear();
        return year + "-fall";
    }
}

package com.ygb.abroad.growth.compliance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 事实闸的依据：内容引用的院校库记录（由服务层按引用依据读取后传入），以及院校库全部校名（找出内容提到的学校）
 *
 * @author ygb
 */
public final class ComplianceFacts
{
    /** 超过多少天未核对视为过期（与 C 端工具、项目页一致） */
    public static final int STALE_DAYS = 90;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * 一个被引用项目的可核对事实
     *
     * @param programId 项目ID
     * @param schoolId 院校ID
     * @param label 院校 + 项目名
     * @param stale 是否超过 90 天未核对（过期的数字不能写进内容）
     * @param dates 截止日
     * @param ieltsOverall 雅思总分要求
     * @param ieltsSub 雅思小分要求
     * @param tuition 学费
     * @param currency 学费币种（ISO 4217）
     * @param durationMonths 学制（月）
     * @param gpa 均分要求（百分制，各档）
     */
    public record ProgramFact(Long programId, Long schoolId, String label, boolean stale, Set<LocalDate> dates, Set<BigDecimal> ieltsOverall,
            Set<BigDecimal> ieltsSub, BigDecimal tuition, String currency, Integer durationMonths, Set<BigDecimal> gpa)
    {
    }

    private final List<ProgramFact> programs = new ArrayList<>();

    private final Set<Long> citedSchools = new LinkedHashSet<>();

    private final Map<String, Long> schoolNames = new LinkedHashMap<>();

    private final Set<String> citationTypes = new LinkedHashSet<>();

    private final List<String> citationProblems = new ArrayList<>();

    /** 被引用案例中的学生成绩（均分区间端点、雅思）：正文写的是学生成绩而非项目要求时以此核对 */
    private final Set<BigDecimal> caseScores = new LinkedHashSet<>();

    /**
     * 由项目与最新申请要求得到可核对事实
     *
     * @param program 项目
     * @param req 申请要求（可为 null）
     * @param today 今天（北京时间）
     * @return 事实
     */
    public static ProgramFact of(AbrProgram program, AbrRequirement req, LocalDate today)
    {
        LocalDate verified = MarketingCalendar.toLocalDate(program.getVerifiedTime());
        boolean stale = verified == null || ChronoUnit.DAYS.between(verified, today) > STALE_DAYS;
        Set<LocalDate> dates = new LinkedHashSet<>();
        Set<BigDecimal> overall = new LinkedHashSet<>();
        Set<BigDecimal> sub = new LinkedHashSet<>();
        Set<BigDecimal> gpa = new LinkedHashSet<>();
        if (req != null)
        {
            for (JsonNode r : read(req.getDeadlines()).path("rounds"))
            {
                try
                {
                    dates.add(LocalDate.parse(r.path("date").asString("")));
                }
                catch (DateTimeParseException e)
                {
                    // 日期格式不对的轮次不作为依据
                }
            }
            add(overall, req.getIeltsOverall());
            add(sub, req.getIeltsMinSub());
            for (JsonNode v : read(req.getMinGpa()))
            {
                if (v.isNumber())
                {
                    add(gpa, v.decimalValue());
                }
            }
        }
        String label = StringUtils.defaultString(program.getSchoolName()) + " " + StringUtils.defaultString(program.getProgramName());
        return new ProgramFact(program.getProgramId(), program.getSchoolId(), label.trim(), stale, dates, overall, sub, program.getTuition(),
                StringUtils.trimToEmpty(program.getCurrency()).toUpperCase(), program.getDurationMonths(), gpa);
    }

    private static void add(Set<BigDecimal> set, BigDecimal v)
    {
        if (v != null)
        {
            set.add(v.stripTrailingZeros());
        }
    }

    private static JsonNode read(String json)
    {
        try
        {
            return StringUtils.isBlank(json) ? MAPPER.createObjectNode() : MAPPER.readTree(json);
        }
        catch (JacksonException e)
        {
            return MAPPER.createObjectNode();
        }
    }

    /**
     * 加入被引用的项目（同时视为引用了它的院校）
     *
     * @param fact 事实
     */
    public void addProgram(ProgramFact fact)
    {
        programs.add(fact);
        if (fact.schoolId() != null)
        {
            citedSchools.add(fact.schoolId());
        }
    }

    /**
     * 加入被引用案例中的学生成绩（对外只写均分区间端点与雅思）
     *
     * @param value 数值
     */
    public void addCaseScore(BigDecimal value)
    {
        add(caseScores, value);
    }

    /**
     * 被引用案例中的学生成绩
     *
     * @return 数值
     */
    public Set<BigDecimal> caseScores()
    {
        return caseScores;
    }

    /**
     * 加入被引用的院校
     *
     * @param schoolId 院校ID
     */
    public void addCitedSchool(Long schoolId)
    {
        citedSchools.add(schoolId);
    }

    /**
     * 登记院校库中的校名（中文名至少 3 个字、英文名至少 6 个字符才参与识别，避免误认）
     *
     * @param name 校名
     * @param schoolId 院校ID
     */
    public void addSchoolName(String name, Long schoolId)
    {
        String n = StringUtils.trimToEmpty(name);
        boolean ascii = n.chars().allMatch(ch -> ch < 128);
        if (n.length() >= (ascii ? 6 : 3))
        {
            schoolNames.putIfAbsent(n, schoolId);
        }
    }

    /**
     * 记录引用依据的类型
     *
     * @param type 类型
     */
    public void addCitationType(String type)
    {
        citationTypes.add(type);
    }

    /**
     * 记录引用依据的问题（记录不存在、已下线、暂不能核对）
     *
     * @param problem 说明
     */
    public void addCitationProblem(String problem)
    {
        citationProblems.add(problem);
    }

    public List<ProgramFact> programs()
    {
        return programs;
    }

    public Set<Long> citedSchools()
    {
        return citedSchools;
    }

    public Map<String, Long> schoolNames()
    {
        return schoolNames;
    }

    public Set<String> citationTypes()
    {
        return citationTypes;
    }

    public List<String> citationProblems()
    {
        return citationProblems;
    }
}

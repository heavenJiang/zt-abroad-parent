package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 背景档案：问卷答案归一化后的标准字段（引擎的输入）
 * 
 * 只描述学生“是什么背景”，不含任何国家的子分或权重；子分由 {@link SubScoreCalculator} 按国家配置计算。
 * 
 * @author ygb
 */
public class BackgroundProfile
{
    /** 本科院校层次（c9 / 985 / 211 / double_first_class / non_key / overseas / unknown） */
    private String undergradTier;

    /** 国内高校ID（abr_cn_university，海本或搜不到为 null） */
    private Long undergradUniversityId;

    /** 均分原始分制（percent / gpa4 / gpa5 / other） */
    private String gpaScale;

    /** 均分原始值 */
    private BigDecimal gpaRaw;

    /** 其他分制的满分 */
    private BigDecimal gpaMax;

    /** 百分制均分（1 位小数） */
    private BigDecimal gpaPercent;

    /** 专业排名（top10 / top30 / other；不清楚或跳过为 null） */
    private String classRank;

    /** 本科专业（专业方向细分值，未匹配为 other） */
    private String undergradMajor;

    /** 专业相关度（same / related / cross） */
    private String majorRelevance;

    /** 相关度来源（matched 按专业对照 / self_reported 学生自评） */
    private String majorRelevanceSource;

    /** 缺先修课门数（v1 问卷不采集，记 0） */
    private Integer missingPrereqCount;

    /** 英语考试情况（taken / estimated / none） */
    private String englishStatus;

    /** 英语考试类型（ielts / toefl_ibt / toefl_band / pte / duolingo） */
    private String englishTest;

    /** 英语原始总分 */
    private BigDecimal englishTotal;

    /** 英语原始最低小分 */
    private BigDecimal englishMinSub;

    /** 是否为预估分 */
    private Boolean englishEstimated;

    /** 雅思等效总分 */
    private BigDecimal ieltsEquivTotal;

    /** 雅思等效最低小分（v1 只对雅思直接取值） */
    private BigDecimal ieltsEquivMinSub;

    /** 标化类型（gre / gmat_focus / gmat_classic / none） */
    private String stdTestType;

    /** 标化总分 */
    private Integer stdTestTotal;

    /** 标化是否为预估分 */
    private Boolean stdTestEstimated;

    /** 科研最高级别（core_journal / conference / lab_project / none） */
    private String researchLevel;

    /** 科研段数 */
    private Integer researchCount;

    /** 科研是否相关（yes / no） */
    private String researchRelevant;

    /** 实习 / 工作段数 */
    private Integer workCount;

    /** 实习 / 工作累计月数 */
    private Integer workMonths;

    /** 其中全职月数 */
    private Integer workFulltimeMonths;

    /** 公司层级（top / normal） */
    private String workCompanyTier;

    /** 实习 / 工作是否相关（yes / no） */
    private String workRelevant;

    /** 竞赛、项目、证书最高级别（international / national / provincial / school / none） */
    private String awardsLevel;

    /** 竞赛、项目、证书项数 */
    private Integer awardsCount;

    /** 竞赛等是否相关（yes / no） */
    private String awardsRelevant;

    /** 课程匹配度 0–1（德国，成绩单解析；v1 为 null） */
    private BigDecimal courseMatchRatio;

    /** 目标国家 / 地区 */
    private List<String> targetCountries = new ArrayList<>();

    /** 目标学位 */
    private String targetDegree;

    /** 目标专业方向 */
    private String targetField;

    /** 目标专业大类 */
    private String targetFieldGroup;

    /** 计划入学年份（年份 / later / undecided） */
    private String intakeYear;

    /** 距入学月数（按 9 月入学估算；later / undecided 为 null） */
    private Integer monthsToIntake;

    /** 总预算档 */
    private String budgetTotal;

    /** 排名还是就业优先 */
    private String rankVsEmployment;

    /** 城市偏好 */
    private String cityPreference;

    /** 最看重的两项 */
    private List<String> priorityTop2 = new ArrayList<>();

    /** 日语等级 */
    private String jlptLevel;

    /** 韩语等级 */
    private String topikLevel;

    /** 德国 APS 状态 */
    private String deApsStatus;

    /** 需要顾问注意的提示码（如 gpa_low_confidence、tier_unknown、major_self_reported） */
    private List<String> warnings = new ArrayList<>();

    public String getUndergradTier()
    {
        return undergradTier;
    }

    public void setUndergradTier(String undergradTier)
    {
        this.undergradTier = undergradTier;
    }

    public Long getUndergradUniversityId()
    {
        return undergradUniversityId;
    }

    public void setUndergradUniversityId(Long undergradUniversityId)
    {
        this.undergradUniversityId = undergradUniversityId;
    }

    public String getGpaScale()
    {
        return gpaScale;
    }

    public void setGpaScale(String gpaScale)
    {
        this.gpaScale = gpaScale;
    }

    public BigDecimal getGpaRaw()
    {
        return gpaRaw;
    }

    public void setGpaRaw(BigDecimal gpaRaw)
    {
        this.gpaRaw = gpaRaw;
    }

    public BigDecimal getGpaMax()
    {
        return gpaMax;
    }

    public void setGpaMax(BigDecimal gpaMax)
    {
        this.gpaMax = gpaMax;
    }

    public BigDecimal getGpaPercent()
    {
        return gpaPercent;
    }

    public void setGpaPercent(BigDecimal gpaPercent)
    {
        this.gpaPercent = gpaPercent;
    }

    public String getClassRank()
    {
        return classRank;
    }

    public void setClassRank(String classRank)
    {
        this.classRank = classRank;
    }

    public String getUndergradMajor()
    {
        return undergradMajor;
    }

    public void setUndergradMajor(String undergradMajor)
    {
        this.undergradMajor = undergradMajor;
    }

    public String getMajorRelevance()
    {
        return majorRelevance;
    }

    public void setMajorRelevance(String majorRelevance)
    {
        this.majorRelevance = majorRelevance;
    }

    public String getMajorRelevanceSource()
    {
        return majorRelevanceSource;
    }

    public void setMajorRelevanceSource(String majorRelevanceSource)
    {
        this.majorRelevanceSource = majorRelevanceSource;
    }

    public Integer getMissingPrereqCount()
    {
        return missingPrereqCount;
    }

    public void setMissingPrereqCount(Integer missingPrereqCount)
    {
        this.missingPrereqCount = missingPrereqCount;
    }

    public String getEnglishStatus()
    {
        return englishStatus;
    }

    public void setEnglishStatus(String englishStatus)
    {
        this.englishStatus = englishStatus;
    }

    public String getEnglishTest()
    {
        return englishTest;
    }

    public void setEnglishTest(String englishTest)
    {
        this.englishTest = englishTest;
    }

    public BigDecimal getEnglishTotal()
    {
        return englishTotal;
    }

    public void setEnglishTotal(BigDecimal englishTotal)
    {
        this.englishTotal = englishTotal;
    }

    public BigDecimal getEnglishMinSub()
    {
        return englishMinSub;
    }

    public void setEnglishMinSub(BigDecimal englishMinSub)
    {
        this.englishMinSub = englishMinSub;
    }

    public Boolean getEnglishEstimated()
    {
        return englishEstimated;
    }

    public void setEnglishEstimated(Boolean englishEstimated)
    {
        this.englishEstimated = englishEstimated;
    }

    public BigDecimal getIeltsEquivTotal()
    {
        return ieltsEquivTotal;
    }

    public void setIeltsEquivTotal(BigDecimal ieltsEquivTotal)
    {
        this.ieltsEquivTotal = ieltsEquivTotal;
    }

    public BigDecimal getIeltsEquivMinSub()
    {
        return ieltsEquivMinSub;
    }

    public void setIeltsEquivMinSub(BigDecimal ieltsEquivMinSub)
    {
        this.ieltsEquivMinSub = ieltsEquivMinSub;
    }

    public String getStdTestType()
    {
        return stdTestType;
    }

    public void setStdTestType(String stdTestType)
    {
        this.stdTestType = stdTestType;
    }

    public Integer getStdTestTotal()
    {
        return stdTestTotal;
    }

    public void setStdTestTotal(Integer stdTestTotal)
    {
        this.stdTestTotal = stdTestTotal;
    }

    public Boolean getStdTestEstimated()
    {
        return stdTestEstimated;
    }

    public void setStdTestEstimated(Boolean stdTestEstimated)
    {
        this.stdTestEstimated = stdTestEstimated;
    }

    public String getResearchLevel()
    {
        return researchLevel;
    }

    public void setResearchLevel(String researchLevel)
    {
        this.researchLevel = researchLevel;
    }

    public Integer getResearchCount()
    {
        return researchCount;
    }

    public void setResearchCount(Integer researchCount)
    {
        this.researchCount = researchCount;
    }

    public String getResearchRelevant()
    {
        return researchRelevant;
    }

    public void setResearchRelevant(String researchRelevant)
    {
        this.researchRelevant = researchRelevant;
    }

    public Integer getWorkCount()
    {
        return workCount;
    }

    public void setWorkCount(Integer workCount)
    {
        this.workCount = workCount;
    }

    public Integer getWorkMonths()
    {
        return workMonths;
    }

    public void setWorkMonths(Integer workMonths)
    {
        this.workMonths = workMonths;
    }

    public Integer getWorkFulltimeMonths()
    {
        return workFulltimeMonths;
    }

    public void setWorkFulltimeMonths(Integer workFulltimeMonths)
    {
        this.workFulltimeMonths = workFulltimeMonths;
    }

    public String getWorkCompanyTier()
    {
        return workCompanyTier;
    }

    public void setWorkCompanyTier(String workCompanyTier)
    {
        this.workCompanyTier = workCompanyTier;
    }

    public String getWorkRelevant()
    {
        return workRelevant;
    }

    public void setWorkRelevant(String workRelevant)
    {
        this.workRelevant = workRelevant;
    }

    public String getAwardsLevel()
    {
        return awardsLevel;
    }

    public void setAwardsLevel(String awardsLevel)
    {
        this.awardsLevel = awardsLevel;
    }

    public Integer getAwardsCount()
    {
        return awardsCount;
    }

    public void setAwardsCount(Integer awardsCount)
    {
        this.awardsCount = awardsCount;
    }

    public String getAwardsRelevant()
    {
        return awardsRelevant;
    }

    public void setAwardsRelevant(String awardsRelevant)
    {
        this.awardsRelevant = awardsRelevant;
    }

    public BigDecimal getCourseMatchRatio()
    {
        return courseMatchRatio;
    }

    public void setCourseMatchRatio(BigDecimal courseMatchRatio)
    {
        this.courseMatchRatio = courseMatchRatio;
    }

    public List<String> getTargetCountries()
    {
        return targetCountries;
    }

    public void setTargetCountries(List<String> targetCountries)
    {
        this.targetCountries = targetCountries;
    }

    public String getTargetDegree()
    {
        return targetDegree;
    }

    public void setTargetDegree(String targetDegree)
    {
        this.targetDegree = targetDegree;
    }

    public String getTargetField()
    {
        return targetField;
    }

    public void setTargetField(String targetField)
    {
        this.targetField = targetField;
    }

    public String getTargetFieldGroup()
    {
        return targetFieldGroup;
    }

    public void setTargetFieldGroup(String targetFieldGroup)
    {
        this.targetFieldGroup = targetFieldGroup;
    }

    public String getIntakeYear()
    {
        return intakeYear;
    }

    public void setIntakeYear(String intakeYear)
    {
        this.intakeYear = intakeYear;
    }

    public Integer getMonthsToIntake()
    {
        return monthsToIntake;
    }

    public void setMonthsToIntake(Integer monthsToIntake)
    {
        this.monthsToIntake = monthsToIntake;
    }

    public String getBudgetTotal()
    {
        return budgetTotal;
    }

    public void setBudgetTotal(String budgetTotal)
    {
        this.budgetTotal = budgetTotal;
    }

    public String getRankVsEmployment()
    {
        return rankVsEmployment;
    }

    public void setRankVsEmployment(String rankVsEmployment)
    {
        this.rankVsEmployment = rankVsEmployment;
    }

    public String getCityPreference()
    {
        return cityPreference;
    }

    public void setCityPreference(String cityPreference)
    {
        this.cityPreference = cityPreference;
    }

    public List<String> getPriorityTop2()
    {
        return priorityTop2;
    }

    public void setPriorityTop2(List<String> priorityTop2)
    {
        this.priorityTop2 = priorityTop2;
    }

    public String getJlptLevel()
    {
        return jlptLevel;
    }

    public void setJlptLevel(String jlptLevel)
    {
        this.jlptLevel = jlptLevel;
    }

    public String getTopikLevel()
    {
        return topikLevel;
    }

    public void setTopikLevel(String topikLevel)
    {
        this.topikLevel = topikLevel;
    }

    public String getDeApsStatus()
    {
        return deApsStatus;
    }

    public void setDeApsStatus(String deApsStatus)
    {
        this.deApsStatus = deApsStatus;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public void setWarnings(List<String> warnings)
    {
        this.warnings = warnings;
    }
}

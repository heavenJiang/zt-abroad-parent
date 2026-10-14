package com.ygb.abroad.domain.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.ygb.common.annotation.Excel;

/**
 * 项目批量导入的一行（同时用于生成导入模板）
 * 
 * 一行 = 一个项目 + 可选的一个申请季要求；院校不存在时按国家 + 英文名新建。
 * 
 * @author ygb
 */
public class AbrProgramImportRow implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 处理结果：新建项目 */
    public static final String ACTION_CREATE = "create";

    /** 处理结果：项目已存在，只新增申请季要求 */
    public static final String ACTION_ADD_REQUIREMENT = "add_requirement";

    /** 处理结果：重复，跳过 */
    public static final String ACTION_SKIP = "skip";

    /** 处理结果：有错误，不导入 */
    public static final String ACTION_ERROR = "error";

    /** 国家 */
    @Excel(name = "国家", sort = 1, dictType = "abr_country", comboReadDict = true, prompt = "必填")
    private String country;

    /** 院校英文名 */
    @Excel(name = "院校英文名", sort = 2, prompt = "必填；与已有院校按 国家 + 英文名 匹配")
    private String schoolNameEn;

    /** 院校中文名 */
    @Excel(name = "院校中文名", sort = 3, prompt = "院校不存在时必填，用于新建院校")
    private String schoolName;

    /** 项目英文名 */
    @Excel(name = "项目英文名", sort = 4, prompt = "必填；同院校同英文名同学位视为重复")
    private String programNameEn;

    /** 项目中文名 */
    @Excel(name = "项目中文名", sort = 5)
    private String programName;

    /** 学位 */
    @Excel(name = "学位", sort = 6, dictType = "abr_degree", comboReadDict = true, prompt = "必填")
    private String degree;

    /** 学科大类 */
    @Excel(name = "学科大类", sort = 7, dictType = "abr_field_group", comboReadDict = true, prompt = "必填")
    private String fieldGroup;

    /** 专业方向 */
    @Excel(name = "专业方向", sort = 8, dictType = "abr_major_direction", comboReadDict = true)
    private String majorDirection;

    /** 学制（月） */
    @Excel(name = "学制（月）", sort = 9, prompt = "1–120 的整数")
    private String durationMonths;

    /** 入学季 */
    @Excel(name = "入学季", sort = 10, dictType = "abr_intake", comboReadDict = true, prompt = "多个用逗号分隔，如 秋季,春季")
    private String intakes;

    /** 学费（每年） */
    @Excel(name = "学费（每年）", sort = 11, prompt = "数字")
    private String tuition;

    /** 币种 */
    @Excel(name = "币种", sort = 12, prompt = "3 位代码，如 GBP、USD、AUD")
    private String currency;

    /** 授课语言 */
    @Excel(name = "授课语言", sort = 13, dictType = "abr_teaching_language", comboReadDict = true, prompt = "不填默认英语")
    private String teachingLanguage;

    /** 项目官网 */
    @Excel(name = "项目官网", sort = 14)
    private String website;

    /** 需要APS */
    @Excel(name = "需要APS", sort = 15, dictType = "sys_yes_no", comboReadDict = true, prompt = "是 / 否，不填为否")
    private String apsRequired;

    /** 难度档 */
    @Excel(name = "难度档", sort = 16, dictType = "abr_program_difficulty", comboReadDict = true, prompt = "选校引擎冷启动参考线用")
    private String difficulty;

    /** 申请季 */
    @Excel(name = "申请季", sort = 17, prompt = "填写下列要求时必填，格式如 2027-fall")
    private String season;

    /** 均分要求-C9 */
    @Excel(name = "均分要求-C9", sort = 18, prompt = "百分制 0–100")
    private String gpaC9;

    /** 均分要求-985 */
    @Excel(name = "均分要求-985", sort = 19, prompt = "百分制 0–100")
    private String gpa985;

    /** 均分要求-211 */
    @Excel(name = "均分要求-211", sort = 20, prompt = "百分制 0–100")
    private String gpa211;

    /** 均分要求-双一流 */
    @Excel(name = "均分要求-双一流", sort = 21, prompt = "百分制 0–100")
    private String gpaDoubleFirstClass;

    /** 均分要求-双非 */
    @Excel(name = "均分要求-双非", sort = 22, prompt = "百分制 0–100")
    private String gpaNonKey;

    /** 均分要求-海本 */
    @Excel(name = "均分要求-海本", sort = 23, prompt = "百分制 0–100")
    private String gpaOverseas;

    /** 雅思总分 */
    @Excel(name = "雅思总分", sort = 24, prompt = "0–9")
    private String ieltsOverall;

    /** 雅思小分 */
    @Excel(name = "雅思小分", sort = 25, prompt = "0–9，不高于总分")
    private String ieltsMinSub;

    /** 截止日 */
    @Excel(name = "截止日", sort = 26, prompt = "滚动录取填“滚动”；分轮次填 R1:2027-01-15;R2:2027-03-01")
    private String deadlines;

    /** 来源链接 */
    @Excel(name = "来源链接", sort = 27, prompt = "填写了均分、雅思、截止日时必填，以 http(s):// 开头")
    private String sourceUrl;

    /** 来源原文 */
    @Excel(name = "来源原文", sort = 28, prompt = "可选，官网原句")
    private String sourceQuote;

    /** Excel 行号（从 2 开始，表头为第 1 行） */
    private Integer rowNo;

    /** 处理结果（create / add_requirement / skip / error） */
    private String action;

    /** 是否需要新建院校 */
    private Boolean newSchool;

    /** 匹配到的院校ID */
    private Long schoolId;

    /** 匹配到的项目ID */
    private Long programId;

    /** 错误或提示信息 */
    private List<String> messages = new ArrayList<>();

    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getSchoolNameEn()
    {
        return schoolNameEn;
    }

    public void setSchoolNameEn(String schoolNameEn)
    {
        this.schoolNameEn = schoolNameEn;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getProgramNameEn()
    {
        return programNameEn;
    }

    public void setProgramNameEn(String programNameEn)
    {
        this.programNameEn = programNameEn;
    }

    public String getProgramName()
    {
        return programName;
    }

    public void setProgramName(String programName)
    {
        this.programName = programName;
    }

    public String getDegree()
    {
        return degree;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    public String getFieldGroup()
    {
        return fieldGroup;
    }

    public void setFieldGroup(String fieldGroup)
    {
        this.fieldGroup = fieldGroup;
    }

    public String getMajorDirection()
    {
        return majorDirection;
    }

    public void setMajorDirection(String majorDirection)
    {
        this.majorDirection = majorDirection;
    }

    public String getDurationMonths()
    {
        return durationMonths;
    }

    public void setDurationMonths(String durationMonths)
    {
        this.durationMonths = durationMonths;
    }

    public String getIntakes()
    {
        return intakes;
    }

    public void setIntakes(String intakes)
    {
        this.intakes = intakes;
    }

    public String getTuition()
    {
        return tuition;
    }

    public void setTuition(String tuition)
    {
        this.tuition = tuition;
    }

    public String getCurrency()
    {
        return currency;
    }

    public void setCurrency(String currency)
    {
        this.currency = currency;
    }

    public String getTeachingLanguage()
    {
        return teachingLanguage;
    }

    public void setTeachingLanguage(String teachingLanguage)
    {
        this.teachingLanguage = teachingLanguage;
    }

    public String getWebsite()
    {
        return website;
    }

    public void setWebsite(String website)
    {
        this.website = website;
    }

    public String getApsRequired()
    {
        return apsRequired;
    }

    public void setApsRequired(String apsRequired)
    {
        this.apsRequired = apsRequired;
    }

    public String getDifficulty()
    {
        return difficulty;
    }

    public void setDifficulty(String difficulty)
    {
        this.difficulty = difficulty;
    }

    public String getSeason()
    {
        return season;
    }

    public void setSeason(String season)
    {
        this.season = season;
    }

    public String getGpaC9()
    {
        return gpaC9;
    }

    public void setGpaC9(String gpaC9)
    {
        this.gpaC9 = gpaC9;
    }

    public String getGpa985()
    {
        return gpa985;
    }

    public void setGpa985(String gpa985)
    {
        this.gpa985 = gpa985;
    }

    public String getGpa211()
    {
        return gpa211;
    }

    public void setGpa211(String gpa211)
    {
        this.gpa211 = gpa211;
    }

    public String getGpaDoubleFirstClass()
    {
        return gpaDoubleFirstClass;
    }

    public void setGpaDoubleFirstClass(String gpaDoubleFirstClass)
    {
        this.gpaDoubleFirstClass = gpaDoubleFirstClass;
    }

    public String getGpaNonKey()
    {
        return gpaNonKey;
    }

    public void setGpaNonKey(String gpaNonKey)
    {
        this.gpaNonKey = gpaNonKey;
    }

    public String getGpaOverseas()
    {
        return gpaOverseas;
    }

    public void setGpaOverseas(String gpaOverseas)
    {
        this.gpaOverseas = gpaOverseas;
    }

    public String getIeltsOverall()
    {
        return ieltsOverall;
    }

    public void setIeltsOverall(String ieltsOverall)
    {
        this.ieltsOverall = ieltsOverall;
    }

    public String getIeltsMinSub()
    {
        return ieltsMinSub;
    }

    public void setIeltsMinSub(String ieltsMinSub)
    {
        this.ieltsMinSub = ieltsMinSub;
    }

    public String getDeadlines()
    {
        return deadlines;
    }

    public void setDeadlines(String deadlines)
    {
        this.deadlines = deadlines;
    }

    public String getSourceUrl()
    {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl)
    {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceQuote()
    {
        return sourceQuote;
    }

    public void setSourceQuote(String sourceQuote)
    {
        this.sourceQuote = sourceQuote;
    }

    public Integer getRowNo()
    {
        return rowNo;
    }

    public void setRowNo(Integer rowNo)
    {
        this.rowNo = rowNo;
    }

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public Boolean getNewSchool()
    {
        return newSchool;
    }

    public void setNewSchool(Boolean newSchool)
    {
        this.newSchool = newSchool;
    }

    public Long getSchoolId()
    {
        return schoolId;
    }

    public void setSchoolId(Long schoolId)
    {
        this.schoolId = schoolId;
    }

    public Long getProgramId()
    {
        return programId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    public List<String> getMessages()
    {
        return messages;
    }

    public void setMessages(List<String> messages)
    {
        this.messages = messages;
    }

    /**
     * 追加一条信息
     * 
     * @param message 信息
     */
    public void addMessage(String message)
    {
        this.messages.add(message);
    }
}

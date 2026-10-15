package com.ygb.abroad.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;

/**
 * C 端：测评（只含学生本人可见的字段）
 * 
 * @author ygb
 */
public class AppAssessmentVo
{
    /** 测评ID */
    private Long assessmentId;

    /** 问卷版本ID（按此取问卷定义） */
    private Long questionnaireId;

    /** 问卷版本号 */
    private Integer questionnaireVersion;

    /** 测评状态（in_progress / terminated / submitted） */
    private String status;

    /** 断点：最近保存时所在的屏 id */
    private String currentScreen;

    /** 已保存的答案 */
    private JsonNode answers;

    /** 流程标志（如 is_minor，前端据此显示提示） */
    private JsonNode flags;

    /** 选校结果是否已生成（提交后自动计算；目标国家都未开放时为 false） */
    private Boolean resultReady;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 最近保存时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Boolean getResultReady()
    {
        return resultReady;
    }

    public void setResultReady(Boolean resultReady)
    {
        this.resultReady = resultReady;
    }

    public Long getAssessmentId()
    {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId)
    {
        this.assessmentId = assessmentId;
    }

    public Long getQuestionnaireId()
    {
        return questionnaireId;
    }

    public void setQuestionnaireId(Long questionnaireId)
    {
        this.questionnaireId = questionnaireId;
    }

    public Integer getQuestionnaireVersion()
    {
        return questionnaireVersion;
    }

    public void setQuestionnaireVersion(Integer questionnaireVersion)
    {
        this.questionnaireVersion = questionnaireVersion;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getCurrentScreen()
    {
        return currentScreen;
    }

    public void setCurrentScreen(String currentScreen)
    {
        this.currentScreen = currentScreen;
    }

    public JsonNode getAnswers()
    {
        return answers;
    }

    public void setAnswers(JsonNode answers)
    {
        this.answers = answers;
    }

    public JsonNode getFlags()
    {
        return flags;
    }

    public void setFlags(JsonNode flags)
    {
        this.flags = flags;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}

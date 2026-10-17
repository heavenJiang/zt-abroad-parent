package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 测评上传文件表 abr_assessment_file（简历、成绩单）
 * 
 * @author ygb
 */
public class AbrAssessmentFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类别：简历 */
    public static final String KIND_RESUME = "resume";

    /** 类别：成绩单 */
    public static final String KIND_TRANSCRIPT = "transcript";

    /** 状态：识别中 */
    public static final String STATUS_RUNNING = "running";

    /** 状态：已识别 */
    public static final String STATUS_DONE = "done";

    /** 状态：未识别 */
    public static final String STATUS_FAILED = "failed";

    /** 文字来源：PDF 文字层 */
    public static final String SOURCE_PDF_TEXT = "pdf_text";

    /** 文字来源：文字识别 */
    public static final String SOURCE_OCR = "ocr";

    /** 文件ID */
    private Long fileId;

    /** 测评ID */
    private Long assessmentId;

    /** 上传的访客ID */
    private Long visitorId;

    /** 类别（resume 简历 / transcript 成绩单） */
    private String fileKind;

    /** 扩展名 */
    private String fileExt;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 原文件名 */
    private String originalName;

    /** 原件在私有目录下的相对路径 */
    private String storagePath;

    /** 单独同意的编号 */
    private String consentId;

    /** 单独同意时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date consentTime;

    /** 解析状态（running 识别中 / done 已识别 / failed 未识别） */
    private String parseStatus;

    /** 认领令牌 */
    private String claimToken;

    /** 文字来源（pdf_text / ocr） */
    private String textSource;

    /** 交给模型的文字字数 */
    private Integer textChars;

    /** 通过校验的建议值（JSON） */
    private String parsed;

    /** 未识别原因 */
    private String failReason;

    /** AI 网关结果状态 */
    private String aiStatus;

    /** AI 调用日志ID */
    private Long callId;

    /** 内部错误信息 */
    private String errorMsg;

    /** 开始解析时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedTime;

    /** 解析完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedTime;

    /** 原件计划删除时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date purgeTime;

    /** 原件实际删除时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date purgedTime;

    /** 删除标志（0 有效 2 已删除） */
    private String delFlag;

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public Long getFileId()
    {
        return fileId;
    }

    public void setAssessmentId(Long assessmentId)
    {
        this.assessmentId = assessmentId;
    }

    public Long getAssessmentId()
    {
        return assessmentId;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setFileKind(String fileKind)
    {
        this.fileKind = fileKind;
    }

    public String getFileKind()
    {
        return fileKind;
    }

    public void setFileExt(String fileExt)
    {
        this.fileExt = fileExt;
    }

    public String getFileExt()
    {
        return fileExt;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setOriginalName(String originalName)
    {
        this.originalName = originalName;
    }

    public String getOriginalName()
    {
        return originalName;
    }

    public void setStoragePath(String storagePath)
    {
        this.storagePath = storagePath;
    }

    public String getStoragePath()
    {
        return storagePath;
    }

    public void setConsentId(String consentId)
    {
        this.consentId = consentId;
    }

    public String getConsentId()
    {
        return consentId;
    }

    public void setConsentTime(Date consentTime)
    {
        this.consentTime = consentTime;
    }

    public Date getConsentTime()
    {
        return consentTime;
    }

    public void setParseStatus(String parseStatus)
    {
        this.parseStatus = parseStatus;
    }

    public String getParseStatus()
    {
        return parseStatus;
    }

    public void setClaimToken(String claimToken)
    {
        this.claimToken = claimToken;
    }

    public String getClaimToken()
    {
        return claimToken;
    }

    public void setTextSource(String textSource)
    {
        this.textSource = textSource;
    }

    public String getTextSource()
    {
        return textSource;
    }

    public void setTextChars(Integer textChars)
    {
        this.textChars = textChars;
    }

    public Integer getTextChars()
    {
        return textChars;
    }

    public void setParsed(String parsed)
    {
        this.parsed = parsed;
    }

    public String getParsed()
    {
        return parsed;
    }

    public void setFailReason(String failReason)
    {
        this.failReason = failReason;
    }

    public String getFailReason()
    {
        return failReason;
    }

    public void setAiStatus(String aiStatus)
    {
        this.aiStatus = aiStatus;
    }

    public String getAiStatus()
    {
        return aiStatus;
    }

    public void setCallId(Long callId)
    {
        this.callId = callId;
    }

    public Long getCallId()
    {
        return callId;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setStartedTime(Date startedTime)
    {
        this.startedTime = startedTime;
    }

    public Date getStartedTime()
    {
        return startedTime;
    }

    public void setFinishedTime(Date finishedTime)
    {
        this.finishedTime = finishedTime;
    }

    public Date getFinishedTime()
    {
        return finishedTime;
    }

    public void setPurgeTime(Date purgeTime)
    {
        this.purgeTime = purgeTime;
    }

    public Date getPurgeTime()
    {
        return purgeTime;
    }

    public void setPurgedTime(Date purgedTime)
    {
        this.purgedTime = purgedTime;
    }

    public Date getPurgedTime()
    {
        return purgedTime;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("fileId", getFileId())
            .append("assessmentId", getAssessmentId())
            .append("visitorId", getVisitorId())
            .append("fileKind", getFileKind())
            .append("fileExt", getFileExt())
            .append("fileSize", getFileSize())
            .append("originalName", getOriginalName())
            .append("storagePath", getStoragePath())
            .append("consentId", getConsentId())
            .append("consentTime", getConsentTime())
            .append("parseStatus", getParseStatus())
            .append("textSource", getTextSource())
            .append("textChars", getTextChars())
            .append("failReason", getFailReason())
            .append("aiStatus", getAiStatus())
            .append("callId", getCallId())
            .append("errorMsg", getErrorMsg())
            .append("startedTime", getStartedTime())
            .append("finishedTime", getFinishedTime())
            .append("purgeTime", getPurgeTime())
            .append("purgedTime", getPurgedTime())
            .append("delFlag", getDelFlag())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}

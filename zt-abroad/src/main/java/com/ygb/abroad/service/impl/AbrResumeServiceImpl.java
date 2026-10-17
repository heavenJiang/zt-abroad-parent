package com.ygb.abroad.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.domain.AbrAssessmentFile;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppResumeParseVo;
import com.ygb.abroad.integration.AbrDocumentText;
import com.ygb.abroad.integration.AbrOcrClient;
import com.ygb.abroad.integration.AbrPrivateFileStore;
import com.ygb.abroad.integration.AbrResumeProperties;
import com.ygb.abroad.mapper.AbrAssessmentFileMapper;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.service.IAbrCnUniversityService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrResumeService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;
import com.ygb.common.utils.file.FileUploadUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 简历 / 成绩单上传与解析 服务层实现
 *
 * 上传：校验单独同意、格式（按文件头）、大小、次数 → 原件存私有目录 → 提交后在线程池中识别。
 * 识别：PDF 取文字层（扫描件、图片走文字识别；未接入时提示手动填写，图片原件不送大模型）→ 去掉无关个人信息、找出姓名
 * → 经 AI 网关调用 resume_parser（去标识化、Schema 校验、调用日志）→ 逐项校验 → 写回建议值。前端轮询识别结果。
 *
 * @author ygb
 */
@Service
public class AbrResumeServiceImpl implements IAbrResumeService
{
    private static final Logger log = LoggerFactory.getLogger(AbrResumeServiceImpl.class);

    /** 调用日志的业务类型 */
    public static final String BIZ_TYPE = "assessment_resume";

    /** 上传题目 id */
    static final String Q_UPLOAD = "resume_upload";

    /** 识别中超过该秒数视为中断（大于 Agent 超时 × 主备两次 + 格式重试，再加文字识别） */
    static final int STALE_SECONDS = 180;

    /** 事件：上传简历 */
    static final String EVENT_UPLOADED = "assessment_resume_uploaded";

    /** 事件：删除简历 */
    static final String EVENT_DELETED = "assessment_resume_deleted";

    /** 定时任务每次最多删除的份数 */
    private static final int PURGE_BATCH = 500;

    /** 未识别原因 → 提示文字（null 为默认） */
    static final Map<String, String> MESSAGES = new HashMap<>();

    static
    {
        MESSAGES.put(null, "没识别出来，手动填也只要 1 分钟");
        MESSAGES.put("no_text", "没识别出文字，手动填也只要 1 分钟");
        MESSAGES.put("ocr_unavailable", "图片和扫描版 PDF 暂不能自动识别，请手动填写，或上传可以复制文字的 PDF");
        MESSAGES.put("nothing_found", "没找到可以自动填写的内容，请手动填写");
    }

    @Autowired
    private AbrAssessmentFileMapper fileMapper;

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Autowired
    private IAbrCnUniversityService cnUniversityService;

    @Autowired
    private IAbrEventService eventService;

    @Autowired
    private AbrPrivateFileStore fileStore;

    @Autowired
    private AbrResumeProperties properties;

    @Autowired(required = false)
    private AbrOcrClient ocrClient;

    @Autowired
    private AiGateway gateway;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private Executor executor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppResumeParseVo uploadResume(AbrVisitor visitor, Long assessmentId, String kind, Boolean consent, MultipartFile file)
    {
        AbrAssessment assessment = owned(visitor, assessmentId);
        if (!AbrAssessment.STATUS_IN_PROGRESS.equals(assessment.getAssessmentStatus()))
        {
            throw new ServiceException("测评已结束，不能再上传");
        }
        AbrAssessmentAnswers.Questionnaire q = questionnaire(assessment);
        JsonNode question = q.questions().get(Q_UPLOAD);
        if (question == null || !"file".equals(question.path("type").asString("")))
        {
            throw new ServiceException("当前测评不支持上传简历");
        }
        if (!Boolean.TRUE.equals(consent))
        {
            throw new ServiceException("请先阅读并勾选同意后再上传");
        }
        JsonNode spec = question.path("file");
        if (!hasValue(spec.path("kinds"), kind))
        {
            throw new ServiceException("请选择上传的是简历还是成绩单");
        }
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要上传的文件");
        }
        int maxMb = Math.min(spec.path("max_size_mb").asInt(properties.getMaxSizeMb()), properties.getMaxSizeMb());
        if (file.getSize() > maxMb * 1024L * 1024L)
        {
            throw new ServiceException("文件不能超过 " + maxMb + "MB");
        }
        String ext = StringUtils.lowerCase(FileUploadUtils.getExtension(file));
        if (!FileUploadUtils.isAllowedExtension(ext, accept(spec)))
        {
            throw new ServiceException("只支持 PDF 或 JPG、PNG 图片");
        }
        byte[] bytes;
        try
        {
            bytes = file.getBytes();
        }
        catch (IOException e)
        {
            throw new ServiceException("文件读取失败，请重新上传");
        }
        String type = AbrDocumentText.detectType(bytes);
        if (type == null || !type.equals("jpeg".equals(ext) ? "jpg" : ext))
        {
            throw new ServiceException("文件内容与格式不符，请上传 PDF 或 JPG、PNG 图片");
        }
        if (fileMapper.countAbrAssessmentFileUploads(assessmentId) >= properties.getMaxUploads())
        {
            throw new ServiceException("上传次数已达上限，请手动填写");
        }

        String consentId = question.path("consent_ref").asString("resume_ai_parse");
        AbrAssessmentFile previous = null;
        for (AbrAssessmentFile f : fileMapper.selectAbrAssessmentFileList(assessmentId))
        {
            if (kind.equals(f.getFileKind()))
            {
                previous = f;
            }
        }
        String path = fileStore.save(bytes, type);
        AbrAssessmentFile row = new AbrAssessmentFile();
        row.setAssessmentId(assessmentId);
        row.setVisitorId(visitor.getVisitorId());
        row.setFileKind(kind);
        row.setFileExt(type);
        row.setFileSize((long) bytes.length);
        row.setOriginalName(StringUtils.substring(FilenameUtils.getName(file.getOriginalFilename()), 0, 100));
        row.setStoragePath(path);
        row.setConsentId(consentId);
        row.setConsentTime(new Date());
        row.setClaimToken(UUID.randomUUID().toString());
        row.setPurgeTime(DateUtils.addDays(new Date(), properties.getRetentionDays()));
        String oldPath = previous == null ? null : previous.getStoragePath();
        try
        {
            if (previous != null)
            {
                fileMapper.deleteAbrAssessmentFileById(previous.getFileId());
            }
            fileMapper.insertAbrAssessmentFile(row);
        }
        catch (DuplicateKeyException e)
        {
            fileStore.delete(path);
            throw new ServiceException("正在上传，请稍后再试");
        }
        Long fileId = row.getFileId();
        String token = row.getClaimToken();
        Long questionnaireId = assessment.getQuestionnaireId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(int status)
            {
                if (status != STATUS_COMMITTED)
                {
                    fileStore.delete(path);
                    return;
                }
                fileStore.delete(oldPath);
                try
                {
                    executor.execute(() -> parse(fileId, token, kind, type, path, questionnaireId));
                }
                catch (RejectedExecutionException e)
                {
                    finish(fileId, token, AbrAssessmentFile.STATUS_FAILED, null, null, null, "ai_failed", null, null, "线程池已满，未识别");
                }
            }
        });
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        payload.put("fileType", type);
        payload.put("replaced", previous != null);
        eventService.recordAction(visitor, EVENT_UPLOADED, "assessment", assessmentId, payload);
        return view(assessment, fileMapper.selectAbrAssessmentFileList(assessmentId));
    }

    @Override
    public AppResumeParseVo selectResumeParse(AbrVisitor visitor, Long assessmentId)
    {
        AbrAssessment assessment = owned(visitor, assessmentId);
        fileMapper.failStaleAbrAssessmentFiles(assessmentId, STALE_SECONDS);
        return view(assessment, fileMapper.selectAbrAssessmentFileList(assessmentId));
    }

    @Override
    public AppResumeParseVo deleteResume(AbrVisitor visitor, Long assessmentId, Long fileId)
    {
        AbrAssessment assessment = owned(visitor, assessmentId);
        AbrAssessmentFile f = fileId == null ? null : fileMapper.selectAbrAssessmentFileById(fileId);
        if (f == null || !assessmentId.equals(f.getAssessmentId()) || !"0".equals(f.getDelFlag()))
        {
            throw new ServiceException("文件不存在");
        }
        if (fileMapper.deleteAbrAssessmentFileById(fileId) > 0)
        {
            fileStore.delete(f.getStoragePath());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kind", f.getFileKind());
            eventService.recordAction(visitor, EVENT_DELETED, "assessment", assessmentId, payload);
        }
        return view(assessment, fileMapper.selectAbrAssessmentFileList(assessmentId));
    }

    @Override
    public int purgeExpiredResumes()
    {
        int purged = 0;
        for (AbrAssessmentFile f : fileMapper.selectExpiredAbrAssessmentFiles(PURGE_BATCH))
        {
            if (fileStore.delete(f.getStoragePath()))
            {
                purged += fileMapper.purgeAbrAssessmentFile(f.getFileId());
            }
        }
        if (purged > 0)
        {
            log.info("已删除到期的简历 / 成绩单原件 {} 份", purged);
        }
        return purged;
    }

    /**
     * 后台识别：取文字、预处理、调用网关、校验、写回（任何异常都记为未识别，学生手动填写）
     */
    void parse(Long fileId, String token, String kind, String type, String path, Long questionnaireId)
    {
        String source = null;
        Integer chars = null;
        AiResult result = null;
        try
        {
            byte[] bytes = fileStore.read(path);
            String text;
            if ("pdf".equals(type))
            {
                text = AbrDocumentText.pdfText(bytes, properties.getMaxPages());
                source = AbrAssessmentFile.SOURCE_PDF_TEXT;
                if (AbrDocumentText.meaningfulChars(text) < properties.getMinTextChars())
                {
                    if (ocrClient == null)
                    {
                        finish(fileId, token, AbrAssessmentFile.STATUS_FAILED, source, null, null, "ocr_unavailable", null, null, "扫描版 PDF，未接入文字识别");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (byte[] page : AbrDocumentText.pdfPageImages(bytes, properties.getMaxPages()))
                    {
                        sb.append(ocrClient.recognize(page, "png")).append('\n');
                    }
                    text = sb.toString();
                    source = AbrAssessmentFile.SOURCE_OCR;
                }
            }
            else
            {
                source = AbrAssessmentFile.SOURCE_OCR;
                if (ocrClient == null)
                {
                    finish(fileId, token, AbrAssessmentFile.STATUS_FAILED, source, null, null, "ocr_unavailable", null, null, "图片，未接入文字识别");
                    return;
                }
                text = ocrClient.recognize(bytes, type);
            }
            if (AbrDocumentText.meaningfulChars(text) < properties.getMinTextChars())
            {
                finish(fileId, token, AbrAssessmentFile.STATUS_FAILED, source, null, null, "no_text", null, null, "文字过少");
                return;
            }
            AbrResumeParser.Prepared prepared = AbrResumeParser.prepare(text, properties.getMaxChars());
            chars = prepared.text().length();
            AiContext context = AiContext.of(BIZ_TYPE, String.valueOf(fileId), "");
            context.setKnownNames(prepared.names());
            result = gateway.invoke(AbrResumeParser.AGENT_CODE, AbrResumeParser.input(kind, prepared), context);
            if (!result.isSuccess())
            {
                finish(fileId, token, AbrAssessmentFile.STATUS_FAILED, source, chars, null, "ai_failed", result.getStatus(), result.getCallId(),
                        result.getMessage());
                return;
            }
            AbrAssessmentAnswers.Questionnaire q = questionnaire(questionnaireId);
            ObjectNode parsed = AbrResumeParser.validate(q, result.getOutput(), prepared.text(),
                    name -> cnUniversityService.searchAbrCnUniversity(name, 10));
            boolean found = !parsed.path("suggestions").isEmpty() || !parsed.path("schoolCandidates").isEmpty();
            finish(fileId, token, found ? AbrAssessmentFile.STATUS_DONE : AbrAssessmentFile.STATUS_FAILED, source, chars, parsed.toString(),
                    found ? null : "nothing_found", result.getStatus(), result.getCallId(), null);
        }
        catch (IOException e)
        {
            finish(fileId, token, AbrAssessmentFile.STATUS_FAILED, source, chars, null, "no_text", null, null, "文件无法读取：" + e.getMessage());
        }
        catch (RuntimeException e)
        {
            log.warn("简历识别失败 fileId={}：{}", fileId, e.getMessage());
            finish(fileId, token, AbrAssessmentFile.STATUS_FAILED, source, chars, null, "ai_failed", result == null ? null : result.getStatus(),
                    result == null ? null : result.getCallId(), e.getMessage());
        }
    }

    private void finish(Long fileId, String token, String status, String source, Integer chars, String parsed, String failReason,
            String aiStatus, Long callId, String error)
    {
        AbrAssessmentFile f = new AbrAssessmentFile();
        f.setFileId(fileId);
        f.setClaimToken(token);
        f.setParseStatus(status);
        f.setTextSource(source);
        f.setTextChars(chars);
        f.setParsed(parsed);
        f.setFailReason(failReason);
        f.setAiStatus(aiStatus);
        f.setCallId(callId);
        f.setErrorMsg(StringUtils.substring(error, 0, 500));
        fileMapper.finishAbrAssessmentFile(f);
    }

    private AppResumeParseVo view(AbrAssessment assessment, List<AbrAssessmentFile> files)
    {
        return AbrResumeParser.merge(files, AbrAssessmentAnswers.object(assessment.getAnswers()), MESSAGES);
    }

    private AbrAssessment owned(AbrVisitor visitor, Long assessmentId)
    {
        AbrAssessment assessment = assessmentId == null ? null : assessmentMapper.selectAbrAssessmentById(assessmentId);
        if (assessment == null || !assessment.getVisitorId().equals(visitor.getVisitorId()))
        {
            throw new ServiceException("测评不存在");
        }
        return assessment;
    }

    private AbrAssessmentAnswers.Questionnaire questionnaire(AbrAssessment assessment)
    {
        return questionnaire(assessment.getQuestionnaireId());
    }

    private AbrAssessmentAnswers.Questionnaire questionnaire(Long questionnaireId)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectAbrQuestionnaireById(questionnaireId);
        if (questionnaire == null)
        {
            throw new ServiceException("问卷不存在");
        }
        return AbrAssessmentAnswers.parse(questionnaire.getDefinition());
    }

    private static boolean hasValue(JsonNode options, String value)
    {
        for (JsonNode o : options)
        {
            if (o.path("value").asString("").equals(value))
            {
                return true;
            }
        }
        return false;
    }

    private static String[] accept(JsonNode spec)
    {
        List<String> exts = new ArrayList<>();
        for (JsonNode e : spec.path("accept"))
        {
            exts.add(e.asString(""));
        }
        return exts.isEmpty() ? new String[] { "pdf", "jpg", "jpeg", "png" } : exts.toArray(new String[0]);
    }
}

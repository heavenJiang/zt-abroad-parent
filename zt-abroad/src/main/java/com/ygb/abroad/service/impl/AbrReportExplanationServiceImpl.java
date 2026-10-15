package com.ygb.abroad.service.impl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrReportExplanation;
import com.ygb.abroad.domain.vo.AppReportVo;
import com.ygb.abroad.mapper.AbrReportExplanationMapper;
import com.ygb.abroad.service.IAbrReportExplanationService;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评解读 服务层实现
 *
 * 生成在线程池中进行（不占用报告接口的请求线程）：认领 → 经 AI 网关调用 report_explainer（审核策略按 Agent 配置，默认直接展示 + 抽检）
 * → 逐条校验 → 写回。前端轮询报告接口，解读状态从 pending 变为 done / fallback。
 *
 * @author ygb
 */
@Service
public class AbrReportExplanationServiceImpl implements IAbrReportExplanationService
{
    private static final Logger log = LoggerFactory.getLogger(AbrReportExplanationServiceImpl.class);

    /** 调用日志的业务类型 */
    public static final String BIZ_TYPE = "assessment_report";

    /** 生成中超过该秒数视为中断，下次查看报告时重新生成（大于 Agent 超时 × 主备两次 + 格式重试） */
    static final int STALE_SECONDS = 150;

    @Autowired
    private AbrReportExplanationMapper explanationMapper;

    @Autowired
    private AiGateway gateway;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private Executor executor;

    @Override
    public AppReportVo explain(AppReportVo report, String degreeLabel, String fieldLabel)
    {
        if (!AbrAssessmentReport.MODE_FULL.equals(report.mode()))
        {
            return report;
        }
        AbrReportExplainer.Texts texts = AbrReportExplainer.templates(report);
        String token = UUID.randomUUID().toString();
        if (explanationMapper.claimAbrReportExplanation(report.assessmentId(), token, STALE_SECONDS) == 1)
        {
            Map<String, Object> input = AbrReportExplainer.input(report, degreeLabel, fieldLabel, texts);
            try
            {
                executor.execute(() -> generate(report.assessmentId(), token, texts, input));
            }
            catch (RejectedExecutionException e)
            {
                finish(report.assessmentId(), token, AbrReportExplanation.STATUS_FAILED, null, null, null, "线程池已满，未生成");
                return report.withExplanation(AbrReportExplainer.explanation(texts, null, AppReportVo.EXPLAIN_FALLBACK));
            }
            return report.withExplanation(AbrReportExplainer.explanation(texts, null, AppReportVo.EXPLAIN_PENDING));
        }
        AbrReportExplanation row = explanationMapper.selectAbrReportExplanationByAssessmentId(report.assessmentId());
        if (row == null)
        {
            return report.withExplanation(AbrReportExplainer.explanation(texts, null, AppReportVo.EXPLAIN_FALLBACK));
        }
        if (AbrReportExplanation.STATUS_DONE.equals(row.getExplainStatus()) && Boolean.TRUE.equals(row.getCurrent()))
        {
            return report.withExplanation(AbrReportExplainer.explanation(texts, AbrAssessmentAnswers.object(row.getAiOutput()),
                    AppReportVo.EXPLAIN_DONE));
        }
        String status = AbrReportExplanation.STATUS_FAILED.equals(row.getExplainStatus()) ? AppReportVo.EXPLAIN_FALLBACK : AppReportVo.EXPLAIN_PENDING;
        return report.withExplanation(AbrReportExplainer.explanation(texts, null, status));
    }

    /**
     * 后台生成：调用网关、校验、写回（任何异常都记为失败，报告继续用模板文字）
     */
    void generate(Long assessmentId, String token, AbrReportExplainer.Texts texts, Map<String, Object> input)
    {
        AiResult result = null;
        try
        {
            result = gateway.invoke(AbrReportExplainer.AGENT_CODE, input, AiContext.of(BIZ_TYPE, String.valueOf(assessmentId), ""));
            if (!result.isSuccess())
            {
                finish(assessmentId, token, AbrReportExplanation.STATUS_FAILED, null, result.getStatus(), result.getCallId(), result.getMessage());
                return;
            }
            ObjectNode kept = AbrReportExplainer.validate(texts, result.getOutput(), input);
            finish(assessmentId, token, AbrReportExplanation.STATUS_DONE, kept.toString(), result.getStatus(), result.getCallId(), null);
        }
        catch (RuntimeException e)
        {
            log.warn("测评解读生成失败 assessmentId={}：{}", assessmentId, e.getMessage());
            finish(assessmentId, token, AbrReportExplanation.STATUS_FAILED, null, result == null ? null : result.getStatus(),
                    result == null ? null : result.getCallId(), e.getMessage());
        }
    }

    private void finish(Long assessmentId, String token, String status, String output, String aiStatus, Long callId, String error)
    {
        AbrReportExplanation e = new AbrReportExplanation();
        e.setAssessmentId(assessmentId);
        e.setClaimToken(token);
        e.setExplainStatus(status);
        e.setAiOutput(output);
        e.setAiStatus(aiStatus);
        e.setCallId(callId);
        e.setErrorMsg(StringUtils.substring(error, 0, 500));
        explanationMapper.finishAbrReportExplanation(e);
    }
}

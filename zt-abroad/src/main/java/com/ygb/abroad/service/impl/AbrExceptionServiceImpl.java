package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.ai.AiJson;
import com.ygb.abroad.domain.AbrAiEvalCase;
import com.ygb.abroad.domain.AbrAiReview;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrGrowthSignal;
import com.ygb.abroad.growth.event.ContentGateChanged;
import com.ygb.abroad.mapper.AbrAiReviewMapper;
import com.ygb.abroad.mapper.AbrContentMapper;
import com.ygb.abroad.service.IAbrContentService;
import com.ygb.abroad.service.IAbrEvalCaseService;
import com.ygb.abroad.service.IAbrExceptionService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 例外队列与抽检 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrExceptionServiceImpl implements IAbrExceptionService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 例外与抽检的队列 */
    private static final List<String> QUEUES = List.of(AiConstants.QUEUE_EXCEPTION, AiConstants.QUEUE_SAMPLE);

    /** 未处理的状态 */
    private static final List<String> OPEN = List.of(AiConstants.REVIEW_PENDING, AiConstants.REVIEW_SAMPLED);

    @Autowired
    private AbrAiReviewMapper reviewMapper;

    @Autowired
    private AbrContentMapper contentMapper;

    @Autowired
    private IAbrContentService contentService;

    @Autowired
    private IAbrEvalCaseService evalCaseService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Override
    public List<AbrAiReview> selectExceptionList(AbrAiReview review)
    {
        if (StringUtils.isEmpty(review.getQueueType()) || !QUEUES.contains(review.getQueueType()))
        {
            review.setQueueType(null);
            review.getParams().put("queueTypes", QUEUES);
        }
        return reviewMapper.selectAbrAiReviewList(review);
    }

    @Override
    public AbrAiReview selectExceptionById(Long reviewId)
    {
        AbrAiReview r = reviewId == null ? null : reviewMapper.selectAbrAiReviewById(reviewId);
        return r != null && QUEUES.contains(r.getQueueType()) ? r : null;
    }

    @Override
    public Long enqueue(String queueType, String reasonCode, String agentCode, String bizType, String bizId, Long callId, String snapshotJson,
            String flagsJson, String sourceInputJson)
    {
        if (!QUEUES.contains(queueType))
        {
            throw new ServiceException("队列只能是 exception / sample");
        }
        AbrAiReview r = new AbrAiReview();
        r.setQueueType(queueType);
        r.setReasonCode(reasonCode);
        r.setAgentCode(StringUtils.defaultIfBlank(agentCode, ""));
        r.setBizType(bizType);
        r.setBizId(bizId);
        r.setCallId(callId);
        r.setContent(StringUtils.defaultIfBlank(snapshotJson, "{}"));
        r.setFlags(StringUtils.defaultIfBlank(flagsJson, "[]"));
        r.setSourceInput(StringUtils.trimToNull(sourceInputJson));
        r.setStatus(AiConstants.QUEUE_SAMPLE.equals(queueType) ? AiConstants.REVIEW_SAMPLED : AiConstants.REVIEW_PENDING);
        r.setCreateBy(StringUtils.defaultIfBlank(agentCode, "system"));
        AbrAiReview open = reviewMapper.selectOpenByBiz(queueType, bizType, bizId);
        if (open != null)
        {
            r.setReviewId(open.getReviewId());
            reviewMapper.updateOpenSnapshot(r);
            return open.getReviewId();
        }
        try
        {
            reviewMapper.insertAbrAiReview(r);
            return r.getReviewId();
        }
        catch (DuplicateKeyException e)
        {
            // 并发写入同一对象：另一条已写入，改为更新它
            open = reviewMapper.selectOpenByBiz(queueType, bizType, bizId);
            r.setReviewId(open.getReviewId());
            reviewMapper.updateOpenSnapshot(r);
            return open.getReviewId();
        }
    }

    /**
     * 内容闸门结果变化：闸门检查拦截进例外；改稿后通过、员工审核或删除时关闭例外；被抽中的内容自动通过后进抽检
     *
     * @param e 事件
     */
    @EventListener
    public void onContentGateChanged(ContentGateChanged e)
    {
        String bizId = String.valueOf(e.contentId());
        if (e.removed())
        {
            close(AiConstants.QUEUE_EXCEPTION, bizId, AiConstants.REVIEW_REJECTED, "内容已删除或归档", e.checker());
            close(AiConstants.QUEUE_SAMPLE, bizId, AiConstants.REVIEW_REJECTED, "内容已删除或归档，未抽检", e.checker());
            return;
        }
        if (AbrContent.GATE_BLOCKED.equals(e.gateStatus()))
        {
            if (!e.manual())
            {
                AbrContent c = contentMapper.selectAbrContentById(e.contentId());
                JsonNode gate = AiJson.parseOrNull(c.getGateResult());
                enqueue(AiConstants.QUEUE_EXCEPTION, REASON_GATE_BLOCKED, c.getAgentCode(), BIZ_CONTENT, bizId, c.getAiCallId(), snapshot(c),
                        gate == null ? "[]" : gate.path("blocked").toString(), null);
            }
            else
            {
                close(AiConstants.QUEUE_EXCEPTION, bizId, AiConstants.REVIEW_REJECTED, "员工审核拦截：" + StringUtils.defaultString(e.note()), e.checker());
            }
        }
        else if (AbrContent.GATE_MANUAL_PASSED.equals(e.gateStatus()))
        {
            close(AiConstants.QUEUE_EXCEPTION, bizId, AiConstants.REVIEW_APPROVED, "员工放行：" + StringUtils.defaultString(e.note()), e.checker());
        }
        else if (AbrContent.GATE_PASSED.equals(e.gateStatus()))
        {
            close(AiConstants.QUEUE_EXCEPTION, bizId, AiConstants.REVIEW_EDITED, "改稿后通过闸门", e.checker());
            AbrContent c = contentMapper.selectAbrContentById(e.contentId());
            if ("Y".equals(c.getSampled()))
            {
                enqueue(AiConstants.QUEUE_SAMPLE, REASON_AUTO_SAMPLE, c.getAgentCode(), BIZ_CONTENT, bizId, c.getAiCallId(), snapshot(c), "[]", null);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrAiReview release(Long reviewId, String note, String operator)
    {
        AbrAiReview r = requireOpen(reviewId, AiConstants.QUEUE_EXCEPTION);
        String text = StringUtils.trimToEmpty(note);
        if (text.length() > 500)
        {
            throw new ServiceException("审核意见最多 500 个字符");
        }
        if (BIZ_CONTENT.equals(r.getBizType()))
        {
            // 内容记为人工审核通过；事件监听同时关闭本条例外
            contentService.reviewGate(Long.valueOf(r.getBizId()), true, text, operator);
        }
        finish(r, AiConstants.REVIEW_APPROVED, text, operator);
        return reviewMapper.selectAbrAiReviewById(reviewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrAiReview confirmBlock(Long reviewId, String reason, String operator)
    {
        AbrAiReview r = requireOpen(reviewId, AiConstants.QUEUE_EXCEPTION);
        String text = requireReason(reason);
        finish(r, AiConstants.REVIEW_REJECTED, text, operator);
        if (aiGenerated(r))
        {
            evalCaseService.createFromReview(reviewMapper.selectAbrAiReviewById(reviewId), AbrAiEvalCase.SOURCE_EXCEPTION, text, operator);
        }
        return reviewMapper.selectAbrAiReviewById(reviewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrAiReview samplePass(Long reviewId, String operator)
    {
        AbrAiReview r = requireOpen(reviewId, AiConstants.QUEUE_SAMPLE);
        finish(r, AiConstants.REVIEW_APPROVED, "", operator);
        signal(r, AbrGrowthSignal.TYPE_SAMPLE_PASS);
        return reviewMapper.selectAbrAiReviewById(reviewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrAiReview sampleFail(Long reviewId, String reason, String operator)
    {
        AbrAiReview r = requireOpen(reviewId, AiConstants.QUEUE_SAMPLE);
        String text = requireReason(reason);
        finish(r, AiConstants.REVIEW_REJECTED, text, operator);
        if (BIZ_CONTENT.equals(r.getBizType()))
        {
            AbrContent c = contentMapper.selectAbrContentById(Long.valueOf(r.getBizId()));
            if (c != null && !AbrContent.STATUS_PUBLISHED.equals(c.getStatus()) && !AbrContent.STATUS_ARCHIVED.equals(c.getStatus()))
            {
                // 还没发布：拦下，回到草稿
                contentService.reviewGate(c.getContentId(), false, "抽检不合格：" + text, operator);
            }
        }
        signal(r, AbrGrowthSignal.TYPE_SAMPLE_FAIL);
        if (aiGenerated(r))
        {
            evalCaseService.createFromReview(reviewMapper.selectAbrAiReviewById(reviewId), AbrAiEvalCase.SOURCE_SAMPLE, text, operator);
        }
        return reviewMapper.selectAbrAiReviewById(reviewId);
    }

    /**
     * 内容快照：标题、正文、平台、账号、话题、闸门结果
     */
    private static String snapshot(AbrContent c)
    {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("contentId", c.getContentId());
        n.put("contentCode", c.getContentCode());
        n.put("title", c.getTitle());
        n.put("body", c.getBody());
        n.put("platform", c.getPlatform());
        n.put("accountCode", c.getAccountCode());
        n.put("contentKind", c.getContentKind());
        n.put("aiGenerated", c.getAiGenerated());
        n.set("hashtags", AiJson.parseOrNull(StringUtils.defaultIfBlank(c.getHashtags(), "[]")));
        JsonNode gate = AiJson.parseOrNull(c.getGateResult());
        if (gate != null)
        {
            n.set("gate", gate);
        }
        return n.toString();
    }

    /**
     * AI 生成的才回流评测集：有 AI 调用，或快照标明 AI 生成
     */
    private static boolean aiGenerated(AbrAiReview r)
    {
        if (StringUtils.isBlank(r.getAgentCode()))
        {
            return false;
        }
        JsonNode snap = AiJson.parseOrNull(r.getContent());
        return r.getCallId() != null || (snap != null && "Y".equals(snap.path("aiGenerated").asString("")));
    }

    private void signal(AbrAiReview r, String type)
    {
        if (StringUtils.isBlank(r.getAgentCode()))
        {
            return;
        }
        JsonNode snap = AiJson.parseOrNull(r.getContent());
        AbrGrowthSignal s = new AbrGrowthSignal();
        s.setSignalType(type);
        s.setAgentCode(r.getAgentCode());
        s.setAccountCode(snap == null ? null : StringUtils.trimToNull(snap.path("accountCode").asString("")));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("review_id", r.getReviewId());
        detail.put("biz_type", r.getBizType());
        detail.put("biz_id", r.getBizId());
        s.setDetail(MAPPER.writeValueAsString(detail));
        guardService.reportSignal(s);
    }

    private void close(String queueType, String bizId, String status, String reason, String operator)
    {
        reviewMapper.closeOpenByBiz(queueType, BIZ_CONTENT, bizId, status, StringUtils.substring(reason, 0, 500), StringUtils.defaultString(operator));
    }

    /**
     * 写处理结果；已被事件监听关闭的（放行内容时）不再改写
     */
    private void finish(AbrAiReview r, String status, String reason, String operator)
    {
        AbrAiReview change = new AbrAiReview();
        change.setReviewId(r.getReviewId());
        change.setStatus(status);
        change.setReason(reason);
        change.setReviewer(operator);
        reviewMapper.updateAbrAiReviewResult(change, OPEN);
    }

    private AbrAiReview requireOpen(Long reviewId, String queueType)
    {
        AbrAiReview r = selectExceptionById(reviewId);
        if (r == null)
        {
            throw new ServiceException("记录不存在");
        }
        if (!queueType.equals(r.getQueueType()))
        {
            throw new ServiceException(AiConstants.QUEUE_SAMPLE.equals(queueType) ? "这条记录不是抽检" : "这条记录不是例外");
        }
        if (!OPEN.contains(r.getStatus()))
        {
            throw new ServiceException("记录已处理，不能重复处理");
        }
        return r;
    }

    private static String requireReason(String reason)
    {
        String text = StringUtils.trimToEmpty(reason);
        if (text.isEmpty())
        {
            throw new ServiceException("请填写原因");
        }
        if (text.length() > 500)
        {
            throw new ServiceException("原因最多 500 个字符");
        }
        return text;
    }
}

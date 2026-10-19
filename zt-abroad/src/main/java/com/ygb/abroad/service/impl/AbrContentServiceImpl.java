package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrChannelAccount;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrGrowthSignal;
import com.ygb.abroad.domain.AbrPublishRecord;
import com.ygb.abroad.growth.OutboundDecision;
import com.ygb.abroad.mapper.AbrChannelAccountMapper;
import com.ygb.abroad.mapper.AbrContentMapper;
import com.ygb.abroad.mapper.AbrPublishRecordMapper;
import com.ygb.abroad.service.IAbrContentService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 内容 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrContentServiceImpl implements IAbrContentService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 生成内容编号与口令时的最多尝试次数 */
    private static final int MAX_TRIES = 20;

    @Autowired
    private AbrContentMapper contentMapper;

    @Autowired
    private AbrChannelAccountMapper accountMapper;

    @Autowired
    private AbrPublishRecordMapper recordMapper;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrEventService eventService;

    /** C 端地址（工具链接的前缀） */
    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    /** AI 生成标识文字（AI 生成的内容通过闸门前须在正文中带上） */
    @Value("${abroad.content.ai-label:本文由 AI 辅助生成}")
    private String aiLabel;

    @Override
    public List<AbrContent> selectAbrContentList(AbrContent content)
    {
        return contentMapper.selectAbrContentList(content);
    }

    @Override
    public AbrContent selectAbrContentById(Long contentId)
    {
        return contentMapper.selectAbrContentById(contentId);
    }

    @Override
    public AbrContent selectAbrContentByCodeword(String codeword)
    {
        String text = StringUtils.trimToNull(codeword);
        return text == null ? null : contentMapper.selectAbrContentByCodeword(text);
    }

    @Override
    public AbrContent selectAbrContentByCode(String contentCode)
    {
        String text = StringUtils.trimToNull(contentCode);
        return text == null ? null : contentMapper.selectAbrContentByCode(text);
    }

    @Override
    public AbrContent insertAbrContent(AbrContent content)
    {
        content.setAiGenerated("N");
        content.setAgentCode(null);
        content.setAiCallId(null);
        content.setRequiresConfirm("N");
        return insert(content);
    }

    @Override
    public AbrContent insertAgentContent(AbrContent content, String agentCode, Long aiCallId)
    {
        OutboundDecision decision = guardService.outbound(agentCode, "content:" + content.getTitle());
        if (decision == OutboundDecision.BLOCKED)
        {
            throw new ServiceException("Agent " + agentCode + " 现在不能运行：" + guardService.agentBlockReason(agentCode));
        }
        content.setAiGenerated("Y");
        content.setAgentCode(agentCode);
        content.setAiCallId(aiCallId);
        content.setRequiresConfirm(decision == OutboundDecision.CONFIRM ? "Y" : "N");
        content.setCreateBy(agentCode);
        return insert(content);
    }

    private AbrContent insert(AbrContent content)
    {
        AbrChannelAccount account = requireAccount(content.getAccountId());
        normalize(content);
        String codeword = AbrContentRules.normalizeCodeword(content.getCodeword());
        if (codeword != null && contentMapper.existsCodeword(codeword, null))
        {
            throw new ServiceException("私信口令“" + codeword + "”已被其他内容使用");
        }
        for (int i = 0; i < MAX_TRIES; i++)
        {
            content.setContentCode(AbrContentRules.newContentCode());
            content.setCodeword(codeword != null ? codeword : unusedCodeword());
            content.setToolLink(AbrContentRules.toolLink(appUrl, content.getHookTool(), account.getAccountCode(), content.getContentCode()));
            try
            {
                contentMapper.insertAbrContent(content);
                requestGate(content.getContentId());
                return contentMapper.selectAbrContentById(content.getContentId());
            }
            catch (DuplicateKeyException e)
            {
                // 随机内容编号或口令与已有记录相同（极少见）：换一个再试；自定义口令冲突时直接提示
                if (codeword != null && contentMapper.existsCodeword(codeword, null))
                {
                    throw new ServiceException("私信口令“" + codeword + "”已被其他内容使用");
                }
            }
        }
        throw new ServiceException("生成内容编号失败，请重试");
    }

    @Override
    public int updateAbrContent(AbrContent content)
    {
        AbrContent before = require(content.getContentId());
        if (AbrContent.STATUS_PUBLISHED.equals(before.getStatus()) || AbrContent.STATUS_ARCHIVED.equals(before.getStatus()))
        {
            throw new ServiceException("已发布或已归档的内容不能修改（需要修改时新建一条内容）");
        }
        if (recordMapper.countActiveByContent(before.getContentId(), null) > 0)
        {
            throw new ServiceException("内容已安排发布，先撤回发布记录再修改");
        }
        AbrChannelAccount account = requireAccount(content.getAccountId());
        normalize(content);
        String codeword = AbrContentRules.normalizeCodeword(content.getCodeword());
        content.setCodeword(codeword != null ? codeword : before.getCodeword());
        if (contentMapper.existsCodeword(content.getCodeword(), before.getContentId()))
        {
            throw new ServiceException("私信口令“" + content.getCodeword() + "”已被其他内容使用");
        }
        content.setContentCode(before.getContentCode());
        content.setToolLink(AbrContentRules.toolLink(appUrl, content.getHookTool(), account.getAccountCode(), before.getContentCode()));
        if (AbrContentRules.needsRegate(before, content))
        {
            content.setGateStatus(AbrContent.GATE_PENDING);
            content.setGateResult(null);
            content.setGateBy("");
            content.setGateTime(null);
            content.setConfirmBy("");
            content.setConfirmTime(null);
            content.setStatus(AbrContent.STATUS_DRAFT);
        }
        else
        {
            content.setGateStatus(before.getGateStatus());
            content.setGateResult(before.getGateResult());
            content.setGateBy(before.getGateBy());
            content.setGateTime(before.getGateTime());
            content.setConfirmBy(before.getConfirmBy());
            content.setConfirmTime(before.getConfirmTime());
            content.setStatus(before.getStatus());
        }
        int rows = contentMapper.updateAbrContent(content);
        if (rows > 0 && AbrContent.GATE_PENDING.equals(content.getGateStatus()))
        {
            requestGate(content.getContentId());
        }
        return rows;
    }

    /**
     * 写入“内容待过合规闸门”事件，由编排器交给 compliance_checker（M6-03）
     */
    private void requestGate(Long contentId)
    {
        eventService.recordSystemEvent(AbrEvent.TYPE_CONTENT_GATE_REQUESTED, null, Map.of("content_id", contentId));
    }

    @Override
    public AbrContent recordGateResult(Long contentId, boolean passed, String resultJson, String checker)
    {
        AbrContent c = requireGateable(contentId);
        String result = AbrJsonFields.requireObject(resultJson, "闸门明细");
        writeGate(c, passed ? AbrContent.GATE_PASSED : AbrContent.GATE_BLOCKED, result, checker);
        if (StringUtils.isNotBlank(c.getAgentCode()))
        {
            AbrGrowthSignal s = new AbrGrowthSignal();
            s.setSignalType(passed ? AbrGrowthSignal.TYPE_COMPLIANCE_PASS : AbrGrowthSignal.TYPE_COMPLIANCE_BLOCK);
            s.setAgentCode(c.getAgentCode());
            s.setAccountCode(c.getAccountCode());
            s.setDetail("{\"content_id\":" + c.getContentId() + "}");
            guardService.reportSignal(s);
        }
        return contentMapper.selectAbrContentById(contentId);
    }

    @Override
    public AbrContent reviewGate(Long contentId, boolean passed, String note, String operator)
    {
        AbrContent c = requireGateable(contentId);
        String text = StringUtils.trimToEmpty(note);
        if (!passed && text.isEmpty())
        {
            throw new ServiceException("拦截时请填写原因");
        }
        if (text.length() > 500)
        {
            throw new ServiceException("审核意见最多 500 个字符");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("manual", true);
        result.put("note", text);
        writeGate(c, passed ? AbrContent.GATE_MANUAL_PASSED : AbrContent.GATE_BLOCKED, MAPPER.writeValueAsString(result), operator);
        return contentMapper.selectAbrContentById(contentId);
    }

    @Override
    public boolean confirmContent(Long contentId, String operator)
    {
        require(contentId);
        return contentMapper.confirmContent(contentId, operator) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeContents(Long[] contentIds, String operator)
    {
        int deleted = contentMapper.deleteUnpublishedContentByIds(contentIds);
        int archived = 0;
        for (Long id : contentIds)
        {
            AbrContent c = contentMapper.selectAbrContentById(id);
            if (c == null || AbrContent.STATUS_ARCHIVED.equals(c.getStatus()))
            {
                continue;
            }
            if (recordMapper.countActiveByContent(id, AbrPublishRecord.STATUS_SCHEDULED) > 0)
            {
                throw new ServiceException("内容“" + c.getTitle() + "”还有待发布的记录，先撤回再归档");
            }
            archived += contentMapper.updateContentStatus(id, AbrContent.STATUS_ARCHIVED, operator);
        }
        return deleted + archived;
    }

    /**
     * 写闸门结果：通过前检查 AI 生成标识；状态按是否须人工确认计算
     */
    private void writeGate(AbrContent c, String gateStatus, String result, String checker)
    {
        boolean passed = !AbrContent.GATE_BLOCKED.equals(gateStatus);
        String missing = passed ? AbrContentRules.missingAiLabel(c, aiLabel) : null;
        if (missing != null)
        {
            throw new ServiceException(missing);
        }
        AbrContent update = new AbrContent();
        update.setContentId(c.getContentId());
        update.setGateStatus(gateStatus);
        update.setGateResult(result);
        update.setGateBy(checker);
        update.setStatus(AbrContentRules.statusAfterGate(gateStatus, "Y".equals(c.getRequiresConfirm())));
        contentMapper.updateContentGate(update);
    }

    private AbrContent requireGateable(Long contentId)
    {
        AbrContent c = require(contentId);
        if (AbrContent.STATUS_PUBLISHED.equals(c.getStatus()) || AbrContent.STATUS_ARCHIVED.equals(c.getStatus()))
        {
            throw new ServiceException("已发布或已归档的内容不能再过闸门");
        }
        return c;
    }

    private void normalize(AbrContent c)
    {
        AbrContentRules.normalize(c, AbrPlaybookServiceImpl.dictValues("abr_content_kind"), AbrPlaybookServiceImpl.dictValues("abr_degree"),
                AbrPlaybookServiceImpl.dictValues("abr_country"), AbrPlaybookServiceImpl.dictValues("abr_hook_tool"));
    }

    private String unusedCodeword()
    {
        for (int i = 0; i < MAX_TRIES; i++)
        {
            String w = AbrContentRules.newCodeword();
            if (!contentMapper.existsCodeword(w, null))
            {
                return w;
            }
        }
        throw new ServiceException("可用的 4 位数字口令不足，请为这条内容填写自定义口令");
    }

    private AbrContent require(Long contentId)
    {
        AbrContent c = contentId == null ? null : contentMapper.selectAbrContentById(contentId);
        if (c == null)
        {
            throw new ServiceException("内容不存在");
        }
        return c;
    }

    private AbrChannelAccount requireAccount(Long accountId)
    {
        AbrChannelAccount a = accountId == null ? null : accountMapper.selectAbrChannelAccountById(accountId);
        if (a == null)
        {
            throw new ServiceException("发布账号不存在");
        }
        return a;
    }
}

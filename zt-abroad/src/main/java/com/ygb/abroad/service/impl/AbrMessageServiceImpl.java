package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiConstants;
import com.ygb.abroad.domain.AbrMessageSend;
import com.ygb.abroad.domain.AbrMessageTemplate;
import com.ygb.abroad.domain.AbrTouchLog;
import com.ygb.abroad.growth.TouchDecision;
import com.ygb.abroad.growth.TouchRequest;
import com.ygb.abroad.growth.compliance.ComplianceChecker;
import com.ygb.abroad.integration.channel.ChannelAdapter;
import com.ygb.abroad.integration.channel.ChannelMask;
import com.ygb.abroad.integration.channel.ChannelSendResult;
import com.ygb.abroad.integration.channel.OutboundMessage;
import com.ygb.abroad.mapper.AbrMessageMapper;
import com.ygb.abroad.service.IAbrComplianceService;
import com.ygb.abroad.service.IAbrExceptionService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrMessageService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 消息发送服务层实现
 *
 * @author ygb
 */
@Service
public class AbrMessageServiceImpl implements IAbrMessageService
{
    private static final Logger log = LoggerFactory.getLogger(AbrMessageServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 员工提醒的业务编码 */
    static final String STAFF_ALERT = "staff_alert";

    static final String TARGET_APP_USER = "app_user";

    static final String TARGET_LEAD = "lead";

    static final String TARGET_VISITOR = "visitor";

    static final String STATUS_BLOCKED = "blocked";

    static final String STATUS_DEFERRED = "deferred";

    static final String STATUS_NO_CHANNEL = "no_channel";

    @Autowired
    private AbrMessageMapper messageMapper;

    @Autowired
    private List<ChannelAdapter> adapters;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrComplianceService complianceService;

    @Autowired
    private IAbrExceptionService exceptionService;

    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    @Value("${abroad.channel.mock:false}")
    private boolean mock;

    /**
     * 学生的接收地址
     *
     * @param oaOpenid 服务号 openid
     * @param miniOpenid 小程序 openid
     * @param phone 手机号
     */
    record Address(String oaOpenid, String miniOpenid, String phone)
    {
        String of(String channel)
        {
            return switch (channel)
            {
                case ChannelAdapter.SERVICE_ACCOUNT -> oaOpenid;
                case ChannelAdapter.MP_SUBSCRIBE -> miniOpenid;
                case ChannelAdapter.SMS -> phone;
                default -> null;
            };
        }
    }

    @Override
    public Result sendToStudent(String templateCode, String targetType, Long targetId, Map<String, ?> vars, String agentCode, Long taskId,
            boolean aiGenerated)
    {
        AbrMessageSend rec = record(templateCode, targetType, targetId, agentCode, taskId, "system");
        List<AbrMessageTemplate> templates = messageMapper.selectEnabledByCode(templateCode);
        if (templates.isEmpty())
        {
            return finish(rec, STATUS_NO_CHANNEL, "没有启用的模板：" + templateCode);
        }
        Address addr = address(targetType, targetId);
        AbrMessageTemplate picked = null;
        boolean anyOff = false;
        for (String channel : AbrMessageRules.STUDENT_ORDER)
        {
            AbrMessageTemplate t = templates.stream().filter(x -> channel.equals(x.getChannel())).findFirst().orElse(null);
            if (t == null || StringUtils.isBlank(addr.of(channel)))
            {
                continue;
            }
            if (!adapter(channel).configured())
            {
                anyOff = true;
                continue;
            }
            picked = t;
            break;
        }
        if (picked == null)
        {
            List<String> names = templates.stream().map(AbrMessageTemplate::getChannel).filter(AbrMessageRules.STUDENT_ORDER::contains).distinct()
                    .map(AbrMessageRules::channelName).toList();
            return finish(rec, anyOff ? ChannelSendResult.OFF : STATUS_NO_CHANNEL, anyOff ? "可达的渠道尚未开通"
                    : names.isEmpty() ? "没有学生可用渠道的模板" : "学生在启用了模板的渠道（" + String.join("、", names) + "）上不可达");
        }
        rec.setChannel(picked.getChannel());
        rec.setCategory(picked.getCategory());
        String recipient = addr.of(picked.getChannel());
        rec.setRecipient(ChannelMask.recipient(recipient));
        TouchRequest touch = new TouchRequest(agentCode, targetType, targetId, picked.getChannel(), picked.getCategory(), taskId, templateCode);
        TouchDecision d = guardService.checkTouch(touch);
        if (d.kind() == TouchDecision.Kind.DENY)
        {
            return finish(rec, STATUS_BLOCKED, d.reason());
        }
        if (d.kind() == TouchDecision.Kind.DEFER)
        {
            Result r = finish(rec, STATUS_DEFERRED, d.reason());
            return new Result(r.status(), r.channel(), r.sendId(), r.reason(), d.deferUntil());
        }
        OutboundMessage msg;
        try
        {
            msg = render(picked, recipient, vars);
        }
        catch (ServiceException e)
        {
            guardService.recordTouch(touch, AbrTouchLog.STATUS_FAILED, e.getMessage());
            return finish(rec, ChannelSendResult.FAILED, e.getMessage());
        }
        rec.setRendered(StringUtils.substring(msg.text(), 0, 1000));
        if (AbrTouchLog.CATEGORY_MARKETING.equals(picked.getCategory()))
        {
            ComplianceChecker.Result check = complianceService.checkMessage(picked.getChannel(), msg.text(), null, aiGenerated);
            if (!check.passed())
            {
                guardService.recordTouch(touch, AbrTouchLog.STATUS_BLOCKED, "合规闸门拦截");
                Result r = finish(rec, STATUS_BLOCKED, "合规闸门拦截");
                enqueueBlocked(r.sendId(), agentCode, picked, msg.text(), check);
                return r;
            }
        }
        ChannelSendResult sent = adapter(picked.getChannel()).send(msg);
        guardService.recordTouch(touch, ChannelSendResult.FAILED.equals(sent.status()) ? AbrTouchLog.STATUS_FAILED : AbrTouchLog.STATUS_SENT,
                StringUtils.substring(sent.error(), 0, 200));
        rec.setPlatformMsgId(sent.platformMsgId());
        return finish(rec, sent.status(), sent.error());
    }

    @Override
    public Result sendToStaff(Long userId, String title, String content)
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("title", StringUtils.defaultString(title));
        vars.put("content", StringUtils.defaultString(content));
        AbrMessageSend rec = record(STAFF_ALERT, TARGET_STAFF, userId, "", null, "system");
        rec.setChannel(ChannelAdapter.WECOM);
        rec.setCategory(AbrTouchLog.CATEGORY_SERVICE);
        AbrMessageTemplate t = messageMapper.selectEnabledByCode(STAFF_ALERT).stream().filter(x -> ChannelAdapter.WECOM.equals(x.getChannel())).findFirst()
                .orElse(null);
        if (t == null)
        {
            return finish(rec, ChannelSendResult.OFF, "员工提醒模板 staff_alert（企业微信）未启用");
        }
        return deliver(rec, t, messageMapper.selectStaffWecomAccount(userId), vars);
    }

    @Override
    public Result testSend(Long templateId, String targetType, Long targetId, Map<String, ?> vars, String operator)
    {
        AbrMessageTemplate t = messageMapper.selectTemplateById(templateId);
        if (t == null)
        {
            throw new ServiceException("模板不存在");
        }
        AbrMessageSend rec = record(t.getTemplateCode(), targetType, targetId, "manual", null, operator);
        rec.setChannel(t.getChannel());
        rec.setCategory(t.getCategory());
        String recipient;
        if (TARGET_STAFF.equals(targetType))
        {
            if (!ChannelAdapter.WECOM.equals(t.getChannel()))
            {
                throw new ServiceException("发给员工只能用企业微信模板");
            }
            recipient = messageMapper.selectStaffWecomAccount(targetId);
        }
        else
        {
            if (ChannelAdapter.WECOM.equals(t.getChannel()))
            {
                throw new ServiceException("企业微信模板只能发给员工");
            }
            recipient = address(targetType, targetId).of(t.getChannel());
        }
        return deliver(rec, t, recipient, vars);
    }

    /**
     * 不经触达判断直接发出（员工提醒、测试发送）
     */
    private Result deliver(AbrMessageSend rec, AbrMessageTemplate t, String recipient, Map<String, ?> vars)
    {
        if (StringUtils.isBlank(recipient))
        {
            return finish(rec, STATUS_NO_CHANNEL, "对象在这个渠道没有可用的接收方");
        }
        rec.setRecipient(ChannelMask.recipient(recipient));
        OutboundMessage msg;
        try
        {
            msg = render(t, recipient, vars);
        }
        catch (ServiceException e)
        {
            return finish(rec, ChannelSendResult.FAILED, e.getMessage());
        }
        rec.setRendered(StringUtils.substring(msg.text(), 0, 1000));
        ChannelSendResult sent = adapter(t.getChannel()).send(msg);
        rec.setPlatformMsgId(sent.platformMsgId());
        return finish(rec, sent.status(), sent.error());
    }

    private OutboundMessage render(AbrMessageTemplate t, String recipient, Map<String, ?> vars)
    {
        Map<String, Object> all = new HashMap<>();
        all.put("appUrl", appUrl);
        if (vars != null)
        {
            all.putAll(vars);
        }
        Map<String, String> fields = AbrMessageRules.renderFields(AbrMessageRules.fieldMap(t.getFieldMap()), all);
        String text = AbrMessageRules.render(t.getContent(), all);
        if (StringUtils.isBlank(text))
        {
            text = String.join(" ", fields.values());
        }
        return new OutboundMessage(recipient, t.getPlatformTemplateId(), fields, text, AbrMessageRules.render(t.getLink(), all));
    }

    /**
     * 学生的接收地址：学生账号（直接、线索关联、访客关联）的服务号 / 小程序 openid 与手机号，线索没有学生账号时用线索的手机号；停用的账号不发
     */
    Address address(String targetType, Long targetId)
    {
        Long appUserId = null;
        String phone = null;
        if (targetId == null)
        {
            return new Address(null, null, null);
        }
        switch (targetType)
        {
            case TARGET_APP_USER -> appUserId = targetId;
            case TARGET_LEAD -> {
                Map<String, Object> l = messageMapper.selectLeadAddress(targetId);
                if (l != null)
                {
                    appUserId = l.get("appUserId") instanceof Number n ? n.longValue() : null;
                    phone = (String) l.get("phone");
                }
            }
            case TARGET_VISITOR -> appUserId = messageMapper.selectVisitorAppUser(targetId);
            default -> {
                return new Address(null, null, null);
            }
        }
        if (appUserId == null)
        {
            return new Address(null, null, StringUtils.trimToNull(phone));
        }
        Map<String, Object> u = messageMapper.selectAppUserAddress(appUserId);
        if (u == null || !"0".equals(String.valueOf(u.get("status")).trim()))
        {
            return new Address(null, null, StringUtils.trimToNull(phone));
        }
        String p = StringUtils.isNotBlank((String) u.get("phone")) ? (String) u.get("phone") : phone;
        return new Address(StringUtils.trimToNull((String) u.get("oaOpenid")), StringUtils.trimToNull((String) u.get("miniOpenid")), StringUtils.trimToNull(p));
    }

    private void enqueueBlocked(Long sendId, String agentCode, AbrMessageTemplate t, String text, ComplianceChecker.Result check)
    {
        try
        {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("title", t.getTemplateName());
            snap.put("body", text);
            snap.put("platform", t.getChannel());
            snap.put("templateCode", t.getTemplateCode());
            snap.put("gate", check.toMap());
            exceptionService.enqueue(AiConstants.QUEUE_EXCEPTION, IAbrExceptionService.REASON_MESSAGE_BLOCKED, StringUtils.trimToNull(agentCode), "message",
                    String.valueOf(sendId), null, MAPPER.writeValueAsString(snap), MAPPER.writeValueAsString(check.blocked()), null);
        }
        catch (RuntimeException e)
        {
            log.warn("消息 {} 被合规闸门拦截，写入例外队列失败：{}", sendId, e.getMessage());
        }
    }

    private ChannelAdapter adapter(String channel)
    {
        return adapters.stream().filter(a -> a.channel().equals(channel)).findFirst().orElseThrow(() -> new ServiceException("未知渠道：" + channel));
    }

    private static AbrMessageSend record(String templateCode, String targetType, Long targetId, String agentCode, Long taskId, String operator)
    {
        AbrMessageSend s = new AbrMessageSend();
        s.setTemplateCode(templateCode);
        s.setTargetType(targetType);
        s.setTargetId(targetId);
        s.setAgentCode(StringUtils.defaultString(agentCode));
        s.setTaskId(taskId);
        s.setCreateBy(operator);
        return s;
    }

    private Result finish(AbrMessageSend rec, String status, String reason)
    {
        rec.setStatus(status);
        rec.setReason(StringUtils.substring(StringUtils.defaultString(reason), 0, 500));
        messageMapper.insertSend(rec);
        return new Result(status, StringUtils.defaultString(rec.getChannel()), rec.getSendId(), rec.getReason(), null);
    }

    @Override
    public List<Map<String, Object>> channelStatus()
    {
        Map<String, Long> enabled = new HashMap<>();
        AbrMessageTemplate q = new AbrMessageTemplate();
        q.setStatus("0");
        messageMapper.selectTemplateList(q).forEach(t -> enabled.merge(t.getChannel(), 1L, Long::sum));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String c : List.of(ChannelAdapter.SERVICE_ACCOUNT, ChannelAdapter.MP_SUBSCRIBE, ChannelAdapter.WECOM, ChannelAdapter.SMS))
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", c);
            m.put("configured", adapter(c).configured());
            m.put("mock", mock);
            m.put("enabledTemplates", enabled.getOrDefault(c, 0L));
            out.add(m);
        }
        return out;
    }

    @Override
    public List<AbrMessageTemplate> selectTemplateList(AbrMessageTemplate template)
    {
        return messageMapper.selectTemplateList(template);
    }

    @Override
    public AbrMessageTemplate selectTemplateById(Long templateId)
    {
        return messageMapper.selectTemplateById(templateId);
    }

    @Override
    public int saveTemplate(AbrMessageTemplate template)
    {
        AbrMessageRules.validate(template);
        if (messageMapper.countTemplate(template.getTemplateCode(), template.getChannel(), template.getTemplateId()) > 0)
        {
            throw new ServiceException("业务编码 " + template.getTemplateCode() + " 在这个渠道已有模板");
        }
        return template.getTemplateId() == null ? messageMapper.insertTemplate(template) : messageMapper.updateTemplate(template);
    }

    @Override
    public int deleteTemplates(Long[] templateIds)
    {
        return messageMapper.deleteTemplateByIds(templateIds);
    }

    @Override
    public List<AbrMessageSend> selectSendList(AbrMessageSend send)
    {
        return messageMapper.selectSendList(send);
    }
}

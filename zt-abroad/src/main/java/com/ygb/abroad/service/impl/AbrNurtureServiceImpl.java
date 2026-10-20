package com.ygb.abroad.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrNurtureEnrollment;
import com.ygb.abroad.domain.AbrNurtureSequence;
import com.ygb.abroad.domain.AbrNurtureStep;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppCalendarVo;
import com.ygb.abroad.growth.OutboundDecision;
import com.ygb.abroad.mapper.AbrNurtureMapper;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrMessageService;
import com.ygb.abroad.service.IAbrNurtureService;
import com.ygb.abroad.service.IAbrPlanService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;

/**
 * 培育序列 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrNurtureServiceImpl implements IAbrNurtureService
{
    static final String ACTIVE = "active";

    static final String WAITING = "waiting_confirm";

    static final String COMPLETED = "completed";

    static final String EXITED = "exited";

    /** 每轮最多推进的入组数 */
    static final int BATCH = 200;

    @Autowired
    private AbrNurtureMapper nurtureMapper;

    @Autowired
    private IAbrMessageService messageService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrPlanService planService;

    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    /** 一步的处理结果（统计用） */
    enum Outcome
    {
        SENT, SKIPPED, WAITING, DEFERRED, EXITED, COMPLETED, RETRY
    }

    @Override
    public Long enroll(String sequenceCode, String targetType, Long targetId)
    {
        AbrNurtureSequence s = sequenceCode == null ? null : nurtureMapper.selectSequenceByCode(sequenceCode);
        if (s == null || !"0".equals(s.getStatus()) || targetId == null)
        {
            return null;
        }
        AbrNurtureStep first = AbrNurtureRules.nextStep(nurtureMapper.selectSteps(s.getSequenceId()), 1);
        if (first == null)
        {
            return null;
        }
        Date now = DateUtils.getNowDate();
        AbrNurtureEnrollment e = new AbrNurtureEnrollment();
        e.setSequenceId(s.getSequenceId());
        e.setTargetType(targetType);
        e.setTargetId(targetId);
        e.setNextStepNo(first.getStepNo());
        e.setNextDueTime(AbrNurtureRules.dueTime(now, first.getDelayDays(), first.getSendHour(), now));
        e.setStatus(ACTIVE);
        e.setEnrolledTime(now);
        return nurtureMapper.insertEnrollment(e) > 0 ? e.getEnrollmentId() : null;
    }

    @Override
    public Map<String, Object> runDue(Set<String> activePlaybooks, Long taskId)
    {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<AbrNurtureEnrollment> due = nurtureMapper.selectDueEnrollments(DateUtils.getNowDate(), BATCH);
        stats.put("due", due.size());
        Map<Long, AbrNurtureSequence> sequences = new HashMap<>();
        for (AbrNurtureEnrollment e : due)
        {
            AbrNurtureSequence s = sequences.computeIfAbsent(e.getSequenceId(), this::sequenceWithSteps);
            Outcome o = process(e, s, activePlaybooks, false, taskId);
            stats.merge(o.name().toLowerCase(), 1, (a, b) -> (Integer) a + (Integer) b);
        }
        return stats;
    }

    @Override
    public AbrNurtureEnrollment confirm(Long enrollmentId)
    {
        AbrNurtureEnrollment e = require(enrollmentId);
        if (!WAITING.equals(e.getStatus()))
        {
            throw new ServiceException("只有待确认的入组可以确认发送");
        }
        process(e, sequenceWithSteps(e.getSequenceId()), null, true, null);
        return nurtureMapper.selectEnrollmentById(enrollmentId);
    }

    @Override
    public AbrNurtureEnrollment skip(Long enrollmentId)
    {
        AbrNurtureEnrollment e = require(enrollmentId);
        if (!WAITING.equals(e.getStatus()) && !ACTIVE.equals(e.getStatus()))
        {
            throw new ServiceException("入组已结束");
        }
        AbrNurtureSequence s = sequenceWithSteps(e.getSequenceId());
        AbrNurtureStep step = AbrNurtureRules.nextStep(s.getSteps(), e.getNextStepNo());
        advance(e, s, step, "员工跳过：" + (step == null ? "" : step.getStepName()), false);
        return nurtureMapper.selectEnrollmentById(enrollmentId);
    }

    @Override
    public AbrNurtureEnrollment exit(Long enrollmentId, String reason)
    {
        AbrNurtureEnrollment e = require(enrollmentId);
        if (COMPLETED.equals(e.getStatus()) || EXITED.equals(e.getStatus()))
        {
            throw new ServiceException("入组已结束");
        }
        finish(e, EXITED, "员工退出：" + StringUtils.defaultIfBlank(StringUtils.trim(reason), "无"), null);
        return nurtureMapper.selectEnrollmentById(enrollmentId);
    }

    /**
     * 处理一条到期（或待确认）的入组
     *
     * @param confirmed 员工已确认（不看执行模式与剧本限制）
     */
    Outcome process(AbrNurtureEnrollment e, AbrNurtureSequence s, Set<String> activePlaybooks, boolean confirmed, Long taskId)
    {
        Date now = DateUtils.getNowDate();
        Map<String, Object> facts = nurtureMapper.selectTargetFacts(e.getTargetType(), e.getTargetId());
        if (facts == null)
        {
            return finish(e, EXITED, "对象不存在", null);
        }
        String exit = AbrNurtureRules.exitReason((String) facts.get("stage"), number(facts.get("activeBookings")), (Date) facts.get("lastActivity"),
                e.getEnrolledTime(), s.getInactiveDays() == null ? 0 : s.getInactiveDays(), now);
        if (exit != null)
        {
            return finish(e, EXITED, exit, null);
        }
        AbrNurtureStep step = AbrNurtureRules.nextStep(s.getSteps(), e.getNextStepNo());
        if (step == null)
        {
            return finish(e, COMPLETED, "全部步骤已完成", null);
        }
        if (!confirmed && activePlaybooks != null && !AbrNurtureRules.playbookAllowed(step.getPlaybookCodes(), activePlaybooks))
        {
            return advance(e, s, step, "跳过「" + step.getStepName() + "」：限定的剧本未生效", false);
        }
        Long visitorId = facts.get("visitorId") instanceof Number n ? n.longValue() : null;
        Long appUserId = facts.get("appUserId") instanceof Number n ? n.longValue() : null;
        Map<String, Object> vars = vars(visitorId, appUserId);
        String unmet = unmet(step.getConditionCode(), vars);
        if (unmet != null)
        {
            return advance(e, s, step, "跳过「" + step.getStepName() + "」：" + unmet, false);
        }
        if (!confirmed)
        {
            OutboundDecision d = guardService.outbound(AGENT, e.getTargetType() + ":" + e.getTargetId());
            if (d == OutboundDecision.BLOCKED)
            {
                return later(e, new Date(now.getTime() + 3600_000L), "nurture_agent 暂停、停用或超出预算，稍后再试", Outcome.RETRY);
            }
            if (d == OutboundDecision.CONFIRM)
            {
                e.setStatus(WAITING);
                e.setLastResult("待人工确认：「" + step.getStepName() + "」");
                nurtureMapper.updateEnrollment(e);
                return Outcome.WAITING;
            }
        }
        IAbrMessageService.Result r = messageService.sendToStudent(step.getTemplateCode(), e.getTargetType(), e.getTargetId(), vars, AGENT, taskId, false,
                citations(vars));
        String label = "「" + step.getStepName() + "」";
        if (r.delivered())
        {
            return advance(e, s, step, label + "已发出（" + r.channel() + "）", true);
        }
        if ("deferred".equals(r.status()))
        {
            return later(e, r.deferUntil(), label + "推迟：" + r.reason(), Outcome.DEFERRED);
        }
        if ("blocked".equals(r.status()))
        {
            return switch (AbrNurtureRules.onBlocked(r.reason()))
            {
                case RETRY_TOMORROW -> later(e, new Date(now.getTime() + 86_400_000L), label + "被拦下：" + r.reason() + "，明天再试", Outcome.DEFERRED);
                case RETRY_LATER -> later(e, new Date(now.getTime() + 3600_000L), label + "被拦下：" + r.reason(), Outcome.RETRY);
                case EXIT -> finish(e, EXITED, label + "被拦下：" + r.reason(), null);
            };
        }
        // 渠道未开通、没有可达渠道、模板缺变量、发送失败：跳过这一步
        return advance(e, s, step, label + "未发出（" + r.status() + "）：" + r.reason(), false);
    }

    /**
     * 不满足的条件（满足为 null）
     */
    private static String unmet(String condition, Map<String, Object> vars)
    {
        return switch (StringUtils.defaultString(condition, AbrNurtureRules.COND_NONE))
        {
            case AbrNurtureRules.COND_HAS_PLAN -> number(vars.get("tieredCount")) > 0 ? null : "选校单没有已分档的项目";
            case AbrNurtureRules.COND_HAS_DEADLINE -> vars.containsKey("deadline") ? null : AbrNurtureRules.DEADLINE_DAYS + " 天内没有截止";
            default -> null;
        };
    }

    /**
     * 消息变量：只取引擎分档（选校单档位快照）与院校库截止日
     */
    Map<String, Object> vars(Long visitorId, Long appUserId)
    {
        Map<String, Object> v = new HashMap<>();
        v.put("planUrl", appUrl + "pages/plan/index");
        long reach = 0;
        long match = 0;
        long safety = 0;
        if (visitorId != null || appUserId != null)
        {
            for (Map<String, Object> t : nurtureMapper.selectPlanTierCounts(visitorId, appUserId))
            {
                long c = number(t.get("count"));
                switch (StringUtils.defaultString((String) t.get("tier")))
                {
                    case "reach" -> reach += c;
                    case "match" -> match += c;
                    case "safety" -> safety += c;
                    default -> {
                    }
                }
            }
        }
        v.put("reachCount", reach);
        v.put("matchCount", match);
        v.put("safetyCount", safety);
        v.put("tieredCount", reach + match + safety);
        if (visitorId != null)
        {
            AbrVisitor visitor = new AbrVisitor();
            visitor.setVisitorId(visitorId);
            AppCalendarVo cal = planService.selectCalendar(visitor);
            cal.events().stream().filter(ev -> !ev.passed() && ev.date() != null && ev.daysLeft() >= 0 && ev.daysLeft() <= AbrNurtureRules.DEADLINE_DAYS)
                    .findFirst().ifPresent(ev -> {
                        v.put("programId", ev.programId());
                        v.put("programName", StringUtils.substring(ev.schoolName() + " " + ev.programName(), 0, 20));
                        v.put("deadline", ev.date());
                        v.put("daysLeft", ev.daysLeft());
                    });
        }
        return v;
    }

    /**
     * 引用依据：文字中提到的截止日取自哪个项目（合规闸门的事实核对用）
     */
    private static String citations(Map<String, Object> vars)
    {
        return vars.get("programId") instanceof Number id ? "[{\"type\": \"program\", \"id\": " + id.longValue() + "}]" : null;
    }

    /**
     * 走到下一步（没有了则完成）
     */
    private Outcome advance(AbrNurtureEnrollment e, AbrNurtureSequence s, AbrNurtureStep done, String result, boolean sent)
    {
        Date now = DateUtils.getNowDate();
        AbrNurtureStep next = done == null ? null : AbrNurtureRules.nextStep(s.getSteps(), done.getStepNo() + 1);
        if (next == null)
        {
            finish(e, COMPLETED, result, sent ? now : null);
            return sent ? Outcome.SENT : Outcome.COMPLETED;
        }
        e.setStatus(ACTIVE);
        e.setNextStepNo(next.getStepNo());
        e.setNextDueTime(AbrNurtureRules.dueTime(e.getEnrolledTime(), next.getDelayDays(), next.getSendHour(), now));
        e.setLastResult(StringUtils.substring(result, 0, 500));
        e.setLastSentTime(sent ? now : null);
        nurtureMapper.updateEnrollment(e);
        return sent ? Outcome.SENT : Outcome.SKIPPED;
    }

    private Outcome later(AbrNurtureEnrollment e, Date due, String result, Outcome outcome)
    {
        e.setStatus(ACTIVE);
        e.setNextDueTime(due);
        e.setLastResult(StringUtils.substring(result, 0, 500));
        e.setLastSentTime(null);
        nurtureMapper.updateEnrollment(e);
        return outcome;
    }

    private Outcome finish(AbrNurtureEnrollment e, String status, String reason, Date sentTime)
    {
        e.setStatus(status);
        e.setNextDueTime(null);
        if (EXITED.equals(status))
        {
            e.setExitReason(StringUtils.substring(reason, 0, 200));
        }
        e.setLastResult(StringUtils.substring(reason, 0, 500));
        e.setLastSentTime(sentTime);
        nurtureMapper.updateEnrollment(e);
        return EXITED.equals(status) ? Outcome.EXITED : Outcome.COMPLETED;
    }

    private AbrNurtureSequence sequenceWithSteps(Long sequenceId)
    {
        AbrNurtureSequence s = nurtureMapper.selectSequenceById(sequenceId);
        if (s == null)
        {
            throw new ServiceException("序列不存在");
        }
        s.setSteps(nurtureMapper.selectSteps(sequenceId));
        return s;
    }

    private AbrNurtureEnrollment require(Long enrollmentId)
    {
        AbrNurtureEnrollment e = enrollmentId == null ? null : nurtureMapper.selectEnrollmentById(enrollmentId);
        if (e == null)
        {
            throw new ServiceException("入组不存在");
        }
        return e;
    }

    private static long number(Object o)
    {
        return o instanceof Number n ? n.longValue() : 0;
    }

    @Override
    public List<AbrNurtureSequence> selectSequenceList(AbrNurtureSequence sequence)
    {
        return nurtureMapper.selectSequenceList(sequence);
    }

    @Override
    public AbrNurtureSequence selectSequenceById(Long sequenceId)
    {
        return sequenceWithSteps(sequenceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSequence(AbrNurtureSequence sequence)
    {
        AbrNurtureRules.validate(sequence);
        if (nurtureMapper.countSequenceCode(sequence.getSequenceCode(), sequence.getSequenceId()) > 0)
        {
            throw new ServiceException("序列编码 " + sequence.getSequenceCode() + " 已被使用");
        }
        if (sequence.getSequenceId() == null)
        {
            nurtureMapper.insertSequence(sequence);
        }
        else if (nurtureMapper.updateSequence(sequence) == 0)
        {
            throw new ServiceException("序列不存在");
        }
        nurtureMapper.deleteSteps(sequence.getSequenceId());
        for (AbrNurtureStep st : sequence.getSteps())
        {
            st.setSequenceId(sequence.getSequenceId());
            nurtureMapper.insertStep(st);
        }
        return sequence.getSequenceId();
    }

    @Override
    public int deleteSequence(Long sequenceId)
    {
        return nurtureMapper.deleteSequence(sequenceId);
    }

    @Override
    public List<AbrNurtureEnrollment> selectEnrollmentList(AbrNurtureEnrollment enrollment)
    {
        return nurtureMapper.selectEnrollmentList(enrollment);
    }
}

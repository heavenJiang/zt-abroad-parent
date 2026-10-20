package com.ygb.abroad.service.impl;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrNurtureSequence;
import com.ygb.abroad.domain.AbrNurtureStep;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 培育序列规则（纯计算）：步骤到期时间、下一步、退出条件、被拦下时的处理、序列校验
 *
 * @author ygb
 */
final class AbrNurtureRules
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 条件 */
    static final String COND_NONE = "none";

    static final String COND_HAS_PLAN = "has_plan";

    static final String COND_HAS_DEADLINE = "has_deadline";

    static final Set<String> CONDITIONS = Set.of(COND_NONE, COND_HAS_PLAN, COND_HAS_DEADLINE);

    /** “近期截止”的天数 */
    static final int DEADLINE_DAYS = 45;

    /** 已过首访、不再培育的线索阶段 */
    static final Set<String> EXIT_STAGES = Set.of("booked", "proposal", "quoting", "won", "lost");

    static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    /** 一个序列最多几步、最长几天 */
    static final int MAX_STEPS = 20;

    static final int MAX_DAYS = 365;

    /**
     * 被拦下时的处理
     */
    enum BlockAction
    {
        /** 明天再试（频控） */
        RETRY_TOMORROW,
        /** 一小时后再试（暂停、停用、预算） */
        RETRY_LATER,
        /** 退出序列（退订、未成年人、合规拦截等） */
        EXIT
    }

    private AbrNurtureRules()
    {
    }

    /**
     * 步骤到期时间：入组日 + 天数，指定了几点则在当天该时刻（北京时间），不早于 notBefore
     *
     * @param enrolled 入组时间
     * @param delayDays 第几天
     * @param sendHour 几点（null 为入组时刻 + 天数）
     * @param notBefore 不早于
     * @return 到期时间
     */
    static Date dueTime(Date enrolled, int delayDays, Integer sendHour, Date notBefore)
    {
        ZonedDateTime base = enrolled.toInstant().atZone(MarketingCalendar.ZONE).plusDays(Math.max(0, delayDays));
        ZonedDateTime at = sendHour == null ? base : base.toLocalDate().atTime(Math.max(0, Math.min(23, sendHour)), 0).atZone(MarketingCalendar.ZONE);
        Date due = Date.from(at.toInstant());
        return notBefore != null && due.before(notBefore) ? notBefore : due;
    }

    /**
     * 从某一步开始的第一个启用的步骤
     *
     * @param steps 步骤（按顺序）
     * @param fromNo 从第几步起
     * @return 步骤（没有为 null）
     */
    static AbrNurtureStep nextStep(List<AbrNurtureStep> steps, int fromNo)
    {
        return steps.stream().filter(s -> s.getStepNo() >= fromNo && "0".equals(s.getStatus())).findFirst().orElse(null);
    }

    /**
     * 退出原因：线索阶段已过首访、有进行中的预约、超过无互动天数
     *
     * @param stage 线索阶段（非线索为 null）
     * @param activeBookings 进行中的预约数
     * @param lastActivity 最近互动（没有为 null，按入组时间算）
     * @param enrolled 入组时间
     * @param inactiveDays 无互动多少天退出（0 不按此退出）
     * @param now 现在
     * @return 原因（不退出为 null）
     */
    static String exitReason(String stage, long activeBookings, Date lastActivity, Date enrolled, int inactiveDays, Date now)
    {
        if (stage != null && EXIT_STAGES.contains(stage))
        {
            return "线索阶段已过首访（" + stage + "）";
        }
        if (activeBookings > 0)
        {
            return "已有进行中的预约";
        }
        Date last = lastActivity == null || (enrolled != null && lastActivity.before(enrolled)) ? enrolled : lastActivity;
        if (inactiveDays > 0 && last != null && now.getTime() - last.getTime() > inactiveDays * 86_400_000L)
        {
            return inactiveDays + " 天无互动，降级为社群与直播";
        }
        return null;
    }

    /**
     * 被触达判断或合规闸门拦下时怎么处理
     *
     * @param reason 拦下原因
     * @return 处理
     */
    static BlockAction onBlocked(String reason)
    {
        String r = StringUtils.defaultString(reason);
        if (r.startsWith("频控"))
        {
            return BlockAction.RETRY_TOMORROW;
        }
        if (r.contains("暂停") || r.contains("执行模式为停用") || r.contains("预算") || r.contains("未配置运行参数"))
        {
            return BlockAction.RETRY_LATER;
        }
        return BlockAction.EXIT;
    }

    /**
     * 剧本限制：步骤限定了剧本时，至少一个正在生效
     *
     * @param stepPlaybooks 步骤限定的剧本（JSON 数组文本）
     * @param active 正在生效的剧本
     * @return 是否允许
     */
    static boolean playbookAllowed(String stepPlaybooks, Set<String> active)
    {
        Set<String> need = codes(stepPlaybooks);
        return need.isEmpty() || need.stream().anyMatch(active::contains);
    }

    /**
     * 剧本编码数组
     *
     * @param json JSON 数组文本
     * @return 编码
     */
    static Set<String> codes(String json)
    {
        Set<String> out = new HashSet<>();
        try
        {
            JsonNode n = MAPPER.readTree(StringUtils.isBlank(json) ? "[]" : json);
            if (!n.isArray())
            {
                throw new ServiceException("限定剧本须为编码数组");
            }
            for (JsonNode c : n)
            {
                if (!c.isString() || c.asString().isBlank())
                {
                    throw new ServiceException("限定剧本须为编码数组");
                }
                out.add(c.asString().trim());
            }
        }
        catch (JacksonException e)
        {
            throw new ServiceException("限定剧本须为编码数组");
        }
        return out;
    }

    /**
     * 校验并规范序列与步骤：编码、名称、无互动天数；步骤从 1 起连续编号、天数不递减、几点 0–23、条件与模板编码合法
     *
     * @param s 序列（含步骤）
     */
    static void validate(AbrNurtureSequence s)
    {
        if (StringUtils.isBlank(s.getSequenceCode()) || !CODE.matcher(s.getSequenceCode().trim()).matches())
        {
            throw new ServiceException("序列编码为小写字母开头的字母、数字、下划线（2–64 位）");
        }
        s.setSequenceCode(s.getSequenceCode().trim());
        if (StringUtils.isBlank(s.getSequenceName()))
        {
            throw new ServiceException("请填写序列名称");
        }
        if (StringUtils.isBlank(s.getEnrollEvent()))
        {
            throw new ServiceException("请填写入组事件");
        }
        int inactive = s.getInactiveDays() == null ? 30 : s.getInactiveDays();
        if (inactive < 0 || inactive > MAX_DAYS)
        {
            throw new ServiceException("无互动天数须为 0–365");
        }
        s.setInactiveDays(inactive);
        s.setStatus("1".equals(s.getStatus()) ? "1" : "0");
        List<AbrNurtureStep> steps = s.getSteps() == null ? List.of() : s.getSteps();
        if (steps.isEmpty() || steps.size() > MAX_STEPS)
        {
            throw new ServiceException("序列须有 1–" + MAX_STEPS + " 个步骤");
        }
        int prevDays = 0;
        for (int i = 0; i < steps.size(); i++)
        {
            AbrNurtureStep st = steps.get(i);
            String at = "第 " + (i + 1) + " 步";
            st.setStepNo(i + 1);
            if (StringUtils.isBlank(st.getStepName()))
            {
                throw new ServiceException(at + "须填写名称");
            }
            int days = st.getDelayDays() == null ? 0 : st.getDelayDays();
            if (days < prevDays || days > MAX_DAYS)
            {
                throw new ServiceException(at + "的天数须在 " + prevDays + "–" + MAX_DAYS + " 之间（不早于上一步）");
            }
            st.setDelayDays(days);
            prevDays = days;
            if (st.getSendHour() != null && (st.getSendHour() < 0 || st.getSendHour() > 23))
            {
                throw new ServiceException(at + "的发送时刻须为 0–23 点");
            }
            if (StringUtils.isBlank(st.getTemplateCode()) || !CODE.matcher(st.getTemplateCode().trim()).matches())
            {
                throw new ServiceException(at + "须选择消息模板");
            }
            st.setTemplateCode(st.getTemplateCode().trim());
            String cond = StringUtils.defaultIfBlank(st.getConditionCode(), COND_NONE);
            if (!CONDITIONS.contains(cond))
            {
                throw new ServiceException(at + "的条件只能是 none / has_plan / has_deadline");
            }
            st.setConditionCode(cond);
            st.setPlaybookCodes(MAPPER.writeValueAsString(codes(st.getPlaybookCodes()).stream().sorted().toList()));
            st.setStatus("1".equals(st.getStatus()) ? "1" : "0");
        }
    }
}

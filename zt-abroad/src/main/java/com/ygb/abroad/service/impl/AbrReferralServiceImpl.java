package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrReferral;
import com.ygb.abroad.domain.AbrReferralRule;
import com.ygb.abroad.domain.AbrReferrer;
import com.ygb.abroad.domain.AbrReward;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.growth.OutboundDecision;
import com.ygb.abroad.mapper.AbrMessageMapper;
import com.ygb.abroad.mapper.AbrReferralMapper;
import com.ygb.abroad.mapper.AbrRewardMapper;
import com.ygb.abroad.service.IAbrAppAuthService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrLeadAssignService;
import com.ygb.abroad.service.IAbrMessageService;
import com.ygb.abroad.service.IAbrReferralService;
import com.ygb.common.constant.HttpStatus;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 推荐码、被推荐人、奖励规则与发放 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrReferralServiceImpl implements IAbrReferralService
{
    static final String REGISTERED = "registered";

    static final String VALID = "valid";

    static final String SIGNED = "signed";

    static final String INVALID = "invalid";

    static final String REWARD_TYPE = "referral";

    /** 没有负责顾问时提醒能看全部线索的员工 */
    static final String PERM_ALL_LEADS = "abroad:lead:all";

    @Autowired
    private AbrReferralMapper referralMapper;

    @Autowired
    private AbrRewardMapper rewardMapper;

    @Autowired
    private AbrMessageMapper messageMapper;

    @Autowired
    private IAbrMessageService messageService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrLeadAssignService assignService;

    @Autowired
    private IAbrAppAuthService appAuthService;

    @Autowired
    private AbrShareSupport shareSupport;

    /** 有效测评的最短用时（秒） */
    @Value("${abroad.referral.min-assessment-seconds:120}")
    private int minAssessmentSeconds;

    @Override
    public Map<String, Object> attribute(Long leadId)
    {
        Map<String, Object> lead = leadId == null ? null : referralMapper.selectLeadInfo(leadId);
        if (lead == null || !(lead.get("visitorId") instanceof Number v))
        {
            return result("skipped", "线索没有访客");
        }
        AbrReferral r = ensureReferral(v.longValue(), number(lead.get("leadId")), number(lead.get("appUserId")));
        return r == null ? result("skipped", "不是经推荐码进入") : result("attributed", "推荐人 " + r.getReferrerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assessmentSubmitted(Long assessmentId)
    {
        Map<String, Object> check = assessmentId == null ? null : referralMapper.selectAssessmentCheck(assessmentId);
        if (check == null)
        {
            return result("skipped", "测评不存在");
        }
        AbrReferral r = ensureReferral(number(check.get("visitorId")), null, null);
        if (r == null)
        {
            return result("skipped", "不是经推荐码进入");
        }
        if (!REGISTERED.equals(r.getStatus()))
        {
            return result("skipped", "已判断过（" + r.getStatus() + "）");
        }
        AbrReferrer f = referralMapper.selectReferrerById(r.getReferrerId());
        String invalid = AbrReferralRules.invalidReason(self(f, r), number(check.get("seconds")), minAssessmentSeconds,
                Boolean.TRUE.equals(check.get("minor")));
        if (invalid != null)
        {
            r.setStatus(INVALID);
            r.setInvalidReason(invalid);
            referralMapper.updateReferral(r);
            return result("invalid", invalid);
        }
        r.setStatus(VALID);
        r.setValidTime(new Date());
        referralMapper.updateReferral(r);
        return reward(f, r, "valid_assessment");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> signed(Long leadId)
    {
        Map<String, Object> lead = leadId == null ? null : referralMapper.selectLeadInfo(leadId);
        if (lead == null)
        {
            return result("skipped", "线索不存在");
        }
        Long root = number(lead.get("leadId"));
        AbrReferral r = referralMapper.selectReferralByLead(root);
        if (r == null && lead.get("visitorId") instanceof Number v)
        {
            r = ensureReferral(v.longValue(), root, number(lead.get("appUserId")));
        }
        if (r == null)
        {
            return result("skipped", "不是经推荐码进入");
        }
        if (INVALID.equals(r.getStatus()) || SIGNED.equals(r.getStatus()))
        {
            return result("skipped", "已判断过（" + r.getStatus() + "）");
        }
        AbrReferrer f = referralMapper.selectReferrerById(r.getReferrerId());
        r.setLeadId(root);
        if (self(f, r))
        {
            r.setStatus(INVALID);
            r.setInvalidReason("自己推荐自己");
            referralMapper.updateReferral(r);
            return result("invalid", "自己推荐自己");
        }
        r.setStatus(SIGNED);
        r.setSignedTime(new Date());
        referralMapper.updateReferral(r);
        return reward(f, r, "signed");
    }

    @Override
    public Map<String, Object> inviteCode(Long leadId, Long taskId)
    {
        Map<String, Object> lead = leadId == null ? null : referralMapper.selectLeadInfo(leadId);
        if (lead == null || !"won".equals(lead.get("stage")))
        {
            return result("skipped", "线索不存在或已不是签约状态");
        }
        Long root = number(lead.get("leadId"));
        Long appUserId = number(lead.get("appUserId"));
        AbrReferrer f = referralMapper.selectReferrerByOwner(root, appUserId);
        if (f == null)
        {
            f = new AbrReferrer();
            f.setReferrerType("student");
            f.setDisplayName(StringUtils.defaultIfBlank((String) lead.get("displayName"), "签约学生 #" + root));
            f.setLeadId(root);
            f.setAppUserId(appUserId);
            f.setStatus("0");
            f.setCreateBy(AGENT);
            f = addReferrer(f);
        }
        Map<String, Object> out = result("", "");
        out.put("refCode", f.getRefCode());
        Long owner = number(lead.get("ownerUserId"));
        String who = StringUtils.defaultIfBlank((String) lead.get("displayName"), "线索 #" + root);
        OutboundDecision d = guardService.outbound(AGENT, "lead:" + root);
        if (d == OutboundDecision.BLOCKED)
        {
            return put(out, "blocked", "referral_agent 已暂停或停用");
        }
        if (d == OutboundDecision.CONFIRM)
        {
            manual(owner, root, "建议发送推荐码：" + who, who + " 签约满月，推荐码 " + f.getRefCode() + "（链接 " + link(f.getRefCode())
                    + "）。referral_agent 为人工确认模式，请顾问发给学生", "ref_code:" + root);
            return put(out, "manual", "人工确认模式：已提醒顾问发送");
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("refCode", f.getRefCode());
        vars.put("refLink", link(f.getRefCode()));
        IAbrMessageService.Result r = messageService.sendToStudent("referral_code", "lead", root, vars, AGENT, taskId, false);
        out.put("send", r);
        if (r.delivered())
        {
            return put(out, "sent", "");
        }
        if ("blocked".equals(r.status()))
        {
            return put(out, "blocked", r.reason());
        }
        manual(owner, root, "请发送推荐码：" + who, who + " 签约满月，推荐码 " + f.getRefCode() + "（链接 " + link(f.getRefCode()) + "）。自动消息未送达（"
                + r.reason() + "），请顾问发给学生", "ref_code:" + root);
        return put(out, "manual", "自动消息未送达，已提醒顾问：" + r.reason());
    }

    @Override
    public Map<String, Object> appMine()
    {
        Long appUserId = appAuthService.currentAppUserId();
        if (appUserId == null)
        {
            throw new ServiceException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        AbrReferrer f = referralMapper.selectReferrerByOwner(null, appUserId);
        if (f == null || !"0".equals(f.getStatus()))
        {
            out.put("code", null);
            return out;
        }
        out.put("code", f.getRefCode());
        out.put("link", link(f.getRefCode()));
        out.put("registered", f.getRegisteredCount());
        out.put("valid", f.getValidCount());
        out.put("signed", f.getSignedCount());
        return out;
    }

    /**
     * 访客经推荐码进入时记被推荐人（已有时补线索与学生账号）
     */
    private AbrReferral ensureReferral(Long visitorId, Long leadId, Long appUserId)
    {
        if (visitorId == null)
        {
            return null;
        }
        AbrReferral r = referralMapper.selectReferralByVisitor(visitorId);
        Map<String, Object> attr = referralMapper.selectVisitorAttribution(visitorId);
        Long visitorAppUser = attr == null ? null : number(attr.get("appUserId"));
        if (r == null)
        {
            String code = attr == null ? null : AbrReferralRules.refCode((String) attr.get("channelCode"), (String) attr.get("contentCode"));
            AbrReferrer f = code == null ? null : referralMapper.selectActiveReferrerByCode(code);
            if (f == null)
            {
                return null;
            }
            r = new AbrReferral();
            r.setReferrerId(f.getReferrerId());
            r.setVisitorId(visitorId);
            r.setLeadId(leadId);
            r.setAppUserId(appUserId != null ? appUserId : visitorAppUser);
            r.setStatus(REGISTERED);
            referralMapper.insertReferral(r);
            return referralMapper.selectReferralByVisitor(visitorId);
        }
        if ((leadId != null && r.getLeadId() == null) || (r.getAppUserId() == null && (appUserId != null || visitorAppUser != null)))
        {
            r.setLeadId(leadId);
            r.setAppUserId(appUserId != null ? appUserId : visitorAppUser);
            referralMapper.updateReferral(r);
        }
        return r;
    }

    /**
     * 自己推荐自己：同一学生账号或同一线索
     */
    private static boolean self(AbrReferrer f, AbrReferral r)
    {
        return f != null && ((f.getAppUserId() != null && f.getAppUserId().equals(r.getAppUserId()))
                || (f.getLeadId() != null && f.getLeadId().equals(r.getLeadId())));
    }

    /**
     * 按规则写待确认的奖励（推荐人、被推荐人各一条；同一里程碑只写一次；推荐人每天有上限）
     */
    private Map<String, Object> reward(AbrReferrer f, AbrReferral r, String milestone)
    {
        Map<String, Object> out = result(milestone, "");
        AbrReferralRule rule = referralMapper.selectActiveRule(f.getReferrerType(), milestone);
        if (rule == null)
        {
            return put(out, milestone, "没有启用的奖励规则");
        }
        int written = 0;
        if (StringUtils.isNotBlank(rule.getRewardTitle()))
        {
            if (rule.getDailyCap() != null && rule.getDailyCap() > 0
                    && referralMapper.countRewardsSince(f.getReferrerId(), milestone, todayStart()) >= rule.getDailyCap())
            {
                out.put("capped", true);
            }
            else
            {
                written += insertReward(f.getAppUserId(), f.getLeadId(), f.getReferrerId(), rule.getRewardTitle(), rule.getRewardProduct(), r, milestone, "referrer");
            }
        }
        if (StringUtils.isNotBlank(rule.getRefereeTitle()))
        {
            written += insertReward(r.getAppUserId(), r.getLeadId(), null, rule.getRefereeTitle(), "", r, milestone, "referee");
        }
        out.put("rewards", written);
        return out;
    }

    private int insertReward(Long appUserId, Long leadId, Long referrerId, String title, String product, AbrReferral r, String milestone, String side)
    {
        AbrReward w = new AbrReward();
        w.setAppUserId(appUserId);
        w.setLeadId(leadId);
        w.setReferrerId(referrerId);
        w.setRewardType(REWARD_TYPE);
        w.setTitle(StringUtils.substring(title, 0, 100));
        w.setProductCode(product);
        w.setStatus("pending");
        w.setRefType("referral");
        w.setRefId(r.getReferralId());
        w.setDedupKey("ref:" + r.getReferralId() + ":" + milestone + ":" + side);
        return rewardMapper.insertReward(w);
    }

    private void manual(Long owner, Long leadId, String title, String content, String dedupKey)
    {
        List<Long> to = owner != null ? List.of(owner) : messageMapper.selectUsersWithPerm(PERM_ALL_LEADS);
        for (Long u : to)
        {
            assignService.notify(u, "referral_code", title, content, leadId, dedupKey);
        }
    }

    @Override
    public String link(String refCode)
    {
        return shareSupport.appLink("?ch=" + AbrReferralRules.CHANNEL + "&ct=" + refCode);
    }

    @Override
    public String link(String referrerType, String refCode)
    {
        return shareSupport.appLink("?ch=" + AbrReferralRules.channel(referrerType) + "&ct=" + refCode);
    }

    @Override
    public List<AbrReferrer> selectReferrerList(AbrReferrer referrer)
    {
        return referralMapper.selectReferrerList(referrer);
    }

    @Override
    public AbrReferrer addReferrer(AbrReferrer referrer)
    {
        validate(referrer);
        if ((referrer.getLeadId() != null || referrer.getAppUserId() != null)
                && referralMapper.selectReferrerByOwner(referrer.getLeadId(), referrer.getAppUserId()) != null)
        {
            throw new ServiceException("这位学生已有推荐码");
        }
        String code;
        int tries = 0;
        do
        {
            code = AbrReferralRules.newCode();
        }
        while (referralMapper.countRefCode(code) > 0 && ++tries < 10);
        referrer.setRefCode(code);
        referralMapper.insertReferrer(referrer);
        return referralMapper.selectReferrerById(referrer.getReferrerId());
    }

    @Override
    public int updateReferrer(AbrReferrer referrer)
    {
        validate(referrer);
        return referralMapper.updateReferrer(referrer);
    }

    @Override
    public int deleteReferrers(Long[] referrerIds)
    {
        for (Long id : referrerIds)
        {
            if (referralMapper.countReferrals(id) > 0)
            {
                throw new ServiceException("推荐人 " + id + " 已有被推荐人，不能删除，可停用");
            }
        }
        return referralMapper.deleteReferrers(referrerIds);
    }

    private static void validate(AbrReferrer f)
    {
        if (!AbrReferralRules.TYPES.contains(f.getReferrerType()))
        {
            throw new ServiceException("推荐人类型只能是 student / ambassador / partner");
        }
        if (StringUtils.isBlank(f.getDisplayName()) || f.getDisplayName().trim().length() > 64)
        {
            throw new ServiceException("请填写称呼（最多 64 字）");
        }
        f.setDisplayName(f.getDisplayName().trim());
        f.setStatus("1".equals(f.getStatus()) ? "1" : "0");
        if (!AbrReferralRules.PARTNER.equals(f.getReferrerType()))
        {
            // 分成只对合作方
            f.setCommissionRate(null);
            f.setWindowDays(null);
            return;
        }
        if (f.getCommissionRate() != null && (f.getCommissionRate().signum() < 0 || f.getCommissionRate().compareTo(BigDecimal.valueOf(100)) > 0
                || f.getCommissionRate().scale() > 2))
        {
            throw new ServiceException("分成比例应为 0–100 之间、最多两位小数的百分数");
        }
        if (f.getWindowDays() != null && (f.getWindowDays() < 1 || f.getWindowDays() > 1095))
        {
            throw new ServiceException("归因有效天数应为 1–1095 天");
        }
    }

    @Override
    public List<AbrReferral> selectReferralList(AbrReferral referral)
    {
        return referralMapper.selectReferralList(referral);
    }

    @Override
    public List<AbrReferralRule> selectRuleList()
    {
        return referralMapper.selectRuleList();
    }

    @Override
    public int updateRule(AbrReferralRule rule)
    {
        if (rule.getRuleId() == null)
        {
            throw new ServiceException("缺少规则ID");
        }
        if (rule.getDailyCap() == null || rule.getDailyCap() < 0 || rule.getDailyCap() > 1000)
        {
            throw new ServiceException("每天上限须为 0–1000（0 不限）");
        }
        if (StringUtils.length(rule.getRewardTitle()) > 100 || StringUtils.length(rule.getRefereeTitle()) > 100)
        {
            throw new ServiceException("奖励内容最多 100 字");
        }
        if (StringUtils.containsAny(StringUtils.defaultString(rule.getRewardTitle()) + StringUtils.defaultString(rule.getRefereeTitle()), "现金", "红包"))
        {
            throw new ServiceException("推荐奖励只发服务抵扣或积分，不发现金");
        }
        rule.setStatus("1".equals(rule.getStatus()) ? "1" : "0");
        return referralMapper.updateRule(rule);
    }

    @Override
    public List<AbrReward> selectRewardList(AbrReward reward)
    {
        return rewardMapper.selectRewardList(reward);
    }

    @Override
    public int changeReward(Long rewardId, String action, String operator)
    {
        AbrReward w = rewardId == null ? null : rewardMapper.selectRewardById(rewardId);
        if (w == null)
        {
            throw new ServiceException("奖励不存在");
        }
        String[] move = switch (StringUtils.defaultString(action))
        {
            case "confirm" -> new String[] { "pending", "issued" };
            case "use" -> new String[] { "issued", "used" };
            case "void" -> new String[] { w.getStatus(), "void" };
            default -> throw new ServiceException("操作只能是 confirm / use / void");
        };
        if ("void".equals(action) && !"pending".equals(w.getStatus()) && !"issued".equals(w.getStatus()))
        {
            throw new ServiceException("只有待确认或已发放的奖励可以作废");
        }
        if (!"void".equals(action) && !move[0].equals(w.getStatus()))
        {
            throw new ServiceException("confirm".equals(action) ? "只有待确认的奖励可以确认发放" : "只有已发放的奖励可以标记为已使用");
        }
        int rows = rewardMapper.updateStatus(rewardId, move[0], move[1], operator);
        if (rows == 0)
        {
            throw new ServiceException("奖励状态已变化，请刷新");
        }
        return rows;
    }

    private static Date todayStart()
    {
        return Date.from(MarketingCalendar.today().atStartOfDay(MarketingCalendar.ZONE).toInstant());
    }

    private static Long number(Object o)
    {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static Map<String, Object> result(String action, String reason)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        m.put("reason", reason);
        return m;
    }

    private static Map<String, Object> put(Map<String, Object> out, String action, String reason)
    {
        out.put("action", action);
        out.put("reason", reason);
        return out;
    }
}

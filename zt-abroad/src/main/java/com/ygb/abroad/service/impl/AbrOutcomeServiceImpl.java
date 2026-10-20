package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrAppUser;
import com.ygb.abroad.domain.AbrCase;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrReward;
import com.ygb.abroad.domain.vo.AbrLeadCapture;
import com.ygb.abroad.domain.vo.AppOutcomeBody;
import com.ygb.abroad.domain.vo.AppOutcomeVo;
import com.ygb.abroad.mapper.AbrAppUserMapper;
import com.ygb.abroad.mapper.AbrAssessmentMapper;
import com.ygb.abroad.mapper.AbrCaseMapper;
import com.ygb.abroad.mapper.AbrMessageMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRewardMapper;
import com.ygb.abroad.service.IAbrAppAuthService;
import com.ygb.abroad.service.IAbrCaseService;
import com.ygb.abroad.service.IAbrLeadAssignService;
import com.ygb.abroad.service.IAbrLeadService;
import com.ygb.abroad.service.IAbrOutcomeService;
import com.ygb.common.constant.HttpStatus;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;

/**
 * 录取结果回填 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrOutcomeServiceImpl implements IAbrOutcomeService
{
    /** 感谢奖励的类型 */
    static final String REWARD_TYPE = "outcome_thanks";

    /** 回填结果待核验时提醒有此权限的员工（线索没有负责人时） */
    static final String PERM_REVIEW = "abroad:case:edit";

    /** 一个学生账号最多回填几条 */
    static final int MAX_OUTCOMES = 30;

    @Autowired
    private AbrCaseMapper caseMapper;

    @Autowired
    private AbrRewardMapper rewardMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrAppUserMapper appUserMapper;

    @Autowired
    private AbrAssessmentMapper assessmentMapper;

    @Autowired
    private AbrMessageMapper messageMapper;

    @Autowired
    private IAbrAppAuthService appAuthService;

    @Autowired
    private IAbrLeadService leadService;

    @Autowired
    private IAbrCaseService caseService;

    @Autowired
    private IAbrLeadAssignService assignService;

    /** 感谢内容与可抵扣的产品（配置；只发服务抵扣，不发现金） */
    @Value("${abroad.outcome.reward-title:免费定位咨询一次}")
    private String rewardTitle;

    @Value("${abroad.outcome.reward-product:consult_position}")
    private String rewardProduct;

    @Override
    public AppOutcomeVo mine()
    {
        return vo(requireUser().getAppUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppOutcomeVo submit(AppOutcomeBody body)
    {
        AbrAppUser user = requireUser();
        if (body == null || body.programId() == null)
        {
            throw new ServiceException("请选择项目");
        }
        AbrProgram program = programMapper.selectAbrProgramById(body.programId());
        if (program == null || !"0".equals(program.getStatus()))
        {
            throw new ServiceException("项目不存在或已下线");
        }
        AbrCase q = new AbrCase();
        q.setAppUserId(user.getAppUserId());
        List<AbrCase> mine = caseMapper.selectCaseList(q);
        if (mine.size() >= MAX_OUTCOMES)
        {
            throw new ServiceException("最多回填 " + MAX_OUTCOMES + " 条结果");
        }
        String season = StringUtils.trimToEmpty(body.season());
        if (mine.stream().anyMatch(c -> body.programId().equals(c.getProgramId()) && season.equals(c.getSeason())))
        {
            throw new ServiceException("这个项目这个申请季已经回填过了");
        }
        boolean publicity = Boolean.TRUE.equals(body.publicityConsent());
        if (publicity && assessmentMapper.countMinorForTarget("app_user", user.getAppUserId()) > 0)
        {
            throw new ServiceException("未成年人展示录取须由监护人同意，本人账号暂不能勾选展示");
        }
        Map<String, Object> captured = leadService.capture(new AbrLeadCapture(AbrLeadRules.SOURCE_H5, "outcome", user.getRegisterChannel(),
                user.getRegisterContent(), user.getNickname(), user.getPhone(), user.getUnionid(), user.getAppUserId(), user.getPrimaryVisitorId(), false, null,
                "回填录取结果：" + program.getSchoolName() + " " + program.getProgramName(), "program", program.getProgramId(), null, "app"));
        AbrLead lead = (AbrLead) captured.get("lead");
        AbrCase c = new AbrCase();
        c.setLeadId(lead.getLeadId());
        c.setAppUserId(user.getAppUserId());
        c.setSeason(season);
        c.setCountry(program.getCountry());
        c.setDegree(program.getDegree());
        c.setProgramId(program.getProgramId());
        c.setSchoolName(program.getSchoolName());
        c.setProgramName(program.getProgramName());
        c.setResult(body.result());
        c.setScholarship(StringUtils.trimToEmpty(body.scholarship()));
        Map<String, Object> profile = user.getPrimaryVisitorId() == null ? null : caseMapper.selectProfileSnapshot(user.getPrimaryVisitorId());
        if (profile != null)
        {
            c.setUndergradTier((String) profile.get("undergradTier"));
            c.setGpa(profile.get("gpa") instanceof BigDecimal g ? g : null);
            c.setIelts(profile.get("ielts") instanceof BigDecimal i ? i : null);
        }
        // 自报：C 级；上传截图后由顾问核验升级（B / A），之前不会公开
        c.setVerifyLevel("C");
        c.setSource("self_report");
        c.setAlgoConsent(Boolean.TRUE.equals(body.algoConsent()) ? "Y" : "N");
        c.setPublicityConsent(publicity ? "Y" : "N");
        c.setStatus("0");
        c.setCreateBy("app");
        caseService.saveCase(c, false);
        thank(user, lead, c);
        notifyReview(lead, c, "学生回填了录取结果：" + c.getSchoolName() + " " + c.getProgramName() + "，请核对并在有截图后核验");
        return vo(user.getAppUserId());
    }

    @Override
    public AppOutcomeVo uploadOffer(Long caseId, byte[] bytes, String filename)
    {
        AbrAppUser user = requireUser();
        AbrCase c = requireMine(caseId, user);
        caseService.saveOfferFile(c.getCaseId(), bytes, filename, "app");
        notifyReview(null, c, "学生为录取结果上传了 offer 截图：" + c.getSchoolName() + " " + c.getProgramName() + "，请核验后把核验等级改为 B / A");
        return vo(user.getAppUserId());
    }

    @Override
    public AppOutcomeVo revoke(Long caseId)
    {
        AbrAppUser user = requireUser();
        caseService.revoke(requireMine(caseId, user).getCaseId(), "app");
        return vo(user.getAppUserId());
    }

    @Override
    public AppOutcomeVo delete(Long caseId)
    {
        AbrAppUser user = requireUser();
        caseService.deleteCases(new Long[] { requireMine(caseId, user).getCaseId() });
        return vo(user.getAppUserId());
    }

    /**
     * 回填的感谢：同一账号同一申请季只发一次
     */
    private void thank(AbrAppUser user, AbrLead lead, AbrCase c)
    {
        AbrReward r = new AbrReward();
        r.setAppUserId(user.getAppUserId());
        r.setLeadId(lead.getLeadId());
        r.setRewardType(REWARD_TYPE);
        r.setTitle(StringUtils.substring(rewardTitle, 0, 100));
        r.setProductCode(rewardProduct);
        // 所有出款（含服务抵扣）只生成待确认记录，由员工核对后发放（开发规范 14.6）
        r.setStatus("pending");
        r.setRefType("case");
        r.setRefId(c.getCaseId());
        r.setDedupKey("outcome:" + user.getAppUserId() + ":" + c.getSeason());
        rewardMapper.insertReward(r);
    }

    private void notifyReview(AbrLead lead, AbrCase c, String content)
    {
        Long owner = lead != null ? lead.getOwnerUserId() : null;
        if (owner == null && c.getLeadId() != null)
        {
            AbrLead l = leadService.selectAbrLeadById(c.getLeadId());
            owner = l == null ? null : l.getOwnerUserId();
        }
        List<Long> to = owner != null ? List.of(owner) : messageMapper.selectUsersWithPerm(PERM_REVIEW);
        for (Long u : to)
        {
            assignService.notify(u, "outcome_review", "回填结果待核验", content, c.getLeadId(), "outcome:" + c.getCaseId() + ":" + DateUtils.dateTimeNow());
        }
    }

    private AppOutcomeVo vo(Long appUserId)
    {
        AbrCase q = new AbrCase();
        q.setAppUserId(appUserId);
        List<AppOutcomeVo.Outcome> outcomes = caseMapper.selectCaseList(q).stream()
                .map(c -> new AppOutcomeVo.Outcome(c.getCaseId(), c.getSeason(), c.getSchoolName(), c.getProgramName(), c.getResult(),
                        StringUtils.trimToNull(c.getScholarship()), "Y".equals(c.getAlgoConsent()), "Y".equals(c.getPublicityConsent()),
                        AbrCaseRules.PUBLIC_LEVELS.contains(c.getVerifyLevel()), c.getHasOfferFile(),
                        DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, c.getCreateTime())))
                .toList();
        AbrReward rq = new AbrReward();
        rq.setAppUserId(appUserId);
        List<AppOutcomeVo.Reward> rewards = rewardMapper.selectRewardList(rq).stream()
                .map(r -> new AppOutcomeVo.Reward(r.getTitle(), r.getStatus(), DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, r.getCreateTime())))
                .toList();
        return new AppOutcomeVo(outcomes, rewards);
    }

    private AbrCase requireMine(Long caseId, AbrAppUser user)
    {
        AbrCase c = caseId == null ? null : caseMapper.selectCaseById(caseId);
        if (c == null || !user.getAppUserId().equals(c.getAppUserId()))
        {
            throw new ServiceException("记录不存在");
        }
        return c;
    }

    private AbrAppUser requireUser()
    {
        Long id = appAuthService.currentAppUserId();
        if (id == null)
        {
            throw new ServiceException("请先登录后再回填结果", HttpStatus.UNAUTHORIZED);
        }
        return appUserMapper.selectAbrAppUserById(id);
    }
}

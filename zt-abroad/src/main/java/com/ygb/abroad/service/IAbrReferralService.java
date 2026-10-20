package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrReferral;
import com.ygb.abroad.domain.AbrReferralRule;
import com.ygb.abroad.domain.AbrReferrer;
import com.ygb.abroad.domain.AbrReward;

/**
 * 推荐码、被推荐人、奖励规则与发放（M9-02；referral_agent 的业务）
 *
 * @author ygb
 */
public interface IAbrReferralService
{
    /** Agent 编码 */
    public static final String AGENT = "referral_agent";

    /**
     * 线索收口后归因：线索的访客经推荐码进入时记被推荐人
     *
     * @param leadId 线索ID
     * @return 结果（action：attributed / skipped，reason）
     */
    public Map<String, Object> attribute(Long leadId);

    /**
     * 测评提交后判断有效测评：防刷（自己推荐自己、用时过短、未成年人），有效时按规则写待确认的奖励（每天上限）
     *
     * @param assessmentId 测评ID
     * @return 结果
     */
    public Map<String, Object> assessmentSubmitted(Long assessmentId);

    /**
     * 签约后：被推荐人记为已签约，按规则写待确认的双方奖励
     *
     * @param leadId 线索ID
     * @return 结果
     */
    public Map<String, Object> signed(Long leadId);

    /**
     * 签约满月：给学生生成推荐码并发送（执行模式为人工确认或发送不成功时提醒负责顾问）
     *
     * @param leadId 线索ID
     * @param taskId 编排任务ID
     * @return 结果
     */
    public Map<String, Object> inviteCode(Long leadId, Long taskId);

    /**
     * C 端：我的推荐码与进度（没有推荐码时 code 为空）
     *
     * @return code、link、registered、valid、signed
     */
    public Map<String, Object> appMine();

    /**
     * 查询推荐人
     *
     * @param referrer 条件
     * @return 列表
     */
    public List<AbrReferrer> selectReferrerList(AbrReferrer referrer);

    /**
     * 新增推荐人（生成推荐码）
     *
     * @param referrer 推荐人
     * @return 推荐人
     */
    public AbrReferrer addReferrer(AbrReferrer referrer);

    /**
     * 修改推荐人
     *
     * @param referrer 推荐人
     * @return 行数
     */
    public int updateReferrer(AbrReferrer referrer);

    /**
     * 删除推荐人（有被推荐人的不能删，可停用）
     *
     * @param referrerIds 推荐人ID
     * @return 行数
     */
    public int deleteReferrers(Long[] referrerIds);

    /**
     * 推荐链接
     *
     * @param refCode 推荐码
     * @return 链接
     */
    public String link(String refCode);

    /**
     * 查询被推荐人
     *
     * @param referral 条件
     * @return 列表
     */
    public List<AbrReferral> selectReferralList(AbrReferral referral);

    /**
     * 查询奖励规则
     *
     * @return 列表
     */
    public List<AbrReferralRule> selectRuleList();

    /**
     * 修改奖励规则
     *
     * @param rule 规则
     * @return 行数
     */
    public int updateRule(AbrReferralRule rule);

    /**
     * 查询奖励
     *
     * @param reward 条件
     * @return 列表
     */
    public List<AbrReward> selectRewardList(AbrReward reward);

    /**
     * 奖励改状态：确认发放（pending → issued）、作废（pending / issued → void）、已使用（issued → used）
     *
     * @param rewardId 奖励ID
     * @param action confirm / void / use
     * @param operator 操作人
     * @return 行数
     */
    public int changeReward(Long rewardId, String action, String operator);
}

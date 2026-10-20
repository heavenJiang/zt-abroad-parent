package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrReferral;
import com.ygb.abroad.domain.AbrReferralRule;
import com.ygb.abroad.domain.AbrReferrer;

/**
 * 推荐码、被推荐人与奖励规则 数据层
 *
 * @author ygb
 */
public interface AbrReferralMapper
{
    /**
     * 查询推荐人（带已进入 / 有效测评 / 已签约人数）
     *
     * @param referrer 条件（referrerType、displayName、refCode、status）
     * @return 列表
     */
    public List<AbrReferrer> selectReferrerList(AbrReferrer referrer);

    /**
     * 查询推荐人
     *
     * @param referrerId 推荐人ID
     * @return 推荐人
     */
    public AbrReferrer selectReferrerById(Long referrerId);

    /**
     * 按推荐码查询正常的推荐人
     *
     * @param refCode 推荐码
     * @return 推荐人
     */
    public AbrReferrer selectActiveReferrerByCode(String refCode);

    /**
     * 按线索或学生账号查询推荐人（签约学生）
     *
     * @param leadId 线索
     * @param appUserId 学生账号
     * @return 推荐人
     */
    public AbrReferrer selectReferrerByOwner(@Param("leadId") Long leadId, @Param("appUserId") Long appUserId);

    /**
     * 推荐码是否已存在
     *
     * @param refCode 推荐码
     * @return 数量
     */
    public int countRefCode(String refCode);

    /**
     * 新增推荐人
     *
     * @param referrer 推荐人
     * @return 行数
     */
    public int insertReferrer(AbrReferrer referrer);

    /**
     * 修改推荐人（称呼、类型、状态、备注；推荐码不改）
     *
     * @param referrer 推荐人
     * @return 行数
     */
    public int updateReferrer(AbrReferrer referrer);

    /**
     * 删除没有被推荐人的推荐人
     *
     * @param referrerIds 推荐人ID
     * @return 行数
     */
    public int deleteReferrers(Long[] referrerIds);

    /**
     * 推荐人下的被推荐人数
     *
     * @param referrerId 推荐人ID
     * @return 数量
     */
    public int countReferrals(Long referrerId);

    /**
     * 查询被推荐人
     *
     * @param referral 条件（referrerId、status）
     * @return 列表
     */
    public List<AbrReferral> selectReferralList(AbrReferral referral);

    /**
     * 按访客查询被推荐人
     *
     * @param visitorId 访客ID
     * @return 被推荐人
     */
    public AbrReferral selectReferralByVisitor(Long visitorId);

    /**
     * 按线索查询被推荐人（线索或其访客）
     *
     * @param leadId 线索ID
     * @return 被推荐人
     */
    public AbrReferral selectReferralByLead(Long leadId);

    /**
     * 新增被推荐人（同一访客只一条）
     *
     * @param referral 被推荐人
     * @return 行数
     */
    public int insertReferral(AbrReferral referral);

    /**
     * 更新被推荐人（线索、学生账号、状态、原因、时间）
     *
     * @param referral 被推荐人
     * @return 行数
     */
    public int updateReferral(AbrReferral referral);

    /**
     * 访客的归因信息：渠道码、内容编号、学生账号
     *
     * @param visitorId 访客ID
     * @return channelCode、contentCode、appUserId（不存在为 null）
     */
    public Map<String, Object> selectVisitorAttribution(Long visitorId);

    /**
     * 测评的防刷信息：访客、用时（秒）、是否未成年 / 不参加分享奖励
     *
     * @param assessmentId 测评ID
     * @return visitorId、seconds、minor（不存在为 null）
     */
    public Map<String, Object> selectAssessmentCheck(Long assessmentId);

    /**
     * 线索（合并后的主线索）的访客、学生账号、阶段、称呼、负责人
     *
     * @param leadId 线索ID
     * @return leadId、visitorId、appUserId、stage、displayName、ownerUserId（不存在为 null）
     */
    public Map<String, Object> selectLeadInfo(Long leadId);

    /**
     * 推荐人某天某类奖励的条数（每天上限用）
     *
     * @param referrerId 推荐人
     * @param milestone 里程碑
     * @param since 当天零点
     * @return 条数
     */
    public int countRewardsSince(@Param("referrerId") Long referrerId, @Param("milestone") String milestone, @Param("since") Date since);

    /**
     * 查询奖励规则
     *
     * @return 列表
     */
    public List<AbrReferralRule> selectRuleList();

    /**
     * 查询启用的奖励规则
     *
     * @param referrerType 推荐人类型
     * @param milestone 里程碑
     * @return 规则（没有为 null）
     */
    public AbrReferralRule selectActiveRule(@Param("referrerType") String referrerType, @Param("milestone") String milestone);

    /**
     * 修改奖励规则（奖励内容、产品、被推荐人奖励、每天上限、状态、备注）
     *
     * @param rule 规则
     * @return 行数
     */
    public int updateRule(AbrReferralRule rule);
}

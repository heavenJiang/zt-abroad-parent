package com.ygb.abroad.domain.vo;

/**
 * C 端回填录取结果
 *
 * @param programId 院校库项目（选校单中的项目）
 * @param season 申请季（如 2027-fall）
 * @param result 结果（admit / reject / waitlist / withdrawn）
 * @param scholarship 奖学金（可空）
 * @param algoConsent 同意用于改进选校（脱敏后进入案例库参与校准）
 * @param publicityConsent 同意以泛化后的背景展示录取（offer 卡、offer 墙）
 * @author ygb
 */
public record AppOutcomeBody(Long programId, String season, String result, String scholarship, Boolean algoConsent, Boolean publicityConsent)
{
}

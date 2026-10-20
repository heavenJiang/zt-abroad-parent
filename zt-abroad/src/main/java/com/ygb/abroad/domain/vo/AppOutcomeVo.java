package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端：我回填的结果与收到的感谢
 *
 * @param outcomes 回填的结果
 * @param rewards 收到的奖励
 * @author ygb
 */
public record AppOutcomeVo(List<Outcome> outcomes, List<Reward> rewards)
{
    /**
     * 一条回填
     *
     * @param id 记录ID
     * @param season 申请季
     * @param school 院校
     * @param program 项目
     * @param result 结果
     * @param scholarship 奖学金
     * @param algoConsent 是否同意用于改进选校
     * @param publicityConsent 是否同意展示
     * @param verified 是否已核验（上传 offer 截图后由顾问核验）
     * @param hasOfferFile 是否已上传 offer 截图
     * @param submittedAt 回填时间
     */
    public record Outcome(Long id, String season, String school, String program, String result, String scholarship, boolean algoConsent,
            boolean publicityConsent, boolean verified, boolean hasOfferFile, String submittedAt)
    {
    }

    /**
     * 一份奖励
     *
     * @param title 内容
     * @param status 状态（pending / issued / used / void）
     * @param createdAt 时间
     */
    public record Reward(String title, String status, String createdAt)
    {
    }
}

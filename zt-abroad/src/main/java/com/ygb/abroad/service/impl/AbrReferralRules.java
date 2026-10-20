package com.ygb.abroad.service.impl;

import java.security.SecureRandom;
import java.util.Set;
import com.ygb.common.utils.StringUtils;

/**
 * 推荐规则（纯计算）：推荐码生成、有效测评的防刷判断、归因判断
 *
 * @author ygb
 */
final class AbrReferralRules
{
    /** 推荐链接的渠道码 */
    static final String CHANNEL = "ref";

    /** 合作方链接的渠道码（M9-05：归因看板中与学生、校园大使的推荐分开统计） */
    static final String PARTNER_CHANNEL = "partner";

    static final String PARTNER = "partner";

    static final Set<String> TYPES = Set.of("student", "ambassador", "partner");

    static final Set<String> MILESTONES = Set.of("valid_assessment", "signed");

    /** 推荐码字符（去掉易混的 0 o 1 l） */
    static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    static final int CODE_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AbrReferralRules()
    {
    }

    /**
     * 新推荐码
     *
     * @return 8 位小写字母与数字
     */
    static String newCode()
    {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++)
        {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * 访客是否经推荐码进入
     *
     * @param channelCode 访客的渠道码
     * @param contentCode 访客的内容编号
     * @return 推荐码（不是推荐进入为 null）
     */
    static String refCode(String channelCode, String contentCode)
    {
        return (CHANNEL.equals(channelCode) || PARTNER_CHANNEL.equals(channelCode)) && StringUtils.isNotBlank(contentCode)
                ? contentCode.trim().toLowerCase() : null;
    }

    /**
     * 推荐人类型对应的链接渠道码
     *
     * @param referrerType 推荐人类型
     * @return 合作方为 partner，其他为 ref
     */
    static String channel(String referrerType)
    {
        return PARTNER.equals(referrerType) ? PARTNER_CHANNEL : CHANNEL;
    }

    /**
     * 有效测评的防刷判断
     *
     * @param selfReferral 是否自己推荐自己
     * @param seconds 测评用时（秒）
     * @param minSeconds 最短用时
     * @param minor 是否未成年人（不参加分享奖励类活动）
     * @return 无效原因（有效为 null）
     */
    static String invalidReason(boolean selfReferral, long seconds, int minSeconds, boolean minor)
    {
        if (selfReferral)
        {
            return "自己推荐自己";
        }
        if (minor)
        {
            return "未成年人不参加推荐奖励";
        }
        if (seconds < minSeconds)
        {
            return "测评用时过短（" + seconds + " 秒，少于 " + minSeconds + " 秒）";
        }
        return null;
    }
}

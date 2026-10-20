package com.ygb.abroad.integration.channel;

import com.ygb.common.utils.StringUtils;

/**
 * 接收方脱敏（发送记录与日志只存脱敏后的值）
 *
 * @author ygb
 */
public final class ChannelMask
{
    private ChannelMask()
    {
    }

    /**
     * 手机号只留后四位；openid、企业微信账号只留前 3 位和后 3 位
     *
     * @param recipient 接收方
     * @return 脱敏后的值
     */
    public static String recipient(String recipient)
    {
        String r = StringUtils.trimToEmpty(recipient);
        if (r.matches("^1\\d{10}$"))
        {
            return "*******" + r.substring(7);
        }
        if (r.length() <= 6)
        {
            return r.isEmpty() ? "" : r.charAt(0) + "***";
        }
        return r.substring(0, 3) + "***" + r.substring(r.length() - 3);
    }
}

package com.ygb.abroad.domain.vo;

/**
 * C 端：当前学生账号（只返回本人可见的信息，不含 openid、内部字段）
 *
 * @param nickname 称呼
 * @param phone 手机号（脱敏，未绑定为空串）
 * @param phoneBound 是否已绑定手机号
 * @param wechatBound 是否已绑定微信
 * @param registerTime 注册时间（yyyy-MM-dd）
 * @author ygb
 */
public record AppUserVo(String nickname, String phone, boolean phoneBound, boolean wechatBound, String registerTime)
{
}

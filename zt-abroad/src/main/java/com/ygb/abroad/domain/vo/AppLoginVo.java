package com.ygb.abroad.domain.vo;

/**
 * C 端：登录结果
 *
 * @param appToken 学生令牌（之后的请求在请求头 X-App-Token 中携带；只返回这一次）
 * @param expiresIn 有效期（秒，期间每次使用都会顺延）
 * @param user 账号
 * @param newUser 是否首次登录
 * @param merged 是否把本设备的匿名记录合并进了账号
 * @author ygb
 */
public record AppLoginVo(String appToken, long expiresIn, AppUserVo user, boolean newUser, boolean merged)
{
}

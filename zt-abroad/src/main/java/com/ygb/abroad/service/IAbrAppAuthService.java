package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrAppUser;
import com.ygb.abroad.domain.vo.AppAuthBody;
import com.ygb.abroad.domain.vo.AppLoginVo;
import com.ygb.abroad.domain.vo.AppUserVo;

/**
 * C 端身份 服务层：学生账号、学生令牌（Redis 前缀 abr_app_token:，请求头 X-App-Token，与员工令牌互不通用）、
 * 服务号网页授权登录、访客合并、手机号单独同意与绑定；管理端账号查询
 *
 * @author ygb
 */
public interface IAbrAppAuthService
{
    /** 学生令牌请求头 */
    public static final String HEADER = "X-App-Token";

    /** 事件：首次登录（注册） */
    public static final String EVENT_REGISTERED = "app_user_registered";

    /** 事件：登录 */
    public static final String EVENT_LOGIN = "app_user_login";

    /** 事件：绑定手机号（含单独同意的政策版本，不含手机号） */
    public static final String EVENT_PHONE_BOUND = "app_user_phone_bound";

    /**
     * 当前请求的学生账号ID：没有学生令牌为 null；令牌无效或账号停用时抛出 401 业务异常；有效时顺延有效期
     *
     * @return 学生账号ID
     */
    public Long currentAppUserId();

    /**
     * 服务号网页授权地址（回到的页面须在 C 端域名内；返回防伪串，回调时带回）
     *
     * @param redirect 授权后回到的页面
     * @return url、state
     */
    public Map<String, Object> wechatAuthorizeUrl(String redirect);

    /**
     * 服务号网页授权登录：换身份 → 找到或新建账号 → 当前设备的访客成为主访客或合并进主访客 → 发学生令牌
     *
     * @param body code、state
     * @param visitorToken 当前设备的访客令牌（可为空）
     * @return 登录结果
     */
    public AppLoginVo loginWechat(AppAuthBody body, String visitorToken);

    /**
     * 退出登录（删除学生令牌；前端随后重新获取访客令牌）
     */
    public void logout();

    /**
     * 当前账号
     *
     * @return 账号
     */
    public AppUserVo me();

    /**
     * 发送绑定手机号的验证码（须已登录；同一手机号 60 秒一次）
     *
     * @param phone 手机号
     */
    public void sendPhoneCode(String phone);

    /**
     * 绑定手机号：须勾选单独同意（隐私政策当前版本）并通过短信验证；手机号已绑定其他账号时拒绝
     *
     * @param body 手机号、验证码、同意与版本
     * @return 账号
     */
    public AppUserVo bindPhone(AppAuthBody body);

    /**
     * 管理端：账号列表（手机号脱敏）
     *
     * @param user 查询条件
     * @return 账号集合
     */
    public List<AbrAppUser> selectAbrAppUserList(AbrAppUser user);

    /**
     * 管理端：账号详情（手机号脱敏）
     *
     * @param appUserId 账号ID
     * @return 账号
     */
    public AbrAppUser selectAbrAppUserById(Long appUserId);

    /**
     * 管理端：手机号明文（须单独权限，调用方记操作日志）
     *
     * @param appUserId 账号ID
     * @return 手机号，未绑定为空串
     */
    public String selectPhone(Long appUserId);

    /**
     * 管理端：停用 / 启用（停用后学生令牌立即失效）
     *
     * @param appUserId 账号ID
     * @param status 状态（0 正常 / 1 停用）
     * @param operator 操作人
     * @return 结果
     */
    public int updateStatus(Long appUserId, String status, String operator);
}

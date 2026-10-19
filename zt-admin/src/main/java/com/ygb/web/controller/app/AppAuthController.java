package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.vo.AppAuthBody;
import com.ygb.abroad.service.IAbrAppAuthService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-登录与账号（服务号网页授权登录、学生令牌、手机号单独同意与绑定）
 *
 * 学生令牌在请求头 X-App-Token 中携带，与员工令牌互不通用；登录后 C 端各接口的“本人”取账号的主访客。
 * 匿名接口按 IP 限流；写操作记业务事件（abr_event），不记员工操作日志。
 *
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/auth")
public class AppAuthController extends BaseController
{
    @Autowired
    private IAbrAppAuthService authService;

    /**
     * 服务号网页授权地址（redirect 为授权后回到的 C 端页面）
     */
    @RateLimiter(key = "app_auth_url:", time = 60, count = 20, limitType = LimitType.IP)
    @GetMapping("/wechat/url")
    public AjaxResult wechatUrl(@RequestParam String redirect)
    {
        return success(authService.wechatAuthorizeUrl(redirect));
    }

    /**
     * 服务号网页授权登录（code、state 来自授权回跳；当前设备的访客记录并入账号）
     */
    @RateLimiter(key = "app_auth_login:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/wechat")
    public AjaxResult loginWechat(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String visitorToken,
            @RequestBody AppAuthBody body)
    {
        return success(authService.loginWechat(body, visitorToken));
    }

    /**
     * 退出登录
     */
    @RateLimiter(key = "app_auth_logout:", time = 60, count = 20, limitType = LimitType.IP)
    @PostMapping("/logout")
    public AjaxResult logout()
    {
        authService.logout();
        return success();
    }

    /**
     * 当前账号
     */
    @RateLimiter(key = "app_auth_me:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/me")
    public AjaxResult me()
    {
        return success(authService.me());
    }

    /**
     * 发送绑定手机号的验证码
     */
    @RateLimiter(key = "app_auth_sms:", time = 60, count = 5, limitType = LimitType.IP)
    @PostMapping("/phone/code")
    public AjaxResult phoneCode(@RequestBody AppAuthBody body)
    {
        authService.sendPhoneCode(body.getPhone());
        return success();
    }

    /**
     * 绑定手机号（须单独同意并通过短信验证）
     */
    @RateLimiter(key = "app_auth_phone:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/phone")
    public AjaxResult bindPhone(@RequestBody AppAuthBody body)
    {
        return success(authService.bindPhone(body));
    }
}

package com.ygb.abroad.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ygb.abroad.domain.AbrAppUser;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppAuthBody;
import com.ygb.abroad.domain.vo.AppLoginVo;
import com.ygb.abroad.domain.vo.AppUserVo;
import com.ygb.abroad.integration.AbrSmsClient;
import com.ygb.abroad.integration.AbrWechatOaAuthClient;
import com.ygb.abroad.mapper.AbrAppUserMapper;
import com.ygb.abroad.mapper.AbrVisitorMapper;
import com.ygb.abroad.service.IAbrAppAuthService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrPolicyService;
import com.ygb.common.constant.HttpStatus;
import com.ygb.common.core.redis.RedisCache;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;

/**
 * C 端身份 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrAppAuthServiceImpl implements IAbrAppAuthService
{
    /** Redis：学生令牌哈希 → 学生账号ID */
    static final String TOKEN_KEY = "abr_app_token:";

    /** Redis：授权防伪串 */
    static final String STATE_KEY = "abr_app_oauth_state:";

    /** Redis：绑定手机号的验证码（按账号） */
    static final String SMS_KEY = "abr_app_sms:";

    /** Redis：同一手机号的发送间隔 */
    static final String SMS_COOLDOWN_KEY = "abr_app_sms_cd:";

    /** 请求属性：本次请求已识别的学生账号ID（同一请求内不重复查 Redis） */
    static final String REQUEST_ATTR = "abr.appUserId";

    /** 令牌格式：32 字节随机数的 base64url（43 位） */
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    /** 中国大陆手机号 */
    static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 防伪串格式 */
    private static final Pattern STATE = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    /** 验证码有效分钟、最多尝试次数、发送间隔秒 */
    static final int SMS_MINUTES = 5;

    static final int SMS_MAX_TRIES = 5;

    static final int SMS_COOLDOWN_SECONDS = 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private AbrAppUserMapper userMapper;

    @Autowired
    private AbrVisitorMapper visitorMapper;

    @Autowired
    private IAbrEventService eventService;

    @Autowired
    private IAbrPolicyService policyService;

    @Autowired
    private AbrWechatOaAuthClient wechat;

    @Autowired
    private AbrSmsClient sms;

    @Autowired
    private RedisCache redisCache;

    /** C 端地址（授权回跳页面须在其下） */
    @Value("${abroad.app.url:http://localhost:5173/}")
    private String appUrl;

    /** 学生令牌有效天数（每次使用顺延） */
    @Value("${abroad.app.token-days:30}")
    private int tokenDays;

    @Override
    public Long currentAppUserId()
    {
        HttpServletRequest request = request();
        if (request == null)
        {
            return null;
        }
        Object cached = request.getAttribute(REQUEST_ATTR);
        if (cached instanceof Long id)
        {
            return id;
        }
        String token = request.getHeader(HEADER);
        if (StringUtils.isBlank(token))
        {
            return null;
        }
        Long id = resolveToken(token);
        if (id == null)
        {
            throw new ServiceException("登录已过期，请重新登录", HttpStatus.UNAUTHORIZED);
        }
        AbrAppUser user = userMapper.selectAbrAppUserById(id);
        if (user == null || !"0".equals(user.getStatus()))
        {
            redisCache.deleteObject(TOKEN_KEY + AbrVisitorServiceImpl.hash(token));
            throw new ServiceException("账号已停用，请联系工作室", HttpStatus.UNAUTHORIZED);
        }
        request.setAttribute(REQUEST_ATTR, id);
        return id;
    }

    private Long resolveToken(String token)
    {
        if (!TOKEN.matcher(token).matches())
        {
            return null;
        }
        String key = TOKEN_KEY + AbrVisitorServiceImpl.hash(token);
        Object v = redisCache.getCacheObject(key);
        if (v == null)
        {
            return null;
        }
        redisCache.expire(key, tokenDays, TimeUnit.DAYS);
        try
        {
            return Long.valueOf(String.valueOf(v));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    @Override
    public Map<String, Object> wechatAuthorizeUrl(String redirect)
    {
        String r = StringUtils.trimToEmpty(redirect);
        String base = appUrl.endsWith("/") ? appUrl : appUrl + "/";
        if (!r.startsWith(base) || r.length() > 500 || r.contains("#"))
        {
            throw new ServiceException("回到的页面须在 C 端域名内");
        }
        String state = random(24);
        redisCache.setCacheObject(STATE_KEY + state, "1", 10, TimeUnit.MINUTES);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", wechat.authorizeUrl(r, state));
        m.put("state", state);
        m.put("mock", wechat.isMock());
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppLoginVo loginWechat(AppAuthBody body, String visitorToken)
    {
        String state = StringUtils.trimToEmpty(body == null ? null : body.getState());
        if (!STATE.matcher(state).matches() || !redisCache.deleteObject(STATE_KEY + state))
        {
            throw new ServiceException("登录已过期，请重新进入页面");
        }
        AbrWechatOaAuthClient.Identity id = wechat.exchange(body.getCode());
        AbrAppUser user = id.unionid() == null ? null : userMapper.selectByUnionid(id.unionid());
        if (user == null)
        {
            user = userMapper.selectByOaOpenid(id.openid());
        }
        if (user != null && !"0".equals(user.getStatus()))
        {
            throw new ServiceException("账号已停用，请联系工作室");
        }
        AbrVisitor visitor = StringUtils.isBlank(visitorToken) ? null : visitorMapper.selectAbrVisitorByTokenHash(AbrVisitorServiceImpl.hash(visitorToken));
        boolean usable = visitor != null && visitor.getAppUserId() == null && visitor.getMergedInto() == null;
        boolean newUser = user == null;
        if (newUser)
        {
            user = new AbrAppUser();
            user.setOaOpenid(id.openid());
            user.setUnionid(id.unionid());
            user.setRegisterChannel(usable ? visitor.getChannelCode() : "direct");
            user.setRegisterContent(usable ? visitor.getContentCode() : "");
            userMapper.insertAbrAppUser(user);
            user = userMapper.selectAbrAppUserById(user.getAppUserId());
        }
        else
        {
            userMapper.updateLogin(user.getAppUserId(), id.openid(), id.unionid());
        }
        Long uid = user.getAppUserId();
        boolean merged = false;
        Long primary = user.getPrimaryVisitorId();
        if (usable && primary == null)
        {
            visitorMapper.bindAppUser(visitor.getVisitorId(), uid);
            userMapper.setPrimaryVisitor(uid, visitor.getVisitorId());
            primary = visitor.getVisitorId();
        }
        else if (usable && !primary.equals(visitor.getVisitorId()))
        {
            merge(visitor.getVisitorId(), primary, uid);
            merged = true;
        }
        else if (primary == null)
        {
            primary = newPrimaryVisitor(uid);
        }
        visitorMapper.tagAppUser(primary, uid);
        AbrVisitor pv = visitorMapper.selectAbrVisitorById(primary);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", "wechat_oa");
        payload.put("merged", merged);
        payload.put("channel", user.getRegisterChannel());
        if (newUser)
        {
            eventService.recordAction(pv, EVENT_REGISTERED, "app_user", uid, payload);
        }
        eventService.recordAction(pv, EVENT_LOGIN, "app_user", uid, payload);
        String token = random(32);
        redisCache.setCacheObject(TOKEN_KEY + AbrVisitorServiceImpl.hash(token), String.valueOf(uid), tokenDays, TimeUnit.DAYS);
        return new AppLoginVo(token, tokenDays * 86400L, vo(userMapper.selectAbrAppUserById(uid)), newUser, merged);
    }

    /**
     * 把另一台设备的访客合并进主访客
     */
    private void merge(Long fromId, Long toId, Long uid)
    {
        visitorMapper.mergeProfileInto(fromId, toId);
        visitorMapper.moveAssessments(fromId, toId, uid);
        visitorMapper.moveProfiles(fromId, toId, uid);
        visitorMapper.moveAssessmentFiles(fromId, toId, uid);
        visitorMapper.moveToolRuns(fromId, toId, uid);
        visitorMapper.movePlanItems(fromId, toId, uid);
        visitorMapper.deletePlanItems(fromId);
        visitorMapper.movePlanActive(fromId, toId);
        visitorMapper.deletePlanActive(fromId);
        visitorMapper.mergeMaterialDone(fromId, toId, uid);
        visitorMapper.moveMaterials(fromId, toId, uid);
        visitorMapper.deleteMaterials(fromId);
        visitorMapper.moveShares(fromId, toId, uid);
        visitorMapper.revokeShares(fromId);
        visitorMapper.moveEvents(fromId, toId, uid);
        visitorMapper.markMerged(fromId, toId, uid);
    }

    /**
     * 账号还没有主访客、当前设备也没有可用的访客时，新建一个只能凭学生令牌使用的主访客
     */
    private Long newPrimaryVisitor(Long uid)
    {
        AbrVisitor v = new AbrVisitor();
        v.setTokenHash(AbrVisitorServiceImpl.hash(random(32)));
        v.setChannelCode("direct");
        v.setContentCode("");
        v.setClientPlatform("");
        visitorMapper.insertAbrVisitor(v);
        visitorMapper.bindAppUser(v.getVisitorId(), uid);
        userMapper.setPrimaryVisitor(uid, v.getVisitorId());
        return userMapper.selectAbrAppUserById(uid).getPrimaryVisitorId();
    }

    @Override
    public void logout()
    {
        HttpServletRequest request = request();
        String token = request == null ? null : request.getHeader(HEADER);
        if (StringUtils.isNotBlank(token))
        {
            redisCache.deleteObject(TOKEN_KEY + AbrVisitorServiceImpl.hash(token));
        }
    }

    @Override
    public AppUserVo me()
    {
        return vo(requireUser());
    }

    @Override
    public void sendPhoneCode(String phone)
    {
        AbrAppUser user = requireUser();
        String p = StringUtils.trimToEmpty(phone);
        if (!PHONE.matcher(p).matches())
        {
            throw new ServiceException("请填写正确的手机号");
        }
        if (redisCache.getCacheObject(SMS_COOLDOWN_KEY + p) != null)
        {
            throw new ServiceException("验证码已发送，请 60 秒后再试");
        }
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        sms.sendVerifyCode(p, code);
        redisCache.setCacheObject(SMS_COOLDOWN_KEY + p, "1", SMS_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        redisCache.setCacheObject(SMS_KEY + user.getAppUserId(), p + ":" + code + ":0", SMS_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserVo bindPhone(AppAuthBody body)
    {
        AbrAppUser user = requireUser();
        if (body == null || !Boolean.TRUE.equals(body.getConsent()))
        {
            throw new ServiceException("请先阅读并单独同意手机号的使用说明");
        }
        policyService.checkConsentVersion(IAbrPolicyService.PRIVACY_POLICY, body.getPolicyVersion());
        String p = StringUtils.trimToEmpty(body.getPhone());
        if (!PHONE.matcher(p).matches())
        {
            throw new ServiceException("请填写正确的手机号");
        }
        String key = SMS_KEY + user.getAppUserId();
        Object v = redisCache.getCacheObject(key);
        String[] parts = v == null ? new String[0] : String.valueOf(v).split(":");
        if (parts.length != 3 || !parts[0].equals(p))
        {
            throw new ServiceException("验证码已失效，请重新获取");
        }
        int tries = Integer.parseInt(parts[2]) + 1;
        if (!parts[1].equals(StringUtils.trimToEmpty(body.getSmsCode())))
        {
            if (tries >= SMS_MAX_TRIES)
            {
                redisCache.deleteObject(key);
                throw new ServiceException("验证码错误次数过多，请重新获取");
            }
            redisCache.setCacheObject(key, parts[0] + ":" + parts[1] + ":" + tries, SMS_MINUTES, TimeUnit.MINUTES);
            throw new ServiceException("验证码不正确");
        }
        redisCache.deleteObject(key);
        AbrAppUser other = userMapper.selectByPhone(p);
        if (other != null && !other.getAppUserId().equals(user.getAppUserId()))
        {
            throw new ServiceException("该手机号已绑定其他账号，请用原来的微信登录");
        }
        userMapper.bindPhone(user.getAppUserId(), p, body.getPolicyVersion());
        AbrVisitor pv = user.getPrimaryVisitorId() == null ? null : visitorMapper.selectAbrVisitorById(user.getPrimaryVisitorId());
        if (pv != null)
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("policy_version", body.getPolicyVersion());
            payload.put("rebind", StringUtils.isNotBlank(user.getPhone()));
            eventService.recordAction(pv, EVENT_PHONE_BOUND, "app_user", user.getAppUserId(), payload);
        }
        return vo(userMapper.selectAbrAppUserById(user.getAppUserId()));
    }

    @Override
    public List<AbrAppUser> selectAbrAppUserList(AbrAppUser user)
    {
        List<AbrAppUser> list = userMapper.selectAbrAppUserList(user);
        list.forEach(AbrAppAuthServiceImpl::mask);
        return list;
    }

    @Override
    public AbrAppUser selectAbrAppUserById(Long appUserId)
    {
        AbrAppUser u = userMapper.selectAbrAppUserById(appUserId);
        if (u != null)
        {
            mask(u);
        }
        return u;
    }

    @Override
    public String selectPhone(Long appUserId)
    {
        AbrAppUser u = userMapper.selectAbrAppUserById(appUserId);
        if (u == null)
        {
            throw new ServiceException("账号不存在");
        }
        return StringUtils.defaultString(u.getPhone());
    }

    @Override
    public int updateStatus(Long appUserId, String status, String operator)
    {
        if (!"0".equals(status) && !"1".equals(status))
        {
            throw new ServiceException("状态只能是 0 正常 / 1 停用");
        }
        if (userMapper.selectAbrAppUserById(appUserId) == null)
        {
            throw new ServiceException("账号不存在");
        }
        return userMapper.updateStatus(appUserId, status, operator);
    }

    private AbrAppUser requireUser()
    {
        Long id = currentAppUserId();
        if (id == null)
        {
            throw new ServiceException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        return userMapper.selectAbrAppUserById(id);
    }

    /**
     * 管理端返回前脱敏：手机号中间 4 位为 *，openid 不返回（实体上已忽略序列化），给出绑定情况
     */
    static void mask(AbrAppUser u)
    {
        u.setPhoneBound(StringUtils.isNotBlank(u.getPhone()));
        u.setOaBound(StringUtils.isNotBlank(u.getOaOpenid()));
        u.setMiniBound(StringUtils.isNotBlank(u.getMiniOpenid()));
        u.setPhone(maskPhone(u.getPhone()));
    }

    static String maskPhone(String phone)
    {
        return StringUtils.isBlank(phone) || phone.length() < 7 ? "" : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    static AppUserVo vo(AbrAppUser u)
    {
        boolean wechat = StringUtils.isNotBlank(u.getOaOpenid()) || StringUtils.isNotBlank(u.getMiniOpenid());
        return new AppUserVo(StringUtils.defaultString(u.getNickname()), maskPhone(u.getPhone()), StringUtils.isNotBlank(u.getPhone()), wechat,
                u.getCreateTime() == null ? "" : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, u.getCreateTime()));
    }

    private static String random(int bytes)
    {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static HttpServletRequest request()
    {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)
        {
            return a.getRequest();
        }
        return null;
    }
}

package com.ygb.abroad.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppVisitorBody;
import com.ygb.abroad.mapper.AbrVisitorMapper;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.constant.HttpStatus;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 访客 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrVisitorServiceImpl implements IAbrVisitorService
{
    /** 渠道码、内容编号格式（与问卷 hidden_fields.channel_code 一致） */
    static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    /** 支持的端 */
    static final Set<String> PLATFORMS = Set.of("h5", "mp_weixin");

    /** 默认渠道 */
    static final String DEFAULT_CHANNEL = "direct";

    /** 令牌格式：32 字节随机数的 base64url（43 位） */
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private AbrVisitorMapper visitorMapper;

    @Override
    public String enterVisitor(String existingToken, AppVisitorBody body)
    {
        AbrVisitor existing = findVisitor(existingToken);
        if (existing != null)
        {
            visitorMapper.touchAbrVisitor(existing.getVisitorId());
            return existingToken;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AbrVisitor visitor = new AbrVisitor();
        visitor.setTokenHash(hash(token));
        visitor.setChannelCode(channel(body == null ? null : body.getChannelCode()));
        visitor.setContentCode(code(body == null ? null : body.getContentCode(), ""));
        visitor.setClientPlatform(platform(body == null ? null : body.getClientPlatform()));
        visitorMapper.insertAbrVisitor(visitor);
        return token;
    }

    @Override
    public AbrVisitor resolveVisitor(String token)
    {
        AbrVisitor visitor = findVisitor(token);
        if (visitor == null)
        {
            throw new ServiceException("访客身份已失效，请刷新页面后重试", HttpStatus.UNAUTHORIZED);
        }
        visitorMapper.touchAbrVisitor(visitor.getVisitorId());
        return visitor;
    }

    @Override
    public void mergeVisitorProfile(Long visitorId, String patch)
    {
        visitorMapper.mergeAbrVisitorProfile(visitorId, patch);
    }

    @Override
    public void clearVisitorProfile(Long visitorId)
    {
        visitorMapper.clearAbrVisitorProfile(visitorId);
    }

    private AbrVisitor findVisitor(String token)
    {
        if (StringUtils.isBlank(token) || !TOKEN.matcher(token).matches())
        {
            return null;
        }
        return visitorMapper.selectAbrVisitorByTokenHash(hash(token));
    }

    /**
     * 渠道码：格式不对按 direct 记录
     * 
     * @param value 原值
     * @return 规范化后的渠道码
     */
    static String channel(String value)
    {
        return code(value, DEFAULT_CHANNEL);
    }

    /**
     * 编码类参数：格式不对用默认值
     * 
     * @param value 原值
     * @param defaultValue 默认值
     * @return 规范化后的值
     */
    static String code(String value, String defaultValue)
    {
        return value != null && CODE.matcher(value).matches() ? value : defaultValue;
    }

    /**
     * 端：只接受 h5 / mp_weixin
     * 
     * @param value 原值
     * @return 规范化后的值
     */
    static String platform(String value)
    {
        return value != null && PLATFORMS.contains(value) ? value : "";
    }

    /**
     * 令牌 SHA-256（十六进制）
     * 
     * @param token 令牌
     * @return 哈希
     */
    static String hash(String token)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }
}

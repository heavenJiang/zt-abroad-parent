package com.ygb.abroad.service;

import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppVisitorBody;

/**
 * 访客 服务层（C 端匿名身份）
 * 
 * 访客令牌由服务端生成，只返回给前端一次，库里只存 SHA-256；前端在请求头 X-Visitor-Token 中携带。
 * 
 * @author ygb
 */
public interface IAbrVisitorService
{
    /** 请求头名称 */
    public static final String HEADER = "X-Visitor-Token";

    /**
     * 进入 C 端时获取访客令牌：已有有效令牌则沿用，否则新建访客
     * 
     * @param existingToken 前端已保存的令牌（可为空）
     * @param body 入口参数（渠道码、内容编号、端）
     * @return 访客令牌
     */
    public String enterVisitor(String existingToken, AppVisitorBody body);

    /**
     * 按令牌识别访客
     * 
     * @param token 访客令牌
     * @return 访客；令牌无效时抛出 401 业务异常
     */
    public AbrVisitor resolveVisitor(String token);

    /**
     * 合并访客渐进档案
     * 
     * @param visitorId 访客ID
     * @param patch 档案增量（JSON 对象文本）
     */
    public void mergeVisitorProfile(Long visitorId, String patch);

    /**
     * 清空访客渐进档案（未满 14 周岁终止测评时，不保留任何已填答案）
     * 
     * @param visitorId 访客ID
     */
    public void clearVisitorProfile(Long visitorId);
}

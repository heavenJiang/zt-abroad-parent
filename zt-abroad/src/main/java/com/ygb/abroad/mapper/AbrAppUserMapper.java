package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrAppUser;

/**
 * 学生账号 数据层
 *
 * @author ygb
 */
public interface AbrAppUserMapper
{
    /**
     * 查询账号列表（管理端；状态、渠道精确，称呼模糊，params.beginTime / endTime 注册时间）
     *
     * @param user 查询条件
     * @return 账号集合
     */
    public List<AbrAppUser> selectAbrAppUserList(AbrAppUser user);

    /**
     * 按ID查询账号
     *
     * @param appUserId 账号ID
     * @return 账号
     */
    public AbrAppUser selectAbrAppUserById(Long appUserId);

    /**
     * 按 unionid 查询账号
     *
     * @param unionid unionid
     * @return 账号
     */
    public AbrAppUser selectByUnionid(String unionid);

    /**
     * 按服务号 openid 查询账号
     *
     * @param openid openid
     * @return 账号
     */
    public AbrAppUser selectByOaOpenid(String openid);

    /**
     * 按手机号查询账号
     *
     * @param phone 手机号
     * @return 账号
     */
    public AbrAppUser selectByPhone(String phone);

    /**
     * 新增账号
     *
     * @param user 账号
     * @return 结果
     */
    public int insertAbrAppUser(AbrAppUser user);

    /**
     * 记录登录：次数加一、最近登录时间；补上之前没有的 openid / unionid
     *
     * @param appUserId 账号ID
     * @param oaOpenid 服务号 openid（可为 null）
     * @param unionid unionid（可为 null）
     * @return 结果
     */
    public int updateLogin(@Param("appUserId") Long appUserId, @Param("oaOpenid") String oaOpenid, @Param("unionid") String unionid);

    /**
     * 设置主访客（只在还没有主访客时）
     *
     * @param appUserId 账号ID
     * @param visitorId 访客ID
     * @return 结果
     */
    public int setPrimaryVisitor(@Param("appUserId") Long appUserId, @Param("visitorId") Long visitorId);

    /**
     * 绑定手机号与单独同意
     *
     * @param appUserId 账号ID
     * @param phone 手机号
     * @param consentVersion 同意时的隐私政策版本
     * @return 结果
     */
    public int bindPhone(@Param("appUserId") Long appUserId, @Param("phone") String phone, @Param("consentVersion") String consentVersion);

    /**
     * 停用 / 启用
     *
     * @param appUserId 账号ID
     * @param status 状态
     * @param updateBy 操作人
     * @return 结果
     */
    public int updateStatus(@Param("appUserId") Long appUserId, @Param("status") String status, @Param("updateBy") String updateBy);
}

package com.ygb.abroad.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrMessageSend;
import com.ygb.abroad.domain.AbrMessageTemplate;

/**
 * 消息模板与发送记录 数据层
 *
 * @author ygb
 */
public interface AbrMessageMapper
{
    /**
     * 查询消息模板列表
     *
     * @param template 条件
     * @return 列表
     */
    public List<AbrMessageTemplate> selectTemplateList(AbrMessageTemplate template);

    /**
     * 查询消息模板
     *
     * @param templateId 模板ID
     * @return 模板
     */
    public AbrMessageTemplate selectTemplateById(Long templateId);

    /**
     * 某业务编码下启用的模板（各渠道）
     *
     * @param templateCode 业务编码
     * @return 模板
     */
    public List<AbrMessageTemplate> selectEnabledByCode(String templateCode);

    /**
     * 同一业务编码与渠道的模板数（排除自己）
     *
     * @param templateCode 业务编码
     * @param channel 渠道
     * @param templateId 排除的模板ID（新增为 null）
     * @return 数量
     */
    public int countTemplate(@Param("templateCode") String templateCode, @Param("channel") String channel, @Param("templateId") Long templateId);

    /**
     * 新增消息模板
     *
     * @param template 模板
     * @return 行数
     */
    public int insertTemplate(AbrMessageTemplate template);

    /**
     * 修改消息模板
     *
     * @param template 模板
     * @return 行数
     */
    public int updateTemplate(AbrMessageTemplate template);

    /**
     * 删除消息模板
     *
     * @param templateIds 模板ID
     * @return 行数
     */
    public int deleteTemplateByIds(Long[] templateIds);

    /**
     * 写发送记录
     *
     * @param send 记录
     * @return 行数
     */
    public int insertSend(AbrMessageSend send);

    /**
     * 查询发送记录
     *
     * @param send 条件（params[beginTime] / params[endTime]）
     * @return 列表
     */
    public List<AbrMessageSend> selectSendList(AbrMessageSend send);

    /**
     * 学生账号的接收地址（服务号 openid、小程序 openid、手机号）
     *
     * @param appUserId 学生账号ID
     * @return oaOpenid、miniOpenid、phone、status（不存在为 null）
     */
    public Map<String, Object> selectAppUserAddress(Long appUserId);

    /**
     * 线索的接收地址（合并后的主线索：学生账号、手机号）
     *
     * @param leadId 线索ID
     * @return appUserId、phone（不存在为 null）
     */
    public Map<String, Object> selectLeadAddress(Long leadId);

    /**
     * 访客关联的学生账号
     *
     * @param visitorId 访客ID
     * @return 学生账号ID（没有为 null）
     */
    public Long selectVisitorAppUser(Long visitorId);

    /**
     * 员工的企业微信成员账号（取登录名；须为在用员工）
     *
     * @param userId 员工ID
     * @return 账号（不存在为 null）
     */
    public String selectStaffWecomAccount(Long userId);

    /**
     * 有某个权限的在用员工（含超级管理员）
     *
     * @param perms 权限标识
     * @return 员工ID
     */
    public List<Long> selectUsersWithPerm(String perms);
}

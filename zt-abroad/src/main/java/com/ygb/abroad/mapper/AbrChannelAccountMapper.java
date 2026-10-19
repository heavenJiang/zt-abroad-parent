package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrChannelAccount;

/**
 * 渠道账号 数据层
 * 
 * @author ygb
 */
public interface AbrChannelAccountMapper
{
    /**
     * 查询账号列表
     * 
     * @param account 查询条件（平台、名称或编码模糊、人设、暂停、状态）
     * @return 账号集合
     */
    public List<AbrChannelAccount> selectAbrChannelAccountList(AbrChannelAccount account);

    /**
     * 按ID查询账号
     * 
     * @param accountId 账号ID
     * @return 账号
     */
    public AbrChannelAccount selectAbrChannelAccountById(Long accountId);

    /**
     * 按编码查询账号（校验唯一、熔断暂停）
     * 
     * @param accountCode 账号编码
     * @return 账号
     */
    public AbrChannelAccount selectAbrChannelAccountByCode(String accountCode);

    /**
     * 新增账号
     * 
     * @param account 账号
     * @return 结果
     */
    public int insertAbrChannelAccount(AbrChannelAccount account);

    /**
     * 修改账号（不改暂停状态）
     * 
     * @param account 账号
     * @return 结果
     */
    public int updateAbrChannelAccount(AbrChannelAccount account);

    /**
     * 批量删除账号
     * 
     * @param accountIds 账号ID
     * @return 结果
     */
    public int deleteAbrChannelAccountByIds(Long[] accountIds);

    /**
     * 引用这些账号的内容与发布记录数
     * 
     * @param accountIds 账号ID
     * @return 引用数
     */
    public int countAccountReferences(Long[] accountIds);

    /**
     * 暂停账号（已暂停时不变）
     * 
     * @param accountCode 账号编码
     * @param reason 原因
     * @param pausedBy 暂停人
     * @return 影响行数（已暂停为 0）
     */
    public int pauseAccount(@Param("accountCode") String accountCode, @Param("reason") String reason, @Param("pausedBy") String pausedBy);

    /**
     * 恢复账号（未暂停时不变）
     * 
     * @param accountCode 账号编码
     * @param updateBy 恢复人
     * @return 影响行数（未暂停为 0）
     */
    public int resumeAccount(@Param("accountCode") String accountCode, @Param("updateBy") String updateBy);

    /**
     * 账号某段时间内的发布数（待发布按计划时间、已发布按实际时间；失败与撤回不计）
     * 
     * @param accountId 账号ID
     * @param from 起点（含）
     * @param to 终点（不含）
     * @param excludeRecordId 不计入的发布记录（修改时排除自己，可为 null）
     * @return 条数
     */
    public int countPublishBetween(@Param("accountId") Long accountId, @Param("from") Date from, @Param("to") Date to,
            @Param("excludeRecordId") Long excludeRecordId);
}

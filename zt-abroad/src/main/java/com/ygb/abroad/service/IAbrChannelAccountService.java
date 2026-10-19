package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrChannelAccount;

/**
 * 渠道账号 服务层（账号矩阵、账号级暂停）
 * 
 * @author ygb
 */
public interface IAbrChannelAccountService
{
    /**
     * 查询账号列表
     * 
     * @param account 查询条件
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
     * 账号编码是否唯一
     * 
     * @param account 账号
     * @return true 唯一
     */
    public boolean checkAccountCodeUnique(AbrChannelAccount account);

    /**
     * 新增账号（校验平台、人设、授权与发布方式）
     * 
     * @param account 账号
     * @return 结果
     */
    public int insertAbrChannelAccount(AbrChannelAccount account);

    /**
     * 修改账号
     * 
     * @param account 账号
     * @return 结果
     */
    public int updateAbrChannelAccount(AbrChannelAccount account);

    /**
     * 删除账号（已有内容或发布记录时不能删除）
     * 
     * @param accountIds 账号ID
     * @return 结果
     */
    public int deleteAbrChannelAccountByIds(Long[] accountIds);

    /**
     * 暂停账号（不能安排新的发布）
     * 
     * @param accountId 账号ID
     * @param reason 原因
     * @param operator 操作人
     * @return 是否有变化
     */
    public boolean pauseAccount(Long accountId, String reason, String operator);

    /**
     * 恢复账号（同时把它未处理的熔断记录标为已处理）
     * 
     * @param accountId 账号ID
     * @param operator 操作人
     * @return 是否有变化
     */
    public boolean resumeAccount(Long accountId, String operator);
}

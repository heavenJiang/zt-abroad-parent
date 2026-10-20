package com.ygb.abroad.service;

/**
 * 院校库字段回滚 服务层（M2-09）
 * 
 * @author ygb
 */
public interface IAbrFieldRollbackService
{
    /**
     * 把字段恢复为这次变化之前的值（只能回滚该字段最近一次仍在生效的变化），回滚本身也记一条历史
     *
     * @param historyId 历史ID
     * @param operator 操作人
     * @return 结果
     */
    public int rollback(Long historyId, String operator);
}

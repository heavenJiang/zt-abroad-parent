package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrGrowthTrigger;

/**
 * 编排触发规则 服务层
 * 
 * @author ygb
 */
public interface IAbrGrowthTriggerService
{
    /**
     * 查询触发规则列表
     * 
     * @param trigger 查询条件
     * @return 触发规则集合
     */
    public List<AbrGrowthTrigger> selectAbrGrowthTriggerList(AbrGrowthTrigger trigger);

    /**
     * 按ID查询触发规则
     * 
     * @param triggerId 触发规则ID
     * @return 触发规则
     */
    public AbrGrowthTrigger selectAbrGrowthTriggerById(Long triggerId);

    /**
     * 新增触发规则（校验事件过滤、定时表达式、Agent）
     * 
     * @param trigger 触发规则
     * @return 结果
     */
    public int insertAbrGrowthTrigger(AbrGrowthTrigger trigger);

    /**
     * 修改触发规则
     * 
     * @param trigger 触发规则
     * @return 结果
     */
    public int updateAbrGrowthTrigger(AbrGrowthTrigger trigger);

    /**
     * 批量删除触发规则（已生成的任务保留）
     * 
     * @param triggerIds 触发规则ID
     * @return 结果
     */
    public int deleteAbrGrowthTriggerByIds(Long[] triggerIds);
}

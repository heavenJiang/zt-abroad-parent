package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrGrowthTrigger;
import com.ygb.abroad.mapper.AbrGrowthTriggerMapper;
import com.ygb.abroad.mapper.AbrPlaybookMapper;
import com.ygb.abroad.service.IAbrGrowthTriggerService;
import com.ygb.common.exception.ServiceException;

/**
 * 编排触发规则 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrGrowthTriggerServiceImpl implements IAbrGrowthTriggerService
{
    @Autowired
    private AbrGrowthTriggerMapper triggerMapper;

    @Autowired
    private AbrPlaybookMapper playbookMapper;

    @Override
    public List<AbrGrowthTrigger> selectAbrGrowthTriggerList(AbrGrowthTrigger trigger)
    {
        return triggerMapper.selectAbrGrowthTriggerList(trigger);
    }

    @Override
    public AbrGrowthTrigger selectAbrGrowthTriggerById(Long triggerId)
    {
        return triggerMapper.selectAbrGrowthTriggerById(triggerId);
    }

    @Override
    public int insertAbrGrowthTrigger(AbrGrowthTrigger trigger)
    {
        check(trigger);
        return triggerMapper.insertAbrGrowthTrigger(trigger);
    }

    @Override
    public int updateAbrGrowthTrigger(AbrGrowthTrigger trigger)
    {
        if (trigger.getTriggerId() == null || triggerMapper.selectAbrGrowthTriggerById(trigger.getTriggerId()) == null)
        {
            throw new ServiceException("触发规则不存在");
        }
        check(trigger);
        return triggerMapper.updateAbrGrowthTrigger(trigger);
    }

    @Override
    public int deleteAbrGrowthTriggerByIds(Long[] triggerIds)
    {
        return triggerMapper.deleteAbrGrowthTriggerByIds(triggerIds);
    }

    private void check(AbrGrowthTrigger trigger)
    {
        boolean exists = trigger.getPlaybookId() == null || playbookMapper.selectAbrPlaybookById(trigger.getPlaybookId()) != null;
        AbrGrowthConfigValidator.checkTrigger(trigger, AbrPlaybookServiceImpl.dictValues("abr_growth_agent"), exists);
    }
}

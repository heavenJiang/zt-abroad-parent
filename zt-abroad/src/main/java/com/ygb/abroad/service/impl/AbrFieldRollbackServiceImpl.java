package com.ygb.abroad.service.impl;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrFieldHistory;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.mapper.AbrSourceArchiveMapper;
import com.ygb.abroad.service.IAbrFieldRollbackService;
import com.ygb.abroad.service.IAbrProgramService;
import com.ygb.abroad.service.IAbrSchoolService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.core.domain.BaseEntity;
import com.ygb.common.exception.ServiceException;

/**
 * 院校库字段回滚 服务层实现：取当前记录、把字段改回旧值，再走原来的修改流程（同样校验、记来源与历史）
 * 
 * @author ygb
 */
@Service
public class AbrFieldRollbackServiceImpl implements IAbrFieldRollbackService
{
    @Autowired
    private AbrSourceArchiveMapper archiveMapper;

    @Autowired
    private IAbrSchoolService schoolService;

    @Autowired
    private IAbrProgramService programService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollback(Long historyId, String operator)
    {
        AbrFieldHistory h = historyId == null ? null : archiveMapper.selectHistoryById(historyId);
        if (h == null || !"applied".equals(h.getStatus()))
        {
            throw new ServiceException("只能回滚仍在生效的变化");
        }
        if (h.getOldValue() == null && "import".equals(h.getChangeMode()))
        {
            throw new ServiceException("建档时的初始值不能回滚，请直接修改");
        }
        BaseEntity entity = switch (h.getTableName())
        {
            case AbrSchoolServiceImpl.SCHOOL_TABLE -> schoolService.selectAbrSchoolById(h.getRecordId());
            case AbrProgramServiceImpl.PROGRAM_TABLE -> programService.selectAbrProgramById(h.getRecordId());
            case AbrProgramServiceImpl.REQUIREMENT_TABLE -> programService.selectAbrRequirementById(h.getRecordId());
            default -> throw new ServiceException("不支持回滚的表：" + h.getTableName());
        };
        if (entity == null)
        {
            throw new ServiceException("记录不存在或已删除");
        }
        // 改副本：同一事务内 MyBatis 一级缓存会把同一个对象交给修改流程，直接改原对象会让修改流程比不出变化
        BaseEntity loaded = entity;
        entity = BeanUtils.instantiateClass(loaded.getClass());
        BeanUtils.copyProperties(loaded, entity, "params");
        BeanWrapperImpl bean = new BeanWrapperImpl(entity);
        String current = AbrSourceArchiveServiceImpl.text(bean.getPropertyValue(h.getFieldName()));
        if (!AbrSourceArchiveServiceImpl.same(current, h.getNewValue()))
        {
            throw new ServiceException("这个字段之后又改过，请回滚最近一次的变化");
        }
        bean.setPropertyValue(h.getFieldName(), h.getOldValue());
        entity.getParams().put(IAbrSourceArchiveService.PARAM_CHANGE_MODE, "rollback");
        entity.setUpdateBy(operator);
        int rows;
        if (entity instanceof AbrSchool s)
        {
            rows = schoolService.updateAbrSchool(s);
        }
        else if (entity instanceof AbrProgram p)
        {
            rows = programService.updateAbrProgram(p);
        }
        else
        {
            rows = programService.updateAbrRequirement((AbrRequirement) entity);
        }
        archiveMapper.markRolledBack(historyId);
        return rows;
    }
}

package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.mapper.AbrCalendarEntryMapper;
import com.ygb.abroad.mapper.AbrPlaybookMapper;
import com.ygb.abroad.service.IAbrCalendarService;
import com.ygb.common.exception.ServiceException;

/**
 * 营销日历 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrCalendarServiceImpl implements IAbrCalendarService
{
    @Autowired
    private AbrCalendarEntryMapper entryMapper;

    @Autowired
    private AbrPlaybookMapper playbookMapper;

    @Override
    public List<AbrCalendarEntry> selectAbrCalendarEntryList(AbrCalendarEntry entry)
    {
        return entryMapper.selectAbrCalendarEntryList(entry);
    }

    @Override
    public AbrCalendarEntry selectAbrCalendarEntryById(Long entryId)
    {
        return entryMapper.selectAbrCalendarEntryById(entryId);
    }

    @Override
    public int insertAbrCalendarEntry(AbrCalendarEntry entry)
    {
        check(entry);
        return entryMapper.insertAbrCalendarEntry(entry);
    }

    @Override
    public int updateAbrCalendarEntry(AbrCalendarEntry entry)
    {
        if (entry.getEntryId() == null || entryMapper.selectAbrCalendarEntryById(entry.getEntryId()) == null)
        {
            throw new ServiceException("日历条目不存在");
        }
        check(entry);
        return entryMapper.updateAbrCalendarEntry(entry);
    }

    @Override
    public int deleteAbrCalendarEntryByIds(Long[] entryIds)
    {
        return entryMapper.deleteAbrCalendarEntryByIds(entryIds);
    }

    private void check(AbrCalendarEntry entry)
    {
        boolean exists = entry.getPlaybookId() != null && playbookMapper.selectAbrPlaybookById(entry.getPlaybookId()) != null;
        AbrGrowthConfigValidator.checkCalendarEntry(entry, exists);
    }
}

package com.ygb.abroad.service.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.abroad.mapper.AbrPlaybookMapper;
import com.ygb.abroad.service.IAbrPlaybookService;
import com.ygb.common.core.domain.entity.SysDictData;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;

/**
 * 营销剧本 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrPlaybookServiceImpl implements IAbrPlaybookService
{
    @Autowired
    private AbrPlaybookMapper playbookMapper;

    @Override
    public List<AbrPlaybook> selectAbrPlaybookList(AbrPlaybook playbook)
    {
        return playbookMapper.selectAbrPlaybookList(playbook);
    }

    @Override
    public AbrPlaybook selectAbrPlaybookById(Long playbookId)
    {
        return playbookMapper.selectAbrPlaybookById(playbookId);
    }

    @Override
    public boolean checkPlaybookCodeUnique(AbrPlaybook playbook)
    {
        AbrPlaybook exist = playbookMapper.checkPlaybookCodeUnique(playbook.getPlaybookCode());
        return exist == null || exist.getPlaybookId().equals(playbook.getPlaybookId());
    }

    @Override
    public int insertAbrPlaybook(AbrPlaybook playbook)
    {
        check(playbook);
        return playbookMapper.insertAbrPlaybook(playbook);
    }

    @Override
    public int updateAbrPlaybook(AbrPlaybook playbook)
    {
        if (playbook.getPlaybookId() == null || playbookMapper.selectAbrPlaybookById(playbook.getPlaybookId()) == null)
        {
            throw new ServiceException("剧本不存在");
        }
        check(playbook);
        return playbookMapper.updateAbrPlaybook(playbook);
    }

    @Override
    public int deleteAbrPlaybookByIds(Long[] playbookIds)
    {
        if (playbookMapper.countPlaybookReferences(playbookIds) > 0)
        {
            throw new ServiceException("剧本已被营销日历或触发规则引用，不能删除（可改为停用）");
        }
        return playbookMapper.deleteAbrPlaybookByIds(playbookIds);
    }

    private void check(AbrPlaybook playbook)
    {
        AbrGrowthConfigValidator.checkPlaybook(playbook, dictValues("abr_degree"), dictValues("abr_country"), dictValues("abr_hook_tool"),
                dictValues("abr_content_kind"));
    }

    /**
     * 字典的全部值（含停用的，避免停用后已有配置无法保存）
     */
    static Set<String> dictValues(String dictType)
    {
        List<SysDictData> datas = DictUtils.getDictCache(dictType);
        return datas == null ? Set.of() : datas.stream().map(SysDictData::getDictValue).collect(Collectors.toSet());
    }
}

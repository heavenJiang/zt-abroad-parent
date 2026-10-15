package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrToolRun;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppPlanItemBody;
import com.ygb.abroad.domain.vo.AppPlanItemVo;
import com.ygb.abroad.mapper.AbrPlanItemMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrToolRunMapper;
import com.ygb.abroad.service.IAbrPlanService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;

/**
 * 我的选校单 服务层实现
 * 
 * 档位不信任前端：从快测加入时由后端按运行记录取该项目的分组（其他工具不分档）；直接加入（项目页等）不带档位，M4-10 再按最新档案计算。
 * 
 * @author ygb
 */
@Service
public class AbrPlanServiceImpl implements IAbrPlanService
{
    /** 来源：快测 */
    static final String SOURCE_QUICK = "quick";

    /** 来源：其他入口工具（本校认可度、雅思够不够、现在还能申） */
    static final String SOURCE_TOOL = "tool";

    /** 来源：项目页等直接加入 */
    static final String SOURCE_PROGRAM = "program";

    @Autowired
    private AbrPlanItemMapper planItemMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrToolRunMapper toolRunMapper;

    @Override
    public List<AppPlanItemVo> selectPlanItems(AbrVisitor visitor)
    {
        return planItemMapper.selectAbrPlanItemsByVisitor(visitor.getVisitorId()).stream().map(AbrPlanServiceImpl::toVo).toList();
    }

    @Override
    public List<AppPlanItemVo> addPlanItem(AbrVisitor visitor, AppPlanItemBody body)
    {
        AbrProgram program = programMapper.selectAbrProgramById(body.getProgramId());
        if (program == null || !"0".equals(program.getStatus()))
        {
            throw new ServiceException("项目不存在或已下线");
        }
        AbrPlanItem item = new AbrPlanItem();
        item.setVisitorId(visitor.getVisitorId());
        item.setAppUserId(visitor.getAppUserId());
        item.setProgramId(program.getProgramId());
        item.setSource(SOURCE_PROGRAM);
        if (body.getToolRunId() != null)
        {
            AbrToolRun run = toolRunMapper.selectAbrToolRunOwned(body.getToolRunId(), visitor.getVisitorId());
            if (run == null)
            {
                throw new ServiceException("工具记录不存在");
            }
            boolean quick = AbrQuickTool.TOOL.equals(run.getTool());
            item.setSource(quick ? SOURCE_QUICK : SOURCE_TOOL);
            item.setSourceRef(run.getToolRunId());
            // 只有快测给出档位；其他工具不分档
            item.setTierSnapshot(quick ? AbrQuickTool.groupOf(run.getResult(), program.getProgramId()) : null);
        }
        boolean exists = planItemMapper.selectAbrPlanItemsByVisitor(visitor.getVisitorId()).stream()
                .anyMatch(i -> i.getProgramId().equals(program.getProgramId()));
        if (!exists && planItemMapper.countAbrPlanItemsByVisitor(visitor.getVisitorId()) >= MAX_ITEMS)
        {
            throw new ServiceException("选校单最多 " + MAX_ITEMS + " 个项目，请先移除一些");
        }
        planItemMapper.upsertAbrPlanItem(item);
        return selectPlanItems(visitor);
    }

    @Override
    public List<AppPlanItemVo> removePlanItem(AbrVisitor visitor, Long programId)
    {
        planItemMapper.deleteAbrPlanItem(visitor.getVisitorId(), programId);
        return selectPlanItems(visitor);
    }

    private static AppPlanItemVo toVo(AbrPlanItem item)
    {
        AppPlanItemVo vo = new AppPlanItemVo();
        vo.setProgramId(item.getProgramId());
        vo.setSchoolName(item.getSchoolName());
        vo.setProgramName(item.getProgramName());
        vo.setCountry(item.getCountry());
        vo.setCountryLabel(StringUtils.defaultIfBlank(DictUtils.getDictLabel("abr_country", item.getCountry()), item.getCountry()));
        vo.setTier(item.getTierSnapshot());
        vo.setSource(item.getSource());
        vo.setAddedTime(item.getCreateTime());
        return vo;
    }
}

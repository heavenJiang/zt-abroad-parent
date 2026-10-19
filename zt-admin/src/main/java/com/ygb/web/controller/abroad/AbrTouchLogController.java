package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrTouchLog;
import com.ygb.abroad.service.IAbrGrowthControlService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.StringUtils;

/**
 * 营销编排器-触达记录与退订
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/orchestrator/touch")
public class AbrTouchLogController extends BaseController
{
    @Autowired
    private IAbrGrowthControlService controlService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    /**
     * 查询触达记录
     */
    @PreAuthorize("@ss.hasPermi('abroad:touchLog:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrTouchLog log)
    {
        startPage();
        List<AbrTouchLog> list = controlService.selectTouchLogList(log);
        return getDataTable(list);
    }

    /**
     * 员工代为退订或恢复营销消息（如学生在电话中要求不再接收；body：targetType、targetId、optout、reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:touchLog:optout')")
    @Log(title = "营销退订", businessType = BusinessType.UPDATE)
    @PutMapping("/optout")
    public AjaxResult optout(@RequestBody Map<String, Object> body)
    {
        String targetType = StringUtils.trimToEmpty((String) body.get("targetType"));
        Object id = body.get("targetId");
        if (!targetType.matches("^(visitor|app_user|lead)$") || !(id instanceof Number))
        {
            return error("对象类型只能是 visitor / app_user / lead，且须填写对象ID");
        }
        boolean optout = !Boolean.FALSE.equals(body.get("optout"));
        boolean changed = guardService.setMarketingOptout(targetType, ((Number) id).longValue(), optout, "admin", getUsername(),
                (String) body.get("reason"));
        return success(changed ? (optout ? "已退订营销消息" : "已恢复订阅") : "状态未变化");
    }
}

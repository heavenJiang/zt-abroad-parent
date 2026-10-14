package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.vo.AbrAiTrialBody;
import com.ygb.abroad.service.IAbrAiAgentService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * AI 管理-Agent 配置（Agent 在代码中定义，只能修改配置，不能新增、删除）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/aiAgent")
public class AbrAiAgentController extends BaseController
{
    @Autowired
    private IAbrAiAgentService agentService;

    /**
     * 查询 Agent 列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiAgent:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrAiAgent agent)
    {
        startPage();
        List<AbrAiAgent> list = agentService.selectAbrAiAgentList(agent);
        return getDataTable(list);
    }

    /**
     * 获取 Agent 详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiAgent:query')")
    @GetMapping(value = "/{agentId}")
    public AjaxResult getInfo(@PathVariable Long agentId)
    {
        return success(agentService.selectAbrAiAgentById(agentId));
    }

    /**
     * 修改 Agent 配置
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiAgent:edit')")
    @Log(title = "Agent 配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrAiAgent agent)
    {
        agent.setUpdateBy(getUsername());
        return toAjax(agentService.updateAbrAiAgent(agent));
    }

    /**
     * 试用 Agent：输入经网关完整执行一次（记调用日志，业务类型 trial）
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiAgent:edit')")
    @Log(title = "Agent 试用", businessType = BusinessType.OTHER)
    @PostMapping("/test/{agentCode}")
    public AjaxResult test(@PathVariable String agentCode, @RequestBody AbrAiTrialBody body)
    {
        return success(agentService.trialAbrAiAgent(agentCode, body, getUsername()));
    }
}

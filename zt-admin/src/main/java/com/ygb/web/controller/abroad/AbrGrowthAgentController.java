package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrGrowthAgent;
import com.ygb.abroad.domain.AbrGrowthSetting;
import com.ygb.abroad.service.IAbrGrowthControlService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 营销编排器-Agent 运行与全局开关（执行模式、抽检、每天 AI 预算、每周触达上限、免打扰、一键暂停）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/orchestrator")
public class AbrGrowthAgentController extends BaseController
{
    @Autowired
    private IAbrGrowthControlService controlService;

    /**
     * 全局设置（带出今天的 AI 用量）
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthAgent:list')")
    @GetMapping("/setting")
    public AjaxResult setting()
    {
        return success(controlService.selectSetting());
    }

    /**
     * 修改全局设置
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthSetting:edit')")
    @Log(title = "获客全局设置", businessType = BusinessType.UPDATE)
    @PutMapping("/setting")
    public AjaxResult editSetting(@Validated @RequestBody AbrGrowthSetting setting)
    {
        setting.setUpdateBy(getUsername());
        return toAjax(controlService.updateSetting(setting));
    }

    /**
     * 一键暂停全部获客 Agent（body：reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthSetting:pause')")
    @Log(title = "获客全局暂停", businessType = BusinessType.UPDATE)
    @PutMapping("/setting/pause")
    public AjaxResult pauseGlobal(@RequestBody Map<String, String> body)
    {
        return controlService.pauseGlobal(body.get("reason"), getUsername()) ? success("已暂停全部获客 Agent") : error("已经是暂停状态");
    }

    /**
     * 恢复全部获客 Agent
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthSetting:pause')")
    @Log(title = "获客全局恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/setting/resume")
    public AjaxResult resumeGlobal()
    {
        return controlService.resumeGlobal(getUsername()) ? success("已恢复") : error("当前没有暂停");
    }

    /**
     * Agent 运行配置列表（带出今天的 AI 用量与不能运行的原因）
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthAgent:list')")
    @GetMapping("/agent/list")
    public TableDataInfo list(AbrGrowthAgent agent)
    {
        List<AbrGrowthAgent> list = controlService.selectAgentList(agent);
        return getDataTable(list);
    }

    /**
     * Agent 运行配置详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthAgent:query')")
    @GetMapping("/agent/{agentCode}")
    public AjaxResult getInfo(@PathVariable String agentCode)
    {
        return success(controlService.selectAgent(agentCode));
    }

    /**
     * 修改执行模式、抽检比例、预算
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthAgent:edit')")
    @Log(title = "获客 Agent 运行配置", businessType = BusinessType.UPDATE)
    @PutMapping("/agent")
    public AjaxResult edit(@Validated @RequestBody AbrGrowthAgent agent)
    {
        agent.setUpdateBy(getUsername());
        return toAjax(controlService.updateAgent(agent));
    }

    /**
     * 暂停一个 Agent（body：reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthAgent:pause')")
    @Log(title = "获客 Agent 暂停", businessType = BusinessType.UPDATE)
    @PutMapping("/agent/pause/{agentCode}")
    public AjaxResult pause(@PathVariable String agentCode, @RequestBody Map<String, String> body)
    {
        return controlService.pauseAgent(agentCode, body.get("reason"), getUsername()) ? success("已暂停") : error("已经是暂停状态");
    }

    /**
     * 恢复一个 Agent
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthAgent:pause')")
    @Log(title = "获客 Agent 恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/agent/resume/{agentCode}")
    public AjaxResult resume(@PathVariable String agentCode)
    {
        return controlService.resumeAgent(agentCode, getUsername()) ? success("已恢复") : error("当前没有暂停");
    }
}

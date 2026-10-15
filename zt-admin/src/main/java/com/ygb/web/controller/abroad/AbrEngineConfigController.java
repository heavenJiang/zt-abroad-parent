package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrEngineConfig;
import com.ygb.abroad.domain.vo.AbrEngineConfigBody;
import com.ygb.abroad.service.IAbrEngineConfigService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 测评与选校-引擎配置（上传 JSON 导入为草稿；顾问已确认的版本才能启用；版本不可修改）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/engine")
public class AbrEngineConfigController extends BaseController
{
    @Autowired
    private IAbrEngineConfigService engineConfigService;

    /**
     * 查询配置版本列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:engine:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrEngineConfig config)
    {
        startPage();
        List<AbrEngineConfig> list = engineConfigService.selectAbrEngineConfigList(config);
        return getDataTable(list);
    }

    /**
     * 获取配置版本详情（含配置 JSON）
     */
    @PreAuthorize("@ss.hasPermi('abroad:engine:query')")
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId)
    {
        return success(engineConfigService.selectAbrEngineConfigById(configId));
    }

    /**
     * 校验配置 JSON（导入前预览，不保存）
     */
    @PreAuthorize("@ss.hasPermi('abroad:engine:import')")
    @PostMapping("/check")
    public AjaxResult check(@Validated @RequestBody AbrEngineConfigBody body)
    {
        return success(engineConfigService.checkAbrEngineConfig(body.getConfig()));
    }

    /**
     * 导入配置版本（保存为草稿）
     */
    @PreAuthorize("@ss.hasPermi('abroad:engine:import')")
    @Log(title = "引擎配置", businessType = BusinessType.IMPORT)
    @PostMapping
    public AjaxResult importConfig(@Validated @RequestBody AbrEngineConfigBody body)
    {
        return success(engineConfigService.importAbrEngineConfig(body.getConfig(), body.getRemark(), getUsername()));
    }

    /**
     * 启用配置版本
     */
    @PreAuthorize("@ss.hasPermi('abroad:engine:activate')")
    @Log(title = "引擎配置启用", businessType = BusinessType.UPDATE)
    @PutMapping("/activate/{configId}")
    public AjaxResult activate(@PathVariable Long configId)
    {
        return toAjax(engineConfigService.activateAbrEngineConfig(configId, getUsername()));
    }

    /**
     * 删除配置草稿
     */
    @PreAuthorize("@ss.hasPermi('abroad:engine:remove')")
    @Log(title = "引擎配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        return toAjax(engineConfigService.deleteAbrEngineConfigByIds(configIds, getUsername()));
    }
}

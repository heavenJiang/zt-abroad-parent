package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrExperiment;
import com.ygb.abroad.service.IAbrExperimentService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * A/B 实验（M9-04：钩子文案）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/experiment")
public class AbrExperimentController extends BaseController
{
    @Autowired
    private IAbrExperimentService experimentService;

    /**
     * 实验列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrExperiment experiment)
    {
        startPage();
        List<AbrExperiment> list = experimentService.selectExperimentList(experiment);
        return getDataTable(list);
    }

    /**
     * 实验详情与当前评估
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:list')")
    @GetMapping("/{experimentId}")
    public AjaxResult getInfo(@PathVariable Long experimentId)
    {
        return success(experimentService.selectExperimentDetail(experimentId));
    }

    /**
     * 新增（草稿）
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:add')")
    @Log(title = "A/B 实验", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AbrExperiment experiment)
    {
        experiment.setCreateBy(getUsername());
        return success(experimentService.insertExperiment(experiment));
    }

    /**
     * 修改草稿
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:edit')")
    @Log(title = "A/B 实验", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AbrExperiment experiment)
    {
        experiment.setUpdateBy(getUsername());
        return toAjax(experimentService.updateExperiment(experiment));
    }

    /**
     * 开始
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:edit')")
    @Log(title = "A/B 实验开始", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{experimentId}")
    public AjaxResult start(@PathVariable Long experimentId)
    {
        return toAjax(experimentService.startExperiment(experimentId, getUsername()));
    }

    /**
     * 停止（不确定胜出组，之后用默认文末引导或上一个实验的胜出文案）
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:edit')")
    @Log(title = "A/B 实验停止", businessType = BusinessType.UPDATE)
    @PutMapping("/stop/{experimentId}")
    public AjaxResult stop(@PathVariable Long experimentId, @RequestBody Map<String, String> body)
    {
        return toAjax(experimentService.stopExperiment(experimentId, body.get("reason"), getUsername()));
    }

    /**
     * 结束并确定胜出组
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:edit')")
    @Log(title = "A/B 实验结束", businessType = BusinessType.UPDATE)
    @PutMapping("/conclude/{experimentId}")
    public AjaxResult conclude(@PathVariable Long experimentId, @RequestBody Map<String, String> body)
    {
        return toAjax(experimentService.concludeExperiment(experimentId, body.get("winner"), body.get("reason"), getUsername()));
    }

    /**
     * 删除草稿
     */
    @PreAuthorize("@ss.hasPermi('abroad:experiment:remove')")
    @Log(title = "A/B 实验", businessType = BusinessType.DELETE)
    @DeleteMapping("/{experimentIds}")
    public AjaxResult remove(@PathVariable Long[] experimentIds)
    {
        return toAjax(experimentService.deleteExperiments(experimentIds));
    }
}

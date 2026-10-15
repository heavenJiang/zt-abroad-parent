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
import com.ygb.abroad.domain.AbrProgramBenchmark;
import com.ygb.abroad.service.IAbrProgramBenchmarkService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 测评与选校-项目参考线（每个项目一条；没有参考线的项目按难度档取默认值）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/benchmark")
public class AbrProgramBenchmarkController extends BaseController
{
    @Autowired
    private IAbrProgramBenchmarkService benchmarkService;

    /**
     * 查询参考线列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:benchmark:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrProgramBenchmark benchmark)
    {
        startPage();
        List<AbrProgramBenchmark> list = benchmarkService.selectAbrProgramBenchmarkList(benchmark);
        return getDataTable(list);
    }

    /**
     * 获取参考线详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:benchmark:list')")
    @GetMapping(value = "/{benchmarkId}")
    public AjaxResult getInfo(@PathVariable Long benchmarkId)
    {
        return success(benchmarkService.selectAbrProgramBenchmarkById(benchmarkId));
    }

    /**
     * 新增参考线
     */
    @PreAuthorize("@ss.hasPermi('abroad:benchmark:add')")
    @Log(title = "项目参考线", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrProgramBenchmark benchmark)
    {
        benchmark.setCreateBy(getUsername());
        return toAjax(benchmarkService.insertAbrProgramBenchmark(benchmark));
    }

    /**
     * 修改参考线
     */
    @PreAuthorize("@ss.hasPermi('abroad:benchmark:edit')")
    @Log(title = "项目参考线", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrProgramBenchmark benchmark)
    {
        benchmark.setUpdateBy(getUsername());
        return toAjax(benchmarkService.updateAbrProgramBenchmark(benchmark));
    }

    /**
     * 删除参考线
     */
    @PreAuthorize("@ss.hasPermi('abroad:benchmark:remove')")
    @Log(title = "项目参考线", businessType = BusinessType.DELETE)
    @DeleteMapping("/{benchmarkIds}")
    public AjaxResult remove(@PathVariable Long[] benchmarkIds)
    {
        return toAjax(benchmarkService.deleteAbrProgramBenchmarkByIds(benchmarkIds, getUsername()));
    }
}

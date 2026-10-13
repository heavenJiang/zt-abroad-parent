package com.ygb.web.controller.abroad;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
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
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.service.IAbrProgramService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.poi.ExcelUtil;

/**
 * 院校库-项目
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/program")
public class AbrProgramController extends BaseController
{
    @Autowired
    private IAbrProgramService programService;

    /**
     * 查询项目列表（params[verifyExpired]=Y 只看超过 90 天未核对的项目）
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrProgram program)
    {
        startPage();
        List<AbrProgram> list = programService.selectAbrProgramList(program);
        return getDataTable(list);
    }

    /**
     * 导出项目列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:export')")
    @Log(title = "项目", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AbrProgram program)
    {
        List<AbrProgram> list = programService.selectAbrProgramList(program);
        ExcelUtil<AbrProgram> util = new ExcelUtil<AbrProgram>(AbrProgram.class);
        util.exportExcel(response, list, "项目数据");
    }

    /**
     * 获取项目详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:query')")
    @GetMapping(value = "/{programId}")
    public AjaxResult getInfo(@PathVariable Long programId)
    {
        return success(programService.selectAbrProgramById(programId));
    }

    /**
     * 新增项目
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:add')")
    @Log(title = "项目", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrProgram program)
    {
        if (!programService.checkAbrProgramUnique(program))
        {
            return error("新增项目'" + program.getProgramNameEn() + "'失败，该院校下同名同学位的项目已存在");
        }
        program.setCreateBy(getUsername());
        return toAjax(programService.insertAbrProgram(program));
    }

    /**
     * 修改项目
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:edit')")
    @Log(title = "项目", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrProgram program)
    {
        if (!programService.checkAbrProgramUnique(program))
        {
            return error("修改项目'" + program.getProgramNameEn() + "'失败，该院校下同名同学位的项目已存在");
        }
        program.setUpdateBy(getUsername());
        return toAjax(programService.updateAbrProgram(program));
    }

    /**
     * 删除项目（连同申请要求）
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:remove')")
    @Log(title = "项目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{programIds}")
    public AjaxResult remove(@PathVariable Long[] programIds)
    {
        return toAjax(programService.deleteAbrProgramByIds(programIds, getUsername()));
    }
}

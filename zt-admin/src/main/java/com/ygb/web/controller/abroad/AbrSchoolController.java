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
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.service.IAbrSchoolService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.poi.ExcelUtil;

/**
 * 院校库-院校
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/school")
public class AbrSchoolController extends BaseController
{
    @Autowired
    private IAbrSchoolService schoolService;

    /**
     * 查询院校列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrSchool school)
    {
        startPage();
        List<AbrSchool> list = schoolService.selectAbrSchoolList(school);
        return getDataTable(list);
    }

    /**
     * 导出院校列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:export')")
    @Log(title = "院校", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AbrSchool school)
    {
        List<AbrSchool> list = schoolService.selectAbrSchoolList(school);
        ExcelUtil<AbrSchool> util = new ExcelUtil<AbrSchool>(AbrSchool.class);
        util.exportExcel(response, list, "院校数据");
    }

    /**
     * 获取院校详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:query')")
    @GetMapping(value = "/{schoolId}")
    public AjaxResult getInfo(@PathVariable Long schoolId)
    {
        return success(schoolService.selectAbrSchoolById(schoolId));
    }

    /**
     * 新增院校
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:add')")
    @Log(title = "院校", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrSchool school)
    {
        if (!schoolService.checkAbrSchoolUnique(school))
        {
            return error("新增院校'" + school.getSchoolNameEn() + "'失败，同一国家下英文名已存在");
        }
        school.setCreateBy(getUsername());
        return toAjax(schoolService.insertAbrSchool(school));
    }

    /**
     * 修改院校
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:edit')")
    @Log(title = "院校", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrSchool school)
    {
        if (!schoolService.checkAbrSchoolUnique(school))
        {
            return error("修改院校'" + school.getSchoolNameEn() + "'失败，同一国家下英文名已存在");
        }
        school.setUpdateBy(getUsername());
        return toAjax(schoolService.updateAbrSchool(school));
    }

    /**
     * 删除院校
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:remove')")
    @Log(title = "院校", businessType = BusinessType.DELETE)
    @DeleteMapping("/{schoolIds}")
    public AjaxResult remove(@PathVariable Long[] schoolIds)
    {
        return toAjax(schoolService.deleteAbrSchoolByIds(schoolIds, getUsername()));
    }
}

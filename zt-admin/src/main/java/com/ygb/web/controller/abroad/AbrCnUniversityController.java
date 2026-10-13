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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrCnUniversity;
import com.ygb.abroad.service.IAbrCnUniversityService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.poi.ExcelUtil;

/**
 * 院校库-国内高校层次
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/cnUniversity")
public class AbrCnUniversityController extends BaseController
{
    @Autowired
    private IAbrCnUniversityService universityService;

    /**
     * 查询国内高校列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:cnUniversity:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrCnUniversity university)
    {
        startPage();
        List<AbrCnUniversity> list = universityService.selectAbrCnUniversityList(university);
        return getDataTable(list);
    }

    /**
     * 按校名或别名模糊搜索（返回按匹配程度排序的前 limit 条；搜不到即视为双非）
     */
    @PreAuthorize("@ss.hasPermi('abroad:cnUniversity:query')")
    @GetMapping("/search")
    public AjaxResult search(@RequestParam String keyword, @RequestParam(defaultValue = "10") int limit)
    {
        return success(universityService.searchAbrCnUniversity(keyword, limit));
    }

    /**
     * 导出国内高校列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:cnUniversity:export')")
    @Log(title = "国内高校", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AbrCnUniversity university)
    {
        List<AbrCnUniversity> list = universityService.selectAbrCnUniversityList(university);
        ExcelUtil<AbrCnUniversity> util = new ExcelUtil<AbrCnUniversity>(AbrCnUniversity.class);
        util.exportExcel(response, list, "国内高校层次");
    }

    /**
     * 获取国内高校详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:cnUniversity:query')")
    @GetMapping(value = "/{cnUniversityId}")
    public AjaxResult getInfo(@PathVariable Long cnUniversityId)
    {
        return success(universityService.selectAbrCnUniversityById(cnUniversityId));
    }

    /**
     * 新增国内高校
     */
    @PreAuthorize("@ss.hasPermi('abroad:cnUniversity:add')")
    @Log(title = "国内高校", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrCnUniversity university)
    {
        if (!universityService.checkAbrCnUniversityUnique(university))
        {
            return error("新增高校'" + university.getUniversityName() + "'失败，高校已存在");
        }
        university.setCreateBy(getUsername());
        return toAjax(universityService.insertAbrCnUniversity(university));
    }

    /**
     * 修改国内高校
     */
    @PreAuthorize("@ss.hasPermi('abroad:cnUniversity:edit')")
    @Log(title = "国内高校", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrCnUniversity university)
    {
        if (!universityService.checkAbrCnUniversityUnique(university))
        {
            return error("修改高校'" + university.getUniversityName() + "'失败，高校已存在");
        }
        university.setUpdateBy(getUsername());
        return toAjax(universityService.updateAbrCnUniversity(university));
    }

    /**
     * 删除国内高校
     */
    @PreAuthorize("@ss.hasPermi('abroad:cnUniversity:remove')")
    @Log(title = "国内高校", businessType = BusinessType.DELETE)
    @DeleteMapping("/{cnUniversityIds}")
    public AjaxResult remove(@PathVariable Long[] cnUniversityIds)
    {
        return toAjax(universityService.deleteAbrCnUniversityByIds(cnUniversityIds));
    }
}

package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrAssessment;
import com.ygb.abroad.service.IAbrAssessmentService;
import com.ygb.abroad.service.IAbrProfileService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 测评与选校-测评记录（查看学生答案与归一化后的背景档案）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/assessment")
public class AbrAssessmentController extends BaseController
{
    @Autowired
    private IAbrAssessmentService assessmentService;

    @Autowired
    private IAbrProfileService profileService;

    /**
     * 查询测评列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:assessment:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrAssessment assessment)
    {
        startPage();
        List<AbrAssessment> list = assessmentService.selectAbrAssessmentList(assessment);
        return getDataTable(list);
    }

    /**
     * 获取测评详情（答案 + 背景档案）
     */
    @PreAuthorize("@ss.hasPermi('abroad:assessment:query')")
    @GetMapping(value = "/{assessmentId}")
    public AjaxResult getInfo(@PathVariable Long assessmentId)
    {
        AjaxResult ajax = success(assessmentService.selectAbrAssessmentById(assessmentId));
        ajax.put("profile", profileService.selectAbrProfileByAssessmentId(assessmentId));
        return ajax;
    }

    /**
     * 重新归一化（换算表或院校层次修改后，按当前数据重算背景档案）
     */
    @PreAuthorize("@ss.hasPermi('abroad:assessment:normalize')")
    @Log(title = "测评归一化", businessType = BusinessType.UPDATE)
    @PostMapping("/{assessmentId}/normalize")
    public AjaxResult normalize(@PathVariable Long assessmentId)
    {
        return success(profileService.normalizeAssessment(assessmentId, getUsername()));
    }
}

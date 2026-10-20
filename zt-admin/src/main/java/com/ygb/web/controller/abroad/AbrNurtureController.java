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
import com.ygb.abroad.domain.AbrNurtureEnrollment;
import com.ygb.abroad.domain.AbrNurtureSequence;
import com.ygb.abroad.service.IAbrNurtureService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-培育序列（序列与步骤配置、入组进度、人工确认模式下的确认 / 跳过 / 退出）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/nurture")
public class AbrNurtureController extends BaseController
{
    @Autowired
    private IAbrNurtureService nurtureService;

    /**
     * 查询序列（带进行中、待确认的入组数）
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:list')")
    @GetMapping("/sequence/list")
    public TableDataInfo list(AbrNurtureSequence sequence)
    {
        startPage();
        List<AbrNurtureSequence> list = nurtureService.selectSequenceList(sequence);
        return getDataTable(list);
    }

    /**
     * 序列详情（含步骤）
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:query')")
    @GetMapping("/sequence/{sequenceId}")
    public AjaxResult getInfo(@PathVariable Long sequenceId)
    {
        return success(nurtureService.selectSequenceById(sequenceId));
    }

    /**
     * 新增序列（含步骤）
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:add')")
    @Log(title = "培育序列", businessType = BusinessType.INSERT)
    @PostMapping("/sequence")
    public AjaxResult add(@RequestBody AbrNurtureSequence sequence)
    {
        sequence.setSequenceId(null);
        sequence.setCreateBy(getUsername());
        return success(nurtureService.saveSequence(sequence));
    }

    /**
     * 修改序列（步骤整体替换）
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:edit')")
    @Log(title = "培育序列", businessType = BusinessType.UPDATE)
    @PutMapping("/sequence")
    public AjaxResult edit(@RequestBody AbrNurtureSequence sequence)
    {
        if (sequence.getSequenceId() == null)
        {
            return error("缺少序列ID");
        }
        sequence.setUpdateBy(getUsername());
        return success(nurtureService.saveSequence(sequence));
    }

    /**
     * 删除序列（连同步骤与入组）
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:remove')")
    @Log(title = "培育序列", businessType = BusinessType.DELETE)
    @DeleteMapping("/sequence/{sequenceId}")
    public AjaxResult remove(@PathVariable Long sequenceId)
    {
        return toAjax(nurtureService.deleteSequence(sequenceId));
    }

    /**
     * 查询入组（sequenceId、status、targetType、targetId；待确认排在前面）
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:list')")
    @GetMapping("/enrollment/list")
    public TableDataInfo enrollments(AbrNurtureEnrollment enrollment)
    {
        startPage();
        List<AbrNurtureEnrollment> list = nurtureService.selectEnrollmentList(enrollment);
        return getDataTable(list);
    }

    /**
     * 确认发送待确认的一步
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:handle')")
    @Log(title = "培育确认发送", businessType = BusinessType.UPDATE)
    @PutMapping("/enrollment/confirm/{enrollmentId}")
    public AjaxResult confirm(@PathVariable Long enrollmentId)
    {
        return success(nurtureService.confirm(enrollmentId));
    }

    /**
     * 跳过下一步
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:handle')")
    @Log(title = "培育跳过", businessType = BusinessType.UPDATE)
    @PutMapping("/enrollment/skip/{enrollmentId}")
    public AjaxResult skip(@PathVariable Long enrollmentId)
    {
        return success(nurtureService.skip(enrollmentId));
    }

    /**
     * 退出序列（body：reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:nurture:handle')")
    @Log(title = "培育退出", businessType = BusinessType.UPDATE)
    @PutMapping("/enrollment/exit/{enrollmentId}")
    public AjaxResult exit(@PathVariable Long enrollmentId, @RequestBody(required = false) Map<String, String> body)
    {
        return success(nurtureService.exit(enrollmentId, body == null ? null : body.get("reason")));
    }
}

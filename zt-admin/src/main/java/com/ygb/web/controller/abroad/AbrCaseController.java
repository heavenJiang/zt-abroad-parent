package com.ygb.web.controller.abroad;

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ygb.abroad.domain.AbrCase;
import com.ygb.abroad.service.IAbrCaseService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.SecurityUtils;

/**
 * 获客运营-offer 库（录取案例、授权、offer 截图、offer 卡）；关联学生与 offer 截图须 abroad:case:identity 权限
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/case")
public class AbrCaseController extends BaseController
{
    /** 查看与设置关联学生的权限 */
    static final String PERM_IDENTITY = "abroad:case:identity";

    @Autowired
    private IAbrCaseService caseService;

    /**
     * 查询案例（country、degree、result、verifyLevel、season、schoolName；params[consent]=publicity）；没有关联学生权限时不返回关联线索
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrCase c)
    {
        boolean identity = SecurityUtils.hasPermi(PERM_IDENTITY);
        if (!identity)
        {
            c.setLeadId(null);
        }
        startPage();
        List<AbrCase> list = caseService.selectCaseList(c);
        if (!identity)
        {
            list.forEach(AbrCaseController::hideIdentity);
        }
        return getDataTable(list);
    }

    /**
     * 案例详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:query')")
    @GetMapping("/{caseId}")
    public AjaxResult getInfo(@PathVariable Long caseId)
    {
        AbrCase c = caseService.selectCaseById(caseId);
        if (c != null && !SecurityUtils.hasPermi(PERM_IDENTITY))
        {
            hideIdentity(c);
        }
        return success(c);
    }

    /**
     * 新增案例
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:add')")
    @Log(title = "录取案例", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AbrCase c)
    {
        c.setCaseId(null);
        c.setCreateBy(getUsername());
        return success(caseService.saveCase(c, !SecurityUtils.hasPermi(PERM_IDENTITY)));
    }

    /**
     * 修改案例（含授权）
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:edit')")
    @Log(title = "录取案例", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AbrCase c)
    {
        if (c.getCaseId() == null)
        {
            return error("缺少案例ID");
        }
        c.setUpdateBy(getUsername());
        return success(caseService.saveCase(c, !SecurityUtils.hasPermi(PERM_IDENTITY)));
    }

    /**
     * 撤回全部授权
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:edit')")
    @Log(title = "录取案例撤回授权", businessType = BusinessType.UPDATE)
    @PutMapping("/revoke/{caseId}")
    public AjaxResult revoke(@PathVariable Long caseId)
    {
        return toAjax(caseService.revoke(caseId, getUsername()));
    }

    /**
     * 删除案例（连同 offer 截图）
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:remove')")
    @Log(title = "录取案例", businessType = BusinessType.DELETE)
    @DeleteMapping("/{caseIds}")
    public AjaxResult remove(@PathVariable Long[] caseIds)
    {
        return toAjax(caseService.deleteCases(caseIds));
    }

    /**
     * 上传 offer 截图（PDF / JPG / PNG，不超过 10MB；按文件头判断格式；存私有目录）
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:edit')")
    @Log(title = "录取案例 offer 截图", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/offer/{caseId}")
    public AjaxResult uploadOffer(@PathVariable Long caseId, @RequestParam("file") MultipartFile file) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择文件");
        }
        caseService.saveOfferFile(caseId, file.getBytes(), file.getOriginalFilename(), getUsername());
        return success();
    }

    /**
     * 查看 offer 截图（须关联学生权限，记操作日志）
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:identity')")
    @Log(title = "录取案例查看 offer 截图", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/offer/{caseId}")
    public ResponseEntity<byte[]> offer(@PathVariable Long caseId)
    {
        Object[] f = caseService.readOfferFile(caseId);
        MediaType type = "pdf".equals(f[1]) ? MediaType.APPLICATION_PDF : "png".equals(f[1]) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore()).body((byte[]) f[0]);
    }

    /**
     * 预览 offer 卡（PNG；只有可公开的案例）
     */
    @PreAuthorize("@ss.hasPermi('abroad:case:query')")
    @GetMapping("/card/{caseId}")
    public ResponseEntity<byte[]> card(@PathVariable Long caseId)
    {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(caseService.cardPng(caseId));
    }

    private static void hideIdentity(AbrCase c)
    {
        c.setLeadId(null);
        c.setLeadName(null);
    }
}

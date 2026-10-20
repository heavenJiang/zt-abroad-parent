package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrPartnerCommission;
import com.ygb.abroad.domain.AbrReferrer;
import com.ygb.abroad.service.IAbrPartnerService;
import com.ygb.abroad.service.IAbrReferralService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.poi.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 合作方分成（M9-05）：合作方链接与二维码、分成对账、确认与发放（合作方本身在「推荐与奖励」中维护）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/partner")
public class AbrPartnerController extends BaseController
{
    @Autowired
    private IAbrPartnerService partnerService;

    @Autowired
    private IAbrReferralService referralService;

    /**
     * 合作方列表（推荐人类型 partner）
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrReferrer referrer)
    {
        referrer.setReferrerType("partner");
        startPage();
        List<AbrReferrer> list = referralService.selectReferrerList(referrer);
        list.forEach(f -> f.getParams().put("link", referralService.link(f.getReferrerType(), f.getRefCode())));
        return getDataTable(list);
    }

    /**
     * 合作方可嵌入的工具链接
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:list')")
    @GetMapping("/links/{referrerId}")
    public AjaxResult links(@PathVariable Long referrerId)
    {
        return success(partnerService.links(referrerId));
    }

    /**
     * 工具链接二维码（PNG）
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:list')")
    @GetMapping("/qr/{referrerId}")
    public ResponseEntity<byte[]> qr(@PathVariable Long referrerId, @RequestParam String tool)
    {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(partnerService.qr(referrerId, tool));
    }

    /**
     * 分成列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:list')")
    @GetMapping("/commission/list")
    public TableDataInfo commissionList(AbrPartnerCommission commission)
    {
        startPage();
        List<AbrPartnerCommission> list = partnerService.selectCommissionList(commission);
        return getDataTable(list);
    }

    /**
     * 月度对账单
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:list')")
    @GetMapping("/statement")
    public AjaxResult statement(@RequestParam(required = false) String month)
    {
        return success(partnerService.statement(month));
    }

    /**
     * 立即对账
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:reconcile')")
    @Log(title = "合作方分成对账", businessType = BusinessType.OTHER)
    @PostMapping("/reconcile")
    public AjaxResult reconcile()
    {
        return success(partnerService.reconcile(getUsername()));
    }

    /**
     * 确认分成
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:confirm')")
    @Log(title = "合作方分成确认", businessType = BusinessType.UPDATE)
    @PutMapping("/commission/confirm/{ids}")
    public AjaxResult confirm(@PathVariable Long[] ids)
    {
        return success(Map.of("rows", partnerService.confirm(ids, getUsername())));
    }

    /**
     * 登记已发放
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:confirm')")
    @Log(title = "合作方分成发放", businessType = BusinessType.UPDATE)
    @PutMapping("/commission/pay/{ids}")
    public AjaxResult pay(@PathVariable Long[] ids, @RequestBody Map<String, String> body)
    {
        return success(Map.of("rows", partnerService.pay(ids, body.get("note"), getUsername())));
    }

    /**
     * 作废
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:confirm')")
    @Log(title = "合作方分成作废", businessType = BusinessType.UPDATE)
    @PutMapping("/commission/void/{id}")
    public AjaxResult voidCommission(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        return toAjax(partnerService.voidCommission(id, body.get("reason"), getUsername()));
    }

    /**
     * 退款追回处理完毕
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:confirm')")
    @Log(title = "合作方分成退款处理", businessType = BusinessType.UPDATE)
    @PutMapping("/commission/refund/{id}")
    public AjaxResult refund(@PathVariable Long id, @RequestBody Map<String, String> body)
    {
        return toAjax(partnerService.clearRefund(id, body.get("note"), getUsername()));
    }

    /**
     * 导出分成明细（学生称呼只保留第一个字）
     */
    @PreAuthorize("@ss.hasPermi('abroad:partner:export')")
    @Log(title = "合作方分成", businessType = BusinessType.EXPORT)
    @PostMapping("/commission/export")
    public void export(HttpServletResponse response, AbrPartnerCommission commission)
    {
        List<AbrPartnerCommission> list = partnerService.selectCommissionExport(commission);
        ExcelUtil<AbrPartnerCommission> util = new ExcelUtil<AbrPartnerCommission>(AbrPartnerCommission.class);
        util.exportExcel(response, list, "合作方分成");
    }
}

package com.ygb.web.controller.abroad;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.vo.AbrLeadImportRow;
import com.ygb.abroad.service.IAbrLeadService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.poi.ExcelUtil;

/**
 * 获客运营-线索收件箱（多来源收口、去重合并、时间线；手机号脱敏，明文须单独权限并记操作日志）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/lead")
public class AbrLeadController extends BaseController
{
    @Autowired
    private IAbrLeadService leadService;

    /**
     * 查询线索列表（params[keyword] 为称呼，或 11 位手机号精确查找；params[beginTime] / params[endTime] 为首次触达日期）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrLead lead)
    {
        startPage();
        List<AbrLead> list = leadService.selectAbrLeadList(lead);
        return getDataTable(list);
    }

    /**
     * 线索详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:query')")
    @GetMapping("/{leadId}")
    public AjaxResult getInfo(@PathVariable Long leadId)
    {
        return success(leadService.selectAbrLeadById(leadId));
    }

    /**
     * 时间线
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:query')")
    @GetMapping("/timeline/{leadId}")
    public AjaxResult timeline(@PathVariable Long leadId, @RequestParam(defaultValue = "200") int limit)
    {
        return success(leadService.timeline(leadId, limit));
    }

    /**
     * 查看手机号明文（记操作日志，日志不保存返回内容）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:phone')")
    @Log(title = "线索-查看手机号", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/phone/{leadId}")
    public AjaxResult phone(@PathVariable Long leadId)
    {
        return success().put(AjaxResult.DATA_TAG, leadService.selectPhone(leadId));
    }

    /**
     * 录入线索（须有手机号；已有同一人的线索时只记一次来源）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:add')")
    @Log(title = "线索", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult add(@RequestBody AbrLead lead)
    {
        return success(leadService.insertManual(lead, getUsername()));
    }

    /**
     * 导入模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:import')")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<AbrLeadImportRow> util = new ExcelUtil<AbrLeadImportRow>(AbrLeadImportRow.class);
        util.importTemplateExcel(response, "线索导入");
    }

    /**
     * 导入线索（逐行去重；失败的行返回原因）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:import')")
    @Log(title = "线索", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file) throws Exception
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择要导入的文件");
        }
        ExcelUtil<AbrLeadImportRow> util = new ExcelUtil<AbrLeadImportRow>(AbrLeadImportRow.class);
        return success(leadService.importLeads(util.importExcel(file.getInputStream()), getUsername()));
    }
}

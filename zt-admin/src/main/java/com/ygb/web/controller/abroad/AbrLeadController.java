package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrLeadFollow;
import com.ygb.abroad.domain.vo.AbrLeadImportRow;
import com.ygb.abroad.service.AbrLeadScope;
import com.ygb.abroad.service.IAbrLeadCardService;
import com.ygb.abroad.service.IAbrLeadScoreService;
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

    @Autowired
    private IAbrLeadScoreService scoreService;

    @Autowired
    private IAbrLeadCardService cardService;

    @Autowired
    private AbrLeadScope leadScope;

    /**
     * 查询线索列表：默认按 意向度 → 下次联系时间 → 最近触达 排序。params[keyword] 为称呼或 11 位手机号；params[beginTime] / params[endTime]
     * 为首次触达日期；params[mine]=1 只看我负责的，params[unassigned]=1 只看未分配的，params[due]=1 只看到期待联系的；ownerUserId 按负责人
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrLead lead)
    {
        if (!leadScope.all() || (lead.getParams().get("mine") != null && !"".equals(lead.getParams().get("mine"))))
        {
            lead.setOwnerUserId(getUserId());
        }
        startPage();
        List<AbrLead> list = leadService.selectAbrLeadList(lead);
        return getDataTable(list);
    }

    /**
     * 可转给的同事（在用员工的姓名与登录名；顾问没有用户管理权限时也能选）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:assign')")
    @GetMapping("/staff")
    public AjaxResult staff()
    {
        return success(leadService.selectActiveStaff());
    }

    /**
     * 线索详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:query') and @abrLeadScope.lead(#p0)")
    @GetMapping("/{leadId}")
    public AjaxResult getInfo(@PathVariable Long leadId)
    {
        return success(leadService.selectAbrLeadById(leadId));
    }

    /**
     * 时间线
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:query') and @abrLeadScope.lead(#p0)")
    @GetMapping("/timeline/{leadId}")
    public AjaxResult timeline(@PathVariable Long leadId, @RequestParam(defaultValue = "200") int limit)
    {
        return success(leadService.timeline(leadId, limit));
    }

    /**
     * 查看手机号明文（记操作日志，日志不保存返回内容）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:phone') and @abrLeadScope.lead(#p0)")
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

    /**
     * 修改意向度（grade 为 A / B / C，须填原因 reason；grade 为空时撤销修改、恢复规则等级）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:grade') and @abrLeadScope.lead(#p0)")
    @Log(title = "线索意向度", businessType = BusinessType.UPDATE)
    @PutMapping("/grade/{leadId}")
    public AjaxResult grade(@PathVariable Long leadId, @RequestBody Map<String, String> body)
    {
        return success(scoreService.override(leadId, body.get("grade"), body.get("reason"), getUsername()));
    }

    /**
     * 立即重新评分
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:score') and @abrLeadScope.lead(#p0)")
    @Log(title = "线索意向度", businessType = BusinessType.OTHER)
    @PostMapping("/score/{leadId}")
    public AjaxResult score(@PathVariable Long leadId)
    {
        return success(scoreService.score(leadId, getUsername()));
    }

    /**
     * 意向度历史
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:query') and @abrLeadScope.lead(#p0)")
    @GetMapping("/score/log/{leadId}")
    public AjaxResult scoreLog(@PathVariable Long leadId)
    {
        return success(scoreService.scoreLog(leadId));
    }

    /**
     * 修改称呼与备注
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:edit') and @abrLeadScope.lead(#p0.leadId)")
    @Log(title = "线索", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AbrLead lead)
    {
        return toAjax(leadService.updateBasic(lead, getUsername()));
    }

    /**
     * 记跟进（method、content、nextFollowTime；可带 stageTo / lostReason 同时改阶段）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:follow') and @abrLeadScope.lead(#p0)")
    @Log(title = "线索跟进", businessType = BusinessType.INSERT)
    @PostMapping("/follow/{leadId}")
    public AjaxResult follow(@PathVariable Long leadId, @RequestBody AbrLeadFollow follow)
    {
        return success(leadService.follow(leadId, follow, getUsername()));
    }

    /**
     * 跟进记录
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:query') and @abrLeadScope.lead(#p0)")
    @GetMapping("/follow/{leadId}")
    public AjaxResult follows(@PathVariable Long leadId)
    {
        return success(leadService.follows(leadId));
    }

    /**
     * 改阶段（stage；流失须填 lostReason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:stage') and @abrLeadScope.lead(#p0)")
    @Log(title = "线索阶段", businessType = BusinessType.UPDATE)
    @PutMapping("/stage/{leadId}")
    public AjaxResult stage(@PathVariable Long leadId, @RequestBody Map<String, String> body)
    {
        return success(leadService.changeStage(leadId, body.get("stage"), body.get("lostReason"), getUsername()));
    }

    /**
     * 转给同事（ownerUserId、reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:assign') and @abrLeadScope.lead(#p0)")
    @Log(title = "线索转交", businessType = BusinessType.UPDATE)
    @PutMapping("/owner/{leadId}")
    public AjaxResult owner(@PathVariable Long leadId, @RequestBody Map<String, Object> body)
    {
        Object owner = body.get("ownerUserId");
        Long ownerUserId = owner instanceof Number n ? n.longValue() : null;
        return success(leadService.assign(leadId, ownerUserId, body.get("reason") == null ? null : String.valueOf(body.get("reason")), getUsername()));
    }

    /**
     * 线索卡片（仅内部可见；没有时为空）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:query') and @abrLeadScope.lead(#p0)")
    @GetMapping("/card/{leadId}")
    public AjaxResult card(@PathVariable Long leadId)
    {
        return success().put(AjaxResult.DATA_TAG, cardService.selectByLead(leadId));
    }

    /**
     * 重新生成线索卡片（强制重新调用模型）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:card') and @abrLeadScope.lead(#p0)")
    @Log(title = "线索卡片", businessType = BusinessType.OTHER)
    @PostMapping("/card/{leadId}")
    public AjaxResult generateCard(@PathVariable Long leadId)
    {
        return success(cardService.generate(leadId, "manual", true, getUsername()));
    }
}

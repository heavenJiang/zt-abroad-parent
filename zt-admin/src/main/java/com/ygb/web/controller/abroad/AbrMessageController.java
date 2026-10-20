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
import com.ygb.abroad.domain.AbrMessageSend;
import com.ygb.abroad.domain.AbrMessageTemplate;
import com.ygb.abroad.service.IAbrMessageService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-消息与渠道（消息模板、发送记录、渠道开通状态、测试发送）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/message")
public class AbrMessageController extends BaseController
{
    @Autowired
    private IAbrMessageService messageService;

    /**
     * 各渠道开通状态（只返回是否已配置，不返回凭据）
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:list')")
    @GetMapping("/channel/status")
    public AjaxResult channelStatus()
    {
        return success(messageService.channelStatus());
    }

    /**
     * 查询消息模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:list')")
    @GetMapping("/template/list")
    public TableDataInfo list(AbrMessageTemplate template)
    {
        startPage();
        List<AbrMessageTemplate> list = messageService.selectTemplateList(template);
        return getDataTable(list);
    }

    /**
     * 消息模板详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:query')")
    @GetMapping("/template/{templateId}")
    public AjaxResult getInfo(@PathVariable Long templateId)
    {
        return success(messageService.selectTemplateById(templateId));
    }

    /**
     * 新增消息模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:add')")
    @Log(title = "消息模板", businessType = BusinessType.INSERT)
    @PostMapping("/template")
    public AjaxResult add(@RequestBody AbrMessageTemplate template)
    {
        template.setTemplateId(null);
        template.setCreateBy(getUsername());
        return toAjax(messageService.saveTemplate(template));
    }

    /**
     * 修改消息模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:edit')")
    @Log(title = "消息模板", businessType = BusinessType.UPDATE)
    @PutMapping("/template")
    public AjaxResult edit(@RequestBody AbrMessageTemplate template)
    {
        if (template.getTemplateId() == null)
        {
            return error("缺少模板ID");
        }
        template.setUpdateBy(getUsername());
        return toAjax(messageService.saveTemplate(template));
    }

    /**
     * 删除消息模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:remove')")
    @Log(title = "消息模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/template/{templateIds}")
    public AjaxResult remove(@PathVariable Long[] templateIds)
    {
        return toAjax(messageService.deleteTemplates(templateIds));
    }

    /**
     * 查询发送记录（templateCode、channel、status、targetType、targetId；params[beginTime] / params[endTime]）
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:list')")
    @GetMapping("/send/list")
    public TableDataInfo sendList(AbrMessageSend send)
    {
        startPage();
        List<AbrMessageSend> list = messageService.selectSendList(send);
        return getDataTable(list);
    }

    /**
     * 测试发送（body：templateId、targetType 为 app_user / lead / visitor / staff、targetId、vars 变量）；不经触达判断与合规闸门，记入发送记录。
     * 带 agentCode（获客 Agent）时按真实发送：按该模板的业务编码选学生可达的渠道，经触达判断（会计入频控）与营销类合规闸门
     */
    @PreAuthorize("@ss.hasPermi('abroad:message:test')")
    @Log(title = "消息测试发送", businessType = BusinessType.OTHER)
    @PostMapping("/test")
    @SuppressWarnings("unchecked")
    public AjaxResult test(@RequestBody Map<String, Object> body)
    {
        Object templateId = body.get("templateId");
        Object targetId = body.get("targetId");
        String targetType = (String) body.get("targetType");
        if (!(templateId instanceof Number) || !(targetId instanceof Number) || !List.of("app_user", "lead", "visitor", "staff").contains(targetType))
        {
            return error("请选择模板，对象类型为 app_user / lead / visitor / staff 并填写对象ID");
        }
        Map<String, Object> vars = body.get("vars") instanceof Map ? (Map<String, Object>) body.get("vars") : Map.of();
        String agentCode = body.get("agentCode") instanceof String a && !a.isBlank() ? a.trim() : null;
        if (agentCode != null)
        {
            if ("staff".equals(targetType))
            {
                return error("按真实发送只能发给学生账号、线索或访客");
            }
            AbrMessageTemplate t = messageService.selectTemplateById(((Number) templateId).longValue());
            if (t == null)
            {
                return error("模板不存在");
            }
            return success(messageService.sendToStudent(t.getTemplateCode(), targetType, ((Number) targetId).longValue(), vars, agentCode, null, false));
        }
        return success(messageService.testSend(((Number) templateId).longValue(), targetType, ((Number) targetId).longValue(), vars, getUsername()));
    }
}

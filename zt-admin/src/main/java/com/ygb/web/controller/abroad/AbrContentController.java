package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
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
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.service.IAbrContentService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 内容引擎-内容库（稿件、内容编号与私信口令、引用依据、合规闸门结果、人工确认）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/content")
public class AbrContentController extends BaseController
{
    @Autowired
    private IAbrContentService contentService;

    /**
     * 查询内容列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrContent content)
    {
        startPage();
        List<AbrContent> list = contentService.selectAbrContentList(content);
        return getDataTable(list);
    }

    /**
     * 按私信口令查找内容
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:query')")
    @GetMapping("/codeword/{codeword}")
    public AjaxResult byCodeword(@PathVariable String codeword)
    {
        AbrContent c = contentService.selectAbrContentByCodeword(codeword);
        return c == null ? error("没有使用这个口令的内容") : success(c);
    }

    /**
     * 获取内容详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:query')")
    @GetMapping(value = "/{contentId}")
    public AjaxResult getInfo(@PathVariable Long contentId)
    {
        return success(contentService.selectAbrContentById(contentId));
    }

    /**
     * 新增内容（草稿）
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:add')")
    @Log(title = "内容", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrContent content)
    {
        content.setCreateBy(getUsername());
        return success(contentService.insertAbrContent(content));
    }

    /**
     * 修改内容（改动稿件后须重新过闸门）
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:edit')")
    @Log(title = "内容", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrContent content)
    {
        content.setUpdateBy(getUsername());
        return toAjax(contentService.updateAbrContent(content));
    }

    /**
     * 闸门人工审核（M6-03 上线前；body：passed、note）
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:gate')")
    @Log(title = "内容闸门审核", businessType = BusinessType.UPDATE)
    @PutMapping("/gate/{contentId}")
    public AjaxResult gate(@PathVariable Long contentId, @RequestBody Map<String, Object> body)
    {
        if (!(body.get("passed") instanceof Boolean))
        {
            return error("请选择通过或拦截");
        }
        return success(contentService.reviewGate(contentId, (Boolean) body.get("passed"), (String) body.get("note"), getUsername()));
    }

    /**
     * 人工确认（执行模式为人工确认的 Agent 生成的内容）
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:confirm')")
    @Log(title = "内容人工确认", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{contentId}")
    public AjaxResult confirm(@PathVariable Long contentId)
    {
        return contentService.confirmContent(contentId, getUsername()) ? success("已确认，可安排发布") : error("内容不是待人工确认状态");
    }

    /**
     * 删除内容（没有发布记录的删除，有的归档）
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:remove')")
    @Log(title = "内容", businessType = BusinessType.DELETE)
    @DeleteMapping("/{contentIds}")
    public AjaxResult remove(@PathVariable Long[] contentIds)
    {
        return toAjax(contentService.removeContents(contentIds, getUsername()));
    }
}

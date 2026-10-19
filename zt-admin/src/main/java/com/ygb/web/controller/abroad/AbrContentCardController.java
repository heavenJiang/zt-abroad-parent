package com.ygb.web.controller.abroad;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrContentDesignService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.BusinessType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 内容引擎-图卡（content_designer 确定性模板：封面、项目清单、截止日历、对比表；数字只取院校库）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/content/card")
public class AbrContentCardController extends BaseController
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private IAbrContentDesignService designService;

    /**
     * 可选模板与展示字段
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:query')")
    @GetMapping("/templates")
    public AjaxResult templates()
    {
        return success(designService.templates());
    }

    /**
     * 内容的图卡
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:query')")
    @GetMapping("/{contentId}")
    public AjaxResult cards(@PathVariable Long contentId)
    {
        return success(designService.cards(contentId));
    }

    /**
     * 预览一张图卡（body：contentId 可选、template、params、title；返回 PNG，不保存）
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:query')")
    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(@RequestBody Map<String, Object> body)
    {
        Object id = body.get("contentId");
        byte[] png = designService.preview(id instanceof Number n ? n.longValue() : null, (String) body.get("template"),
                body.get("params") == null ? null : MAPPER.writeValueAsString(body.get("params")), (String) body.get("title"));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    /**
     * 渲染并保存内容的全部图卡（body：cards 图卡清单；为空按现有图卡重新渲染）；图卡变化后内容须重新过闸门
     */
    @PreAuthorize("@ss.hasPermi('abroad:content:edit')")
    @Log(title = "内容图卡", businessType = BusinessType.UPDATE)
    @PutMapping("/{contentId}")
    public AjaxResult render(@PathVariable Long contentId, @RequestBody Map<String, Object> body)
    {
        String cards = body.get("cards") == null ? null : MAPPER.writeValueAsString(body.get("cards"));
        return success(designService.renderCards(contentId, cards, false, getUsername()));
    }
}

package com.ygb.web.controller.abroad;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrSiteBuild;
import com.ygb.abroad.service.IAbrSiteService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-公开项目页（院校库确定性渲染的静态 HTML：生成记录、概况、预览、重新生成）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/site")
public class AbrSiteController extends BaseController
{
    @Autowired
    private IAbrSiteService siteService;

    /**
     * 概况：站点目录与地址、各类页面数、最近一次生成、是否有未生成的变化、收录提交是否配置
     */
    @PreAuthorize("@ss.hasPermi('abroad:site:list')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        return success(siteService.status());
    }

    /**
     * 生成记录
     */
    @PreAuthorize("@ss.hasPermi('abroad:site:list')")
    @GetMapping("/build/list")
    public TableDataInfo list(AbrSiteBuild build)
    {
        startPage();
        List<AbrSiteBuild> list = siteService.selectAbrSiteBuildList(build);
        return getDataTable(list);
    }

    /**
     * 生成记录详情（含变化的页面与收录提交结果）
     */
    @PreAuthorize("@ss.hasPermi('abroad:site:list')")
    @GetMapping("/build/{buildId}")
    public AjaxResult getInfo(@PathVariable Long buildId)
    {
        return success(siteService.selectAbrSiteBuildById(buildId));
    }

    /**
     * 预览一个页面（按当前院校库渲染，不写文件；url 如 program/12、country/uk，首页为空）
     */
    @PreAuthorize("@ss.hasPermi('abroad:site:query')")
    @GetMapping("/preview")
    public ResponseEntity<String> preview(@RequestParam(required = false, defaultValue = "") String url)
    {
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data:; sandbox")
                .body(siteService.preview(url));
    }

    /**
     * 重新生成（不论院校库有没有变化；仍只重写内容变化的文件）
     */
    @PreAuthorize("@ss.hasPermi('abroad:site:build')")
    @Log(title = "公开项目页", businessType = BusinessType.OTHER)
    @PostMapping("/build")
    public AjaxResult build()
    {
        return success(siteService.refresh(AbrSiteBuild.TRIGGER_MANUAL, true, getUsername()));
    }
}

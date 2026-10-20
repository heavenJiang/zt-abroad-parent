package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrFieldHistory;
import com.ygb.abroad.mapper.AbrFieldSourceMapper;
import com.ygb.abroad.service.IAbrFieldRollbackService;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 院校库源信息与字段历史（M2-09）：字段来源、存档原文、变化历史、回滚
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/source")
public class AbrSourceController extends BaseController
{
    private static final Set<String> TABLES = Set.of("abr_school", "abr_program", "abr_requirement");

    @Autowired
    private AbrFieldSourceMapper sourceMapper;

    @Autowired
    private IAbrSourceArchiveService archiveService;

    @Autowired
    private IAbrFieldRollbackService rollbackService;

    /**
     * 记录的字段来源（含存档标题与时间）
     */
    @PreAuthorize("@ss.hasPermi('abroad:source:query')")
    @GetMapping("/sources")
    public AjaxResult sources(@RequestParam String tableName, @RequestParam Long recordId)
    {
        if (!TABLES.contains(tableName))
        {
            return error("不支持的表");
        }
        return success(sourceMapper.selectAbrFieldSourceByRecord(tableName, recordId));
    }

    /**
     * 存档原文
     */
    @PreAuthorize("@ss.hasPermi('abroad:source:query')")
    @GetMapping("/snapshot/{snapshotId}")
    public AjaxResult snapshot(@PathVariable Long snapshotId)
    {
        return success(archiveService.selectSnapshot(snapshotId));
    }

    /**
     * 字段变化历史
     */
    @PreAuthorize("@ss.hasPermi('abroad:source:query')")
    @GetMapping("/history")
    public TableDataInfo history(@RequestParam String tableName, @RequestParam Long recordId)
    {
        if (!TABLES.contains(tableName))
        {
            return getDataTable(List.of());
        }
        startPage();
        List<AbrFieldHistory> list = archiveService.selectHistory(tableName, recordId);
        return getDataTable(list);
    }

    /**
     * 回滚一次字段变化
     */
    @PreAuthorize("@ss.hasPermi('abroad:source:rollback')")
    @Log(title = "院校库字段回滚", businessType = BusinessType.UPDATE)
    @PutMapping("/history/rollback/{historyId}")
    public AjaxResult rollback(@PathVariable Long historyId)
    {
        return toAjax(rollbackService.rollback(historyId, getUsername()));
    }
}

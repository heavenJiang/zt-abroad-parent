package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrAiReview;
import com.ygb.abroad.service.IAbrAiReviewService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * AI 管理-审核队列
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/aiReview")
public class AbrAiReviewController extends BaseController
{
    @Autowired
    private IAbrAiReviewService reviewService;

    /**
     * 查询审核记录列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiReview:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrAiReview review)
    {
        startPage();
        List<AbrAiReview> list = reviewService.selectAbrAiReviewList(review);
        return getDataTable(list);
    }

    /**
     * 获取审核记录详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiReview:query')")
    @GetMapping(value = "/{reviewId}")
    public AjaxResult getInfo(@PathVariable Long reviewId)
    {
        return success(reviewService.selectAbrAiReviewById(reviewId));
    }

    /**
     * 审核通过，返回最终内容
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiReview:approve')")
    @Log(title = "AI 审核", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{reviewId}")
    public AjaxResult approve(@PathVariable Long reviewId)
    {
        return success(reviewService.approveAbrAiReview(reviewId, getUsername()));
    }

    /**
     * 审核驳回（必须填写原因）
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiReview:reject')")
    @Log(title = "AI 审核", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{reviewId}")
    public AjaxResult reject(@PathVariable Long reviewId, @RequestBody AbrAiReview review)
    {
        return toAjax(reviewService.rejectAbrAiReview(reviewId, review.getReason(), getUsername()));
    }

    /**
     * 修改后通过（editedContent 为 JSON 文本，按该 Agent 的输出结构校验），返回最终内容
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiReview:edit')")
    @Log(title = "AI 审核", businessType = BusinessType.UPDATE)
    @PutMapping("/edit/{reviewId}")
    public AjaxResult edit(@PathVariable Long reviewId, @RequestBody AbrAiReview review)
    {
        return success(reviewService.editAbrAiReview(reviewId, review.getEditedContent(), review.getReason(), getUsername()));
    }
}

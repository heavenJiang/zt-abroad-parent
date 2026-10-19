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
import com.ygb.abroad.service.IAbrExceptionService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-例外队列与抽检（复用 AI 审核队列）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/exception")
public class AbrExceptionController extends BaseController
{
    @Autowired
    private IAbrExceptionService exceptionService;

    /**
     * 查询例外与抽检记录（queueType 为 exception / sample，不填两类都列）
     */
    @PreAuthorize("@ss.hasPermi('abroad:exception:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrAiReview review)
    {
        startPage();
        List<AbrAiReview> list = exceptionService.selectExceptionList(review);
        return getDataTable(list);
    }

    /**
     * 获取记录详情（含快照、命中项）
     */
    @PreAuthorize("@ss.hasPermi('abroad:exception:query')")
    @GetMapping("/{reviewId}")
    public AjaxResult getInfo(@PathVariable Long reviewId)
    {
        return success(exceptionService.selectExceptionById(reviewId));
    }

    /**
     * 例外：放行（reason 为审核意见，可空）
     */
    @PreAuthorize("@ss.hasPermi('abroad:exception:handle')")
    @Log(title = "例外队列", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{reviewId}")
    public AjaxResult release(@PathVariable Long reviewId, @RequestBody AbrAiReview review)
    {
        return success(exceptionService.release(reviewId, review.getReason(), getUsername()));
    }

    /**
     * 例外：确认拦截（必须填写原因；AI 生成的回流评测集）
     */
    @PreAuthorize("@ss.hasPermi('abroad:exception:handle')")
    @Log(title = "例外队列", businessType = BusinessType.UPDATE)
    @PutMapping("/block/{reviewId}")
    public AjaxResult block(@PathVariable Long reviewId, @RequestBody AbrAiReview review)
    {
        return success(exceptionService.confirmBlock(reviewId, review.getReason(), getUsername()));
    }

    /**
     * 抽检：合格
     */
    @PreAuthorize("@ss.hasPermi('abroad:exception:sample')")
    @Log(title = "抽检", businessType = BusinessType.UPDATE)
    @PutMapping("/pass/{reviewId}")
    public AjaxResult pass(@PathVariable Long reviewId)
    {
        return success(exceptionService.samplePass(reviewId, getUsername()));
    }

    /**
     * 抽检：不合格（必须填写原因；未发布的内容同时拦下；回流评测集）
     */
    @PreAuthorize("@ss.hasPermi('abroad:exception:sample')")
    @Log(title = "抽检", businessType = BusinessType.UPDATE)
    @PutMapping("/fail/{reviewId}")
    public AjaxResult fail(@PathVariable Long reviewId, @RequestBody AbrAiReview review)
    {
        return success(exceptionService.sampleFail(reviewId, review.getReason(), getUsername()));
    }
}

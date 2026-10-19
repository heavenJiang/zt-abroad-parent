package com.ygb.web.controller.abroad;

import java.util.List;
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
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.abroad.service.IAbrProductService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-产品与报价（任意数量；价格或区间可为空，C 端显示“价格面议”）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/product")
public class AbrProductController extends BaseController
{
    @Autowired
    private IAbrProductService productService;

    /**
     * 查询产品列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:product:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrProduct product)
    {
        startPage();
        List<AbrProduct> list = productService.selectAbrProductList(product);
        return getDataTable(list);
    }

    /**
     * 获取产品详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:product:query')")
    @GetMapping("/{productId}")
    public AjaxResult getInfo(@PathVariable Long productId)
    {
        return success(productService.selectAbrProductById(productId));
    }

    /**
     * 新增产品
     */
    @PreAuthorize("@ss.hasPermi('abroad:product:add')")
    @Log(title = "产品与报价", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrProduct product)
    {
        product.setCreateBy(getUsername());
        return toAjax(productService.insertAbrProduct(product));
    }

    /**
     * 修改产品（编码不能改）
     */
    @PreAuthorize("@ss.hasPermi('abroad:product:edit')")
    @Log(title = "产品与报价", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrProduct product)
    {
        product.setUpdateBy(getUsername());
        return toAjax(productService.updateAbrProduct(product));
    }

    /**
     * 删除产品（被引用的不能删）
     */
    @PreAuthorize("@ss.hasPermi('abroad:product:remove')")
    @Log(title = "产品与报价", businessType = BusinessType.DELETE)
    @DeleteMapping("/{productIds}")
    public AjaxResult remove(@PathVariable Long[] productIds)
    {
        return toAjax(productService.deleteAbrProductByIds(productIds));
    }
}

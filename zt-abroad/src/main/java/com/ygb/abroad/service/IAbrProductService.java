package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.abroad.domain.vo.AppProductVo;

/**
 * 产品与报价 服务层（任意数量的产品，价格或区间可为空；介绍保存前过合规词表）
 *
 * @author ygb
 */
public interface IAbrProductService
{
    /**
     * 查询产品列表
     *
     * @param product 查询条件
     * @return 产品集合
     */
    public List<AbrProduct> selectAbrProductList(AbrProduct product);

    /**
     * 按ID查询产品
     *
     * @param productId 产品ID
     * @return 产品
     */
    public AbrProduct selectAbrProductById(Long productId);

    /**
     * 按编码查询在用的产品
     *
     * @param productCode 产品编码
     * @return 产品；不存在或已停用时抛出业务异常
     */
    public AbrProduct requireActive(String productCode);

    /**
     * 新增产品（编码唯一）
     *
     * @param product 产品
     * @return 结果
     */
    public int insertAbrProduct(AbrProduct product);

    /**
     * 修改产品（编码不能改）
     *
     * @param product 产品
     * @return 结果
     */
    public int updateAbrProduct(AbrProduct product);

    /**
     * 删除产品（被预约、订单、剧本或推荐引用的不能删，改为停用）
     *
     * @param productIds 产品ID
     * @return 结果
     */
    public int deleteAbrProductByIds(Long[] productIds);

    /**
     * C 端：在用的产品（只含对外字段与价格展示）
     *
     * @param bookableOnly 是否只要可在线预约的
     * @return 产品
     */
    public List<AppProductVo> appProducts(boolean bookableOnly);
}

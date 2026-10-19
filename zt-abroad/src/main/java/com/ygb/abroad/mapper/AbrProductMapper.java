package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrProduct;

/**
 * 产品与报价 数据层
 *
 * @author ygb
 */
public interface AbrProductMapper
{
    /**
     * 查询产品列表（类别、状态、是否可预约精确；名称模糊）
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
     * 按编码查询产品
     *
     * @param productCode 产品编码
     * @return 产品
     */
    public AbrProduct selectAbrProductByCode(String productCode);

    /**
     * 新增产品
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
     * 删除产品
     *
     * @param productIds 产品ID
     * @return 结果
     */
    public int deleteAbrProductByIds(Long[] productIds);

    /**
     * 产品被引用的次数（预约、订单、剧本、转化时刻推荐）
     *
     * @param productCode 产品编码
     * @return 次数
     */
    public int countReferences(String productCode);
}

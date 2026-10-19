package com.ygb.abroad.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.abroad.domain.vo.AppProductVo;
import com.ygb.abroad.growth.compliance.ComplianceChecker;
import com.ygb.abroad.mapper.AbrProductMapper;
import com.ygb.abroad.service.IAbrComplianceService;
import com.ygb.abroad.service.IAbrProductService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 产品与报价 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrProductServiceImpl implements IAbrProductService
{
    @Autowired
    private AbrProductMapper productMapper;

    @Autowired
    private IAbrComplianceService complianceService;

    @Override
    public List<AbrProduct> selectAbrProductList(AbrProduct product)
    {
        return productMapper.selectAbrProductList(product);
    }

    @Override
    public AbrProduct selectAbrProductById(Long productId)
    {
        return productMapper.selectAbrProductById(productId);
    }

    @Override
    public AbrProduct requireActive(String productCode)
    {
        AbrProduct p = StringUtils.isBlank(productCode) ? null : productMapper.selectAbrProductByCode(productCode.trim());
        if (p == null || !"0".equals(p.getStatus()))
        {
            throw new ServiceException("产品不存在或已停用");
        }
        return p;
    }

    @Override
    public int insertAbrProduct(AbrProduct product)
    {
        if (productMapper.selectAbrProductByCode(product.getProductCode()) != null)
        {
            throw new ServiceException("产品编码“" + product.getProductCode() + "”已存在");
        }
        normalize(product);
        return productMapper.insertAbrProduct(product);
    }

    @Override
    public int updateAbrProduct(AbrProduct product)
    {
        AbrProduct before = product.getProductId() == null ? null : productMapper.selectAbrProductById(product.getProductId());
        if (before == null)
        {
            throw new ServiceException("产品不存在");
        }
        product.setProductCode(before.getProductCode());
        normalize(product);
        return productMapper.updateAbrProduct(product);
    }

    @Override
    public int deleteAbrProductByIds(Long[] productIds)
    {
        for (Long id : productIds)
        {
            AbrProduct p = productMapper.selectAbrProductById(id);
            if (p != null && productMapper.countReferences(p.getProductCode()) > 0)
            {
                throw new ServiceException("产品“" + p.getProductName() + "”已被预约、订单、剧本或推荐引用，不能删除，可改为停用");
            }
        }
        return productMapper.deleteAbrProductByIds(productIds);
    }

    @Override
    public List<AppProductVo> appProducts(boolean bookableOnly)
    {
        AbrProduct q = new AbrProduct();
        q.setStatus("0");
        if (bookableOnly)
        {
            q.setBookable("Y");
        }
        return productMapper.selectAbrProductList(q).stream().map(AbrProductServiceImpl::vo).toList();
    }

    static AppProductVo vo(AbrProduct p)
    {
        return new AppProductVo(p.getProductCode(), p.getProductName(), p.getCategory(), StringUtils.defaultString(p.getDescription()),
                AbrBookingRules.priceText(p.getPriceMin(), p.getPriceMax(), p.getCurrency(), p.getPriceNote()), "Y".equals(p.getBookable()),
                p.getDurationMinutes());
    }

    /**
     * 校验并规范化：类别、价格、预约时长、学段与国家；名称、介绍、价格说明不得有 HTML 写法，且须通过合规词表（对外展示）
     */
    private void normalize(AbrProduct p)
    {
        AbrBookingRules.validate(p);
        p.setDegrees(codes(p.getDegrees(), "abr_degree", "适用学段"));
        p.setCountries(codes(p.getCountries(), "abr_country", "适用国家 / 地区"));
        p.setProductName(StringUtils.trim(p.getProductName()));
        p.setDescription(StringUtils.trimToEmpty(p.getDescription()));
        p.setPriceNote(StringUtils.trimToEmpty(p.getPriceNote()));
        String text = p.getProductName() + "\n" + p.getDescription() + "\n" + p.getPriceNote();
        if (AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
        {
            throw new ServiceException("产品名称、介绍、价格说明不能包含 < 加字母的写法（类似 HTML 标签）");
        }
        ComplianceChecker.Result r = complianceService.checkMessage(null, text, "[]", false);
        if (!r.passed())
        {
            Map<String, Object> b = r.blocked().get(0);
            throw new ServiceException("产品介绍未通过合规词表：" + StringUtils.defaultString((String) b.get("matched"), String.valueOf(b.get("detail"))) + "（"
                    + b.get("suggestion") + "）");
        }
    }

    private static String codes(String json, String dict, String label)
    {
        if (StringUtils.isBlank(json))
        {
            return "[]";
        }
        Set<String> allowed = AbrPlaybookServiceImpl.dictValues(dict);
        JsonNode node = AbrJsonFields.parse(json, label);
        if (node == null || !node.isArray())
        {
            throw new ServiceException(label + "必须是 JSON 数组");
        }
        for (JsonNode n : node)
        {
            if (!allowed.contains(n.asString("")))
            {
                throw new ServiceException(label + "中的“" + n.asString("") + "”不是可选值");
            }
        }
        return node.toString();
    }
}

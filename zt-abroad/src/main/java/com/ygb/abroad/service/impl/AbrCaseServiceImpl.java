package com.ygb.abroad.service.impl;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrCase;
import com.ygb.abroad.domain.vo.AppOfferVo;
import com.ygb.abroad.integration.AbrDocumentText;
import com.ygb.abroad.integration.AbrPrivateFileStore;
import com.ygb.abroad.mapper.AbrCaseMapper;
import com.ygb.abroad.service.IAbrCaseService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;

/**
 * 录取案例与 offer 库 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrCaseServiceImpl implements IAbrCaseService
{
    private static final Logger log = LoggerFactory.getLogger(AbrCaseServiceImpl.class);

    /** offer 截图允许的格式 */
    static final Set<String> OFFER_EXTS = Set.of("pdf", "jpg", "png");

    /** offer 截图大小上限（10MB） */
    static final int MAX_OFFER_BYTES = 10 * 1024 * 1024;

    @Autowired
    private AbrCaseMapper caseMapper;

    @Autowired
    private AbrPrivateFileStore fileStore;

    @Autowired
    private AbrShareSupport shareSupport;

    @Autowired
    private IAbrEventService eventService;

    @Override
    public List<AbrCase> selectCaseList(AbrCase c)
    {
        return caseMapper.selectCaseList(c);
    }

    @Override
    public AbrCase selectCaseById(Long caseId)
    {
        return caseMapper.selectCaseById(caseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCase(AbrCase c, boolean keepIdentity)
    {
        AbrCaseRules.validate(c);
        AbrCase old = c.getCaseId() == null ? null : caseMapper.selectCaseById(c.getCaseId());
        if (c.getCaseId() != null && old == null)
        {
            throw new ServiceException("案例不存在");
        }
        if (keepIdentity)
        {
            c.setLeadId(old == null ? null : old.getLeadId());
        }
        if (c.getLeadId() != null && c.getProgramId() != null && caseMapper.countDuplicate(c.getLeadId(), c.getProgramId(), c.getSeason(), c.getCaseId()) > 0)
        {
            throw new ServiceException("这位学生在这个项目、这个申请季已有结果");
        }
        boolean hadAny = old != null && ("Y".equals(old.getAlgoConsent()) || "Y".equals(old.getPublicityConsent()));
        boolean hasAny = "Y".equals(c.getAlgoConsent()) || "Y".equals(c.getPublicityConsent());
        boolean newPublicity = "Y".equals(c.getPublicityConsent()) && (old == null || !"Y".equals(old.getPublicityConsent()));
        c.setConsentTime(old == null ? null : old.getConsentTime());
        c.setRevokeTime(old == null ? null : old.getRevokeTime());
        if (hasAny && (!hadAny || newPublicity || ("Y".equals(c.getAlgoConsent()) && !"Y".equals(old.getAlgoConsent()))))
        {
            c.setConsentTime(DateUtils.getNowDate());
            c.setRevokeTime(null);
        }
        else if (!hasAny && hadAny)
        {
            c.setRevokeTime(DateUtils.getNowDate());
        }
        if (c.getCaseId() == null)
        {
            caseMapper.insertCase(c);
        }
        else
        {
            caseMapper.updateCase(c);
        }
        if (newPublicity && AbrCaseRules.publicEligible(c))
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("case_id", c.getCaseId());
            payload.put("program_id", c.getProgramId());
            payload.put("country", c.getCountry());
            payload.put("season", c.getSeason());
            eventService.recordSystemEvent(EVENT_OFFER_AUTHORIZED, "case:" + c.getCaseId() + ":" + DateUtils.dateTimeNow(), payload);
        }
        return c.getCaseId();
    }

    @Override
    public int revoke(Long caseId, String operator)
    {
        AbrCase c = require(caseId);
        c.setAlgoConsent("N");
        c.setPublicityConsent("N");
        c.setRevokeTime(DateUtils.getNowDate());
        c.setUpdateBy(operator);
        return caseMapper.updateCase(c);
    }

    @Override
    public int deleteCases(Long[] caseIds)
    {
        for (Long id : caseIds)
        {
            AbrCase c = caseMapper.selectCaseById(id);
            if (c != null && StringUtils.isNotBlank(c.getOfferFile()))
            {
                fileStore.delete(c.getOfferFile());
            }
        }
        return caseMapper.deleteCaseByIds(caseIds);
    }

    @Override
    public void saveOfferFile(Long caseId, byte[] bytes, String ext, String operator)
    {
        AbrCase c = require(caseId);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_OFFER_BYTES)
        {
            throw new ServiceException("文件为空或超过 10MB");
        }
        // 格式按文件头判断，不信任扩展名
        String e = AbrDocumentText.detectType(bytes);
        if (e == null || !OFFER_EXTS.contains(e))
        {
            throw new ServiceException("offer 截图只支持 PDF、JPG、PNG");
        }
        String path = fileStore.save(bytes, e);
        caseMapper.updateOfferFile(caseId, path, operator);
        if (StringUtils.isNotBlank(c.getOfferFile()))
        {
            fileStore.delete(c.getOfferFile());
        }
    }

    @Override
    public Object[] readOfferFile(Long caseId)
    {
        AbrCase c = require(caseId);
        if (StringUtils.isBlank(c.getOfferFile()))
        {
            throw new ServiceException("没有上传 offer 截图");
        }
        try
        {
            String path = c.getOfferFile();
            return new Object[] { fileStore.read(path), path.substring(path.lastIndexOf('.') + 1) };
        }
        catch (IOException e)
        {
            log.warn("读取 offer 截图失败 case={}：{}", caseId, e.getMessage());
            throw new ServiceException("offer 截图读取失败");
        }
    }

    @Override
    public byte[] cardPng(Long caseId)
    {
        AbrCase c = require(caseId);
        if (!AbrCaseRules.publicEligible(c))
        {
            throw new ServiceException("只有录取、核验 A / B、取得宣传授权且未撤回的案例可以生成 offer 卡");
        }
        return png(c);
    }

    @Override
    public List<AppOfferVo> publicOffers(String country, String degree)
    {
        return caseMapper.selectPublicOffers(StringUtils.trimToNull(country), StringUtils.trimToNull(degree)).stream()
                .map(c -> new AppOfferVo(c.getCaseId(), AbrCaseRules.seasonLabel(c.getSeason()), label("abr_country", c.getCountry()),
                        label("abr_degree", c.getDegree()), c.getSchoolName(), c.getProgramName(), StringUtils.trimToNull(c.getScholarship()),
                        AbrCaseRules.background(c, AbrCaseServiceImpl::label)))
                .toList();
    }

    @Override
    public byte[] publicCardPng(Long caseId)
    {
        AbrCase c = publicCase(caseId);
        if (c == null)
        {
            throw new ServiceException("offer 不存在");
        }
        return png(c);
    }

    @Override
    public AbrCase publicCase(Long caseId)
    {
        AbrCase c = caseId == null ? null : caseMapper.selectCaseById(caseId);
        return AbrCaseRules.publicEligible(c) ? c : null;
    }

    private byte[] png(AbrCase c)
    {
        String qr = shareSupport.appLink("?ch=offer&ct=o" + c.getCaseId());
        return shareSupport.png(AbrCaseRules.card(c, AbrCaseServiceImpl::label, shareSupport.brandName(), qr));
    }

    private AbrCase require(Long caseId)
    {
        AbrCase c = caseId == null ? null : caseMapper.selectCaseById(caseId);
        if (c == null)
        {
            throw new ServiceException("案例不存在");
        }
        return c;
    }

    private static String label(String dictType, String value)
    {
        String l = StringUtils.isBlank(value) ? null : DictUtils.getDictLabel(dictType, value);
        return StringUtils.defaultIfBlank(l, StringUtils.defaultString(value));
    }
}

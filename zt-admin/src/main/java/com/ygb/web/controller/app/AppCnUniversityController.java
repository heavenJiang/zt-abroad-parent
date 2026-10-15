package com.ygb.web.controller.app;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.vo.AppCnUniversityVo;
import com.ygb.abroad.service.IAbrCnUniversityService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;
import com.ygb.common.utils.StringUtils;

/**
 * C 端-国内高校搜索（快测与问卷“本科学校”题；只返回ID与校名，不返回院校层次）
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/cn-university")
public class AppCnUniversityController extends BaseController
{
    /** 每次最多返回条数 */
    private static final int LIMIT = 20;

    /** 关键词最大长度 */
    private static final int MAX_KEYWORD = 32;

    @Autowired
    private IAbrCnUniversityService universityService;

    /**
     * 按校名或简称搜索（至少 2 个字）
     */
    @RateLimiter(key = "app_cn_university:", time = 60, count = 120, limitType = LimitType.IP)
    @GetMapping("/search")
    public AjaxResult search(@RequestParam(value = "keyword", required = false) String keyword)
    {
        String kw = StringUtils.substring(StringUtils.trim(keyword), 0, MAX_KEYWORD);
        List<AppCnUniversityVo> list = universityService.searchAbrCnUniversity(kw, LIMIT).stream()
                .map(u -> new AppCnUniversityVo(u.getCnUniversityId(), u.getUniversityName())).toList();
        return success(list);
    }
}

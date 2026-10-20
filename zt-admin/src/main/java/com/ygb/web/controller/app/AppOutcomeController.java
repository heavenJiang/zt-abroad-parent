package com.ygb.web.controller.app;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ygb.abroad.domain.vo.AppOutcomeBody;
import com.ygb.abroad.service.IAbrOutcomeService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-录取结果回填（须登录，本人取学生令牌；结果与两项授权分开勾选，可随时撤回或删除）
 *
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/outcome")
public class AppOutcomeController extends BaseController
{
    @Autowired
    private IAbrOutcomeService outcomeService;

    /**
     * 我回填的结果与收到的感谢
     */
    @RateLimiter(key = "app_outcome_mine:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping
    public AjaxResult mine()
    {
        return success(outcomeService.mine());
    }

    /**
     * 回填一条结果（programId、season、result、scholarship、algoConsent、publicityConsent）
     */
    @RateLimiter(key = "app_outcome_submit:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping
    public AjaxResult submit(@RequestBody AppOutcomeBody body)
    {
        return success(outcomeService.submit(body));
    }

    /**
     * 上传 offer 截图（PDF / JPG / PNG，不超过 10MB）
     */
    @RateLimiter(key = "app_outcome_offer:", time = 60, count = 5, limitType = LimitType.IP)
    @PostMapping("/{id}/offer")
    public AjaxResult offer(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择文件");
        }
        return success(outcomeService.uploadOffer(id, file.getBytes(), file.getOriginalFilename()));
    }

    /**
     * 撤回一条回填的全部授权
     */
    @RateLimiter(key = "app_outcome_revoke:", time = 60, count = 10, limitType = LimitType.IP)
    @PutMapping("/{id}/revoke")
    public AjaxResult revoke(@PathVariable Long id)
    {
        return success(outcomeService.revoke(id));
    }

    /**
     * 删除一条回填
     */
    @RateLimiter(key = "app_outcome_delete:", time = 60, count = 10, limitType = LimitType.IP)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id)
    {
        return success(outcomeService.delete(id));
    }
}

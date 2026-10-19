package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrAppUser;
import com.ygb.abroad.service.IAbrAppAuthService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-C 端账号（学生账号；手机号脱敏，明文须单独权限并记操作日志）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/appUser")
public class AbrAppUserController extends BaseController
{
    @Autowired
    private IAbrAppAuthService authService;

    /**
     * 查询账号列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:appUser:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrAppUser user)
    {
        startPage();
        List<AbrAppUser> list = authService.selectAbrAppUserList(user);
        return getDataTable(list);
    }

    /**
     * 获取账号详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:appUser:query')")
    @GetMapping("/{appUserId}")
    public AjaxResult getInfo(@PathVariable Long appUserId)
    {
        return success(authService.selectAbrAppUserById(appUserId));
    }

    /**
     * 查看手机号明文（记操作日志，日志不保存返回内容）
     */
    @PreAuthorize("@ss.hasPermi('abroad:appUser:phone')")
    @Log(title = "C 端账号-查看手机号", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/phone/{appUserId}")
    public AjaxResult phone(@PathVariable Long appUserId)
    {
        return success().put(AjaxResult.DATA_TAG, authService.selectPhone(appUserId));
    }

    /**
     * 停用（status=1）/ 启用（status=0）
     */
    @PreAuthorize("@ss.hasPermi('abroad:appUser:edit')")
    @Log(title = "C 端账号", businessType = BusinessType.UPDATE)
    @PutMapping("/status/{appUserId}/{status}")
    public AjaxResult status(@PathVariable Long appUserId, @PathVariable String status)
    {
        return toAjax(authService.updateStatus(appUserId, status, getUsername()));
    }
}

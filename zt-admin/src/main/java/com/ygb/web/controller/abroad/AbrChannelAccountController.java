package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
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
import com.ygb.abroad.domain.AbrChannelAccount;
import com.ygb.abroad.service.IAbrChannelAccountService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.domain.entity.SysUser;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.system.service.ISysUserService;

/**
 * 内容引擎-渠道账号（账号矩阵：平台、负责人、人设、授权、发布方式与每天上限、账号级暂停）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/content/account")
public class AbrChannelAccountController extends BaseController
{
    @Autowired
    private IAbrChannelAccountService accountService;

    @Autowired
    private ISysUserService userService;

    /**
     * 查询账号列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:account:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrChannelAccount account)
    {
        startPage();
        List<AbrChannelAccount> list = accountService.selectAbrChannelAccountList(account);
        return getDataTable(list);
    }

    /**
     * 获取账号详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:account:query')")
    @GetMapping(value = "/{accountId}")
    public AjaxResult getInfo(@PathVariable Long accountId)
    {
        return success(accountService.selectAbrChannelAccountById(accountId));
    }

    /**
     * 新增账号
     */
    @PreAuthorize("@ss.hasPermi('abroad:account:add')")
    @Log(title = "渠道账号", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrChannelAccount account)
    {
        if (!accountService.checkAccountCodeUnique(account))
        {
            return error("新增账号'" + account.getAccountName() + "'失败，账号编码已存在");
        }
        String owner = ownerName(account.getOwnerUserId());
        if (owner == null)
        {
            return error("负责人不存在");
        }
        account.setOwnerName(owner);
        account.setCreateBy(getUsername());
        return toAjax(accountService.insertAbrChannelAccount(account));
    }

    /**
     * 修改账号
     */
    @PreAuthorize("@ss.hasPermi('abroad:account:edit')")
    @Log(title = "渠道账号", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrChannelAccount account)
    {
        if (!accountService.checkAccountCodeUnique(account))
        {
            return error("修改账号'" + account.getAccountName() + "'失败，账号编码已存在");
        }
        String owner = ownerName(account.getOwnerUserId());
        if (owner == null)
        {
            return error("负责人不存在");
        }
        account.setOwnerName(owner);
        account.setUpdateBy(getUsername());
        return toAjax(accountService.updateAbrChannelAccount(account));
    }

    /**
     * 删除账号
     */
    @PreAuthorize("@ss.hasPermi('abroad:account:remove')")
    @Log(title = "渠道账号", businessType = BusinessType.DELETE)
    @DeleteMapping("/{accountIds}")
    public AjaxResult remove(@PathVariable Long[] accountIds)
    {
        return toAjax(accountService.deleteAbrChannelAccountByIds(accountIds));
    }

    /**
     * 暂停账号（body：reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:account:pause')")
    @Log(title = "渠道账号暂停", businessType = BusinessType.UPDATE)
    @PutMapping("/pause/{accountId}")
    public AjaxResult pause(@PathVariable Long accountId, @RequestBody Map<String, String> body)
    {
        return accountService.pauseAccount(accountId, body.get("reason"), getUsername()) ? success("已暂停") : error("已经是暂停状态");
    }

    /**
     * 恢复账号
     */
    @PreAuthorize("@ss.hasPermi('abroad:account:pause')")
    @Log(title = "渠道账号恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/resume/{accountId}")
    public AjaxResult resume(@PathVariable Long accountId)
    {
        return accountService.resumeAccount(accountId, getUsername()) ? success("已恢复") : error("当前没有暂停");
    }

    /**
     * 负责人姓名（未指定负责人返回空串，用户不存在返回 null）
     */
    private String ownerName(Long userId)
    {
        if (userId == null)
        {
            return "";
        }
        SysUser user = userService.selectUserById(userId);
        return user == null ? null : user.getNickName();
    }
}

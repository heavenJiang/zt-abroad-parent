package com.ygb.abroad.domain.vo;

/**
 * C 端：登录与绑定手机号的请求体
 *
 * @author ygb
 */
public class AppAuthBody
{
    /** 授权后回到的页面（取授权地址时） */
    private String redirect;

    /** 微信授权 code */
    private String code;

    /** 防伪随机串（取授权地址时返回，回调时带回） */
    private String state;

    /** 手机号 */
    private String phone;

    /** 短信验证码 */
    private String smsCode;

    /** 是否勾选手机号单独同意 */
    private Boolean consent;

    /** 同意时阅读的隐私政策版本 */
    private String policyVersion;

    public String getRedirect()
    {
        return redirect;
    }

    public void setRedirect(String redirect)
    {
        this.redirect = redirect;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getState()
    {
        return state;
    }

    public void setState(String state)
    {
        this.state = state;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getSmsCode()
    {
        return smsCode;
    }

    public void setSmsCode(String smsCode)
    {
        this.smsCode = smsCode;
    }

    public Boolean getConsent()
    {
        return consent;
    }

    public void setConsent(Boolean consent)
    {
        this.consent = consent;
    }

    public String getPolicyVersion()
    {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion)
    {
        this.policyVersion = policyVersion;
    }
}

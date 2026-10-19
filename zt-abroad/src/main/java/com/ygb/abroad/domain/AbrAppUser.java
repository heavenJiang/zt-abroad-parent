package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ygb.common.annotation.Sensitive;
import com.ygb.common.core.domain.BaseEntity;
import com.ygb.common.enums.DesensitizedType;

/**
 * 学生账号 abr_app_user（C 端；不进 sys_user）
 *
 * @author ygb
 */
public class AbrAppUser extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 是否已绑定服务号、小程序、手机号（管理端展示用，openid 本身不返回） */
    private Boolean oaBound;

    private Boolean miniBound;

    private Boolean phoneBound;

    /** 学生账号ID */
    private Long appUserId;

    /** 称呼 */
    private String nickname;

    /** 服务号 openid */
    @JsonIgnore
    private String oaOpenid;

    /** 小程序 openid */
    @JsonIgnore
    private String miniOpenid;

    /** 微信 unionid */
    @JsonIgnore
    private String unionid;

    /** 手机号（管理端列表与详情返回脱敏值；明文须单独权限） */
    @Sensitive(desensitizedType = DesensitizedType.PHONE)
    private String phone;

    /** 手机号单独同意时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date phoneConsentTime;

    /** 同意时的隐私政策版本 */
    private String phoneConsentVersion;

    /** 主访客ID */
    private Long primaryVisitorId;

    /** 注册时的渠道码 */
    private String registerChannel;

    /** 注册时的内容编号 */
    private String registerContent;

    /** 登录次数 */
    private Integer loginCount;

    /** 最近登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志 */
    private String delFlag;

    public Boolean getOaBound()
    {
        return oaBound;
    }

    public void setOaBound(Boolean oaBound)
    {
        this.oaBound = oaBound;
    }

    public Boolean getMiniBound()
    {
        return miniBound;
    }

    public void setMiniBound(Boolean miniBound)
    {
        this.miniBound = miniBound;
    }

    public Boolean getPhoneBound()
    {
        return phoneBound;
    }

    public void setPhoneBound(Boolean phoneBound)
    {
        this.phoneBound = phoneBound;
    }

    public Long getAppUserId()
    {
        return appUserId;
    }

    public void setAppUserId(Long appUserId)
    {
        this.appUserId = appUserId;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getOaOpenid()
    {
        return oaOpenid;
    }

    public void setOaOpenid(String oaOpenid)
    {
        this.oaOpenid = oaOpenid;
    }

    public String getMiniOpenid()
    {
        return miniOpenid;
    }

    public void setMiniOpenid(String miniOpenid)
    {
        this.miniOpenid = miniOpenid;
    }

    public String getUnionid()
    {
        return unionid;
    }

    public void setUnionid(String unionid)
    {
        this.unionid = unionid;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public Date getPhoneConsentTime()
    {
        return phoneConsentTime;
    }

    public void setPhoneConsentTime(Date phoneConsentTime)
    {
        this.phoneConsentTime = phoneConsentTime;
    }

    public String getPhoneConsentVersion()
    {
        return phoneConsentVersion;
    }

    public void setPhoneConsentVersion(String phoneConsentVersion)
    {
        this.phoneConsentVersion = phoneConsentVersion;
    }

    public Long getPrimaryVisitorId()
    {
        return primaryVisitorId;
    }

    public void setPrimaryVisitorId(Long primaryVisitorId)
    {
        this.primaryVisitorId = primaryVisitorId;
    }

    public String getRegisterChannel()
    {
        return registerChannel;
    }

    public void setRegisterChannel(String registerChannel)
    {
        this.registerChannel = registerChannel;
    }

    public String getRegisterContent()
    {
        return registerContent;
    }

    public void setRegisterContent(String registerContent)
    {
        this.registerContent = registerContent;
    }

    public Integer getLoginCount()
    {
        return loginCount;
    }

    public void setLoginCount(Integer loginCount)
    {
        this.loginCount = loginCount;
    }

    public Date getLastLoginTime()
    {
        return lastLoginTime;
    }

    public void setLastLoginTime(Date lastLoginTime)
    {
        this.lastLoginTime = lastLoginTime;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("appUserId", getAppUserId())
            .append("nickname", getNickname())
            .append("phoneConsentTime", getPhoneConsentTime())
            .append("phoneConsentVersion", getPhoneConsentVersion())
            .append("primaryVisitorId", getPrimaryVisitorId())
            .append("registerChannel", getRegisterChannel())
            .append("registerContent", getRegisterContent())
            .append("loginCount", getLoginCount())
            .append("lastLoginTime", getLastLoginTime())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

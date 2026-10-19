package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrAppUser;
import com.ygb.abroad.domain.vo.AppUserVo;

/**
 * C 端账号规则测试：手机号格式、脱敏、管理端返回前脱敏、C 端账号视图
 *
 * @author ygb
 */
class AbrAppAuthRulesTest
{
    @Test
    void phoneFormatAndMask()
    {
        assertTrue(AbrAppAuthServiceImpl.PHONE.matcher("13812345678").matches());
        assertFalse(AbrAppAuthServiceImpl.PHONE.matcher("12812345678").matches());
        assertFalse(AbrAppAuthServiceImpl.PHONE.matcher("1381234567").matches());
        assertEquals("138****5678", AbrAppAuthServiceImpl.maskPhone("13812345678"));
        assertEquals("", AbrAppAuthServiceImpl.maskPhone(null));
    }

    @Test
    void maskForAdminAndVo()
    {
        AbrAppUser u = new AbrAppUser();
        u.setPhone("13812345678");
        u.setOaOpenid("o_1");
        AbrAppAuthServiceImpl.mask(u);
        assertEquals("138****5678", u.getPhone());
        assertTrue(u.getPhoneBound());
        assertTrue(u.getOaBound());
        assertFalse(u.getMiniBound());
        AbrAppUser v = new AbrAppUser();
        v.setNickname("同学");
        v.setMiniOpenid("m_1");
        AppUserVo vo = AbrAppAuthServiceImpl.vo(v);
        assertEquals("", vo.phone());
        assertFalse(vo.phoneBound());
        assertTrue(vo.wechatBound());
        assertEquals("", vo.registerTime());
    }
}

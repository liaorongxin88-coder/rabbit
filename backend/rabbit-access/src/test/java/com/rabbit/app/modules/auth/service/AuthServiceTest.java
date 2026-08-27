package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.UserProfileResponse;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.house.service.HouseInvitationService;
import com.rabbit.app.security.JwtUtil;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 账号认证。这里的每条分支要么决定「谁能进来」，要么决定「进来之后是谁」，
 * 两个方向的错误都不会在日常使用中暴露：放宽了没人报，收紧了用户只会以为自己记错密码。
 *
 * <p>重点守四件事：
 *
 * <ul>
 *   <li>停用账号在每一条入口（密码、手机号、微信、刷 token、改密码）都必须被挡住，
 *       只堵一两条等于没堵；</li>
 *   <li>没设过密码的账号（手机号/微信注册）不能用密码登录——它们的 password 列里是
 *       一串随机 UUID 的哈希，一旦 {@code passwordInitialized} 判断被绕过，
 *       就等于开了一条谁都不知道口令的后门；</li>
 *   <li>换绑手机必须先验证身份，且要在事务里复核绑定状态没被并发改掉，
 *       否则「换绑」会变成「抢号」；</li>
 *   <li>手机号唯一，绑到别人名下的号必须拒绝，DuplicateKey 也要翻译成同一个语义。</li>
 * </ul>
 */
class AuthServiceTest {
    private static final long USER_ID = 11L;
    private static final String PHONE = "13800001111";
    private static final String PHONE_HASH = "hash:13800001111";

    private SysUserMapper sysUserMapper;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private PhoneIdentityService phoneIdentityService;
    private HouseInvitationService houseInvitationService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(JwtUtil.class);
        phoneIdentityService = mock(PhoneIdentityService.class);
        houseInvitationService = mock(HouseInvitationService.class);
        when(passwordEncoder.encode(anyString())).thenAnswer(call -> "encoded:" + call.getArgument(0));
        when(jwtUtil.generateToken(anyLong())).thenReturn("jwt-token");
        when(phoneIdentityService.hash(PHONE)).thenReturn(PHONE_HASH);
        when(phoneIdentityService.mask(PHONE)).thenReturn("138****1111");
        when(sysUserMapper.insert(any())).thenAnswer(call -> {
            call.<SysUser>getArgument(0).setUserId(USER_ID);
            return 1;
        });
        service = new AuthService(
                sysUserMapper,
                passwordEncoder,
                jwtUtil,
                phoneIdentityService,
                houseInvitationService
        );
    }

    // ---------- 用户名注册 ----------

    @Test
    void registeringAnExistingUserNameIsRejected() {
        when(sysUserMapper.selectByUserName("alice")).thenReturn(enabledUser());

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.register("alice", "secret")
        ).getCode());
        verify(sysUserMapper, never()).insert(any());
    }

    @Test
    void userNameIsTrimmedBeforeTheUniquenessCheck() {
        service.register("  alice  ", "secret");

        verify(sysUserMapper).selectByUserName("alice");
        ArgumentCaptor<SysUser> created = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(created.capture());
        assertEquals("alice", created.getValue().getUserName());
    }

    @Test
    void blankOrOverlongUserNamesAreRejected() {
        assertEquals(400, assertThrows(BizException.class, () -> service.register("   ", "p")).getCode());
        assertEquals(400, assertThrows(BizException.class, () -> service.register(null, "p")).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.register("a".repeat(65), "p")
        ).getCode());
        verify(sysUserMapper, never()).insert(any());
    }

    @Test
    void registeredAccountIsEnabledAndOwnsItsPassword() {
        AuthTokenResponse response = service.register("alice", "secret");

        ArgumentCaptor<SysUser> created = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(created.capture());
        assertEquals("ENABLED", created.getValue().getStatus());
        assertTrue(created.getValue().getPasswordInitialized());
        assertEquals("encoded:secret", created.getValue().getPassword());
        assertNotNull(created.getValue().getUserCode());
        assertEquals("jwt-token", response.getToken());
        assertTrue(response.getHasPassword());
        assertFalse(response.getPhoneBound());
        assertTrue(response.getPermissions().contains("account:profile:query"));
    }

    /**
     * 账号是随机取的，撞了要换一个再试，而不是把唯一键冲突甩成 500。
     * 连撞五次才放弃——放弃时也得是明确的 500，不能返回一个已被占用的账号。
     */
    @Test
    void collidingUserCodeIsRetriedAndGivesUpLoudly() {
        when(sysUserMapper.selectByUserCode(anyString())).thenReturn(enabledUser());

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.register("alice", "secret")
        ).getCode());
        verify(sysUserMapper, never()).insert(any());
    }

    // ---------- 用户名密码登录 ----------

    @Test
    void loginWithUnknownUserNameIsRejected() {
        when(sysUserMapper.selectByUserName("ghost")).thenReturn(null);

        assertEquals(401, assertThrows(
                BizException.class,
                () -> service.login("ghost", "secret")
        ).getCode());
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        SysUser user = enabledUser();
        user.setPasswordInitialized(Boolean.TRUE);
        user.setPassword("encoded:secret");
        when(sysUserMapper.selectByUserName("alice")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded:secret")).thenReturn(false);

        assertEquals(401, assertThrows(
                BizException.class,
                () -> service.login("alice", "wrong")
        ).getCode());
    }

    /**
     * 手机号或微信注册的账号，password 列里存的是一串随机 UUID 的哈希，
     * 用户从来没设置过。如果只比对哈希不看 passwordInitialized，
     * 这些账号就多出一条谁也不知道口令、但确实存在的登录路径。
     */
    @Test
    void accountThatNeverSetAPasswordCannotLogInWithOne() {
        SysUser user = enabledUser();
        user.setPasswordInitialized(Boolean.FALSE);
        user.setPassword("encoded:random-uuid");
        when(sysUserMapper.selectByUserName("mobile_x")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertEquals(401, assertThrows(
                BizException.class,
                () -> service.login("mobile_x", "random-uuid")
        ).getCode());
        verify(jwtUtil, never()).generateToken(anyLong());
    }

    @Test
    void suspendedAccountCannotLogInEvenWithTheRightPassword() {
        SysUser user = enabledUser();
        user.setStatus("DISABLED");
        user.setPasswordInitialized(Boolean.TRUE);
        user.setPassword("encoded:secret");
        when(sysUserMapper.selectByUserName("alice")).thenReturn(user);
        when(passwordEncoder.matches("secret", "encoded:secret")).thenReturn(true);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.login("alice", "secret")
        ).getCode());
        verify(jwtUtil, never()).generateToken(anyLong());
    }

    @Test
    void validCredentialsIssueAToken() {
        SysUser user = enabledUser();
        user.setPasswordInitialized(Boolean.TRUE);
        user.setPassword("encoded:secret");
        when(sysUserMapper.selectByUserName("alice")).thenReturn(user);
        when(passwordEncoder.matches("secret", "encoded:secret")).thenReturn(true);

        AuthTokenResponse response = service.login("alice", "secret");

        assertEquals("jwt-token", response.getToken());
        assertEquals(USER_ID, response.getUserId());
    }

    // ---------- 刷新 token ----------

    @Test
    void refreshingATokenForASuspendedAccountIsRejected() {
        SysUser user = enabledUser();
        user.setStatus("DISABLED");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(user);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.refreshToken(USER_ID)
        ).getCode());
    }

    @Test
    void refreshingATokenWithoutAUserIdIsRejected() {
        assertEquals(401, assertThrows(BizException.class, () -> service.refreshToken(null)).getCode());
        verify(sysUserMapper, never()).selectById(anyLong());
    }

    @Test
    void refreshingATokenForADeletedAccountIsRejected() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(null);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.refreshToken(USER_ID)
        ).getCode());
    }

    // ---------- 手机号登录/注册 ----------

    @Test
    void loggingInWithAnUnregisteredPhoneIsRejected() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(null);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.loginPhone(PHONE)
        ).getCode());
        verify(houseInvitationService, never()).acceptPending(anyString(), anyLong());
    }

    @Test
    void suspendedAccountCannotLogInByPhone() {
        SysUser user = enabledUser();
        user.setStatus("DISABLED");
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(user);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.loginPhone(PHONE)
        ).getCode());
        verify(houseInvitationService, never()).acceptPending(anyString(), anyLong());
    }

    @Test
    void phoneLoginRedeemsInvitationsAddressedToThatNumber() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(enabledUser());

        service.loginPhone(PHONE);

        verify(houseInvitationService).acceptPending(PHONE_HASH, USER_ID);
    }

    @Test
    void firstPhoneLoginCreatesAnAccountWithoutAUsablePassword() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(null);

        AuthTokenResponse response = service.loginOrRegisterPhone(PHONE);

        ArgumentCaptor<SysUser> created = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(created.capture());
        assertEquals("ENABLED", created.getValue().getStatus());
        assertFalse(created.getValue().getPasswordInitialized());
        assertEquals(PHONE_HASH, created.getValue().getPhoneHash());
        assertEquals("138****1111", created.getValue().getPhoneMasked());
        assertNotNull(created.getValue().getPhoneBoundTime());
        assertFalse(response.getHasPassword());
        assertTrue(response.getPhoneBound());
    }

    @Test
    void losingTheCreationRaceFallsBackToTheRowTheWinnerWrote() {
        SysUser winner = enabledUser();
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH))
                .thenReturn(null)
                .thenReturn(winner);
        doThrow(new DuplicateKeyException("duplicate")).when(sysUserMapper).insert(any());

        assertEquals(USER_ID, service.loginOrRegisterPhone(PHONE).getUserId());
    }

    @Test
    void aDuplicateWithNoVisibleRowIsNotSwallowed() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate")).when(sysUserMapper).insert(any());

        assertThrows(DuplicateKeyException.class, () -> service.loginOrRegisterPhone(PHONE));
    }

    @Test
    void registeringAnAlreadyRegisteredPhoneIsRejectedBothByLookupAndByUniqueKey() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(enabledUser());

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.registerPhone(PHONE)
        ).getCode());
        verify(sysUserMapper, never()).insert(any());

        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate")).when(sysUserMapper).insert(any());

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.registerPhone(PHONE)
        ).getCode());
    }

    // ---------- 短信重置密码 ----------

    @Test
    void resettingThePasswordOfAnUnregisteredPhoneIsRejected() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(null);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.resetPasswordByPhone(PHONE, "new-secret")
        ).getCode());
        verify(sysUserMapper, never()).updatePasswordAndInitialize(anyLong(), anyString());
    }

    /**
     * 停用账号不能靠短信把密码改掉再登录进来——那等于给封禁开了一条绕行路。
     */
    @Test
    void resettingThePasswordOfASuspendedAccountIsRejected() {
        SysUser user = enabledUser();
        user.setStatus("DISABLED");
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(user);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.resetPasswordByPhone(PHONE, "new-secret")
        ).getCode());
        verify(sysUserMapper, never()).updatePasswordAndInitialize(anyLong(), anyString());
    }

    @Test
    void resettingThePasswordAlsoMarksItInitialized() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(enabledUser());
        when(sysUserMapper.updatePasswordAndInitialize(USER_ID, "encoded:new-secret")).thenReturn(1);

        service.resetPasswordByPhone(PHONE, "new-secret");

        verify(sysUserMapper).updatePasswordAndInitialize(USER_ID, "encoded:new-secret");
    }

    @Test
    void resetThatUpdatesNoRowReportsNotFound() {
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(enabledUser());
        when(sysUserMapper.updatePasswordAndInitialize(anyLong(), anyString())).thenReturn(0);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.resetPasswordByPhone(PHONE, "new-secret")
        ).getCode());
    }

    // ---------- 微信登录 ----------

    @Test
    void malformedOpenidIsRejected() {
        assertEquals(400, assertThrows(BizException.class, () -> service.wechatLogin(null)).getCode());
        assertEquals(400, assertThrows(BizException.class, () -> service.wechatLogin("   ")).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.wechatLogin("o".repeat(129))
        ).getCode());
        verify(sysUserMapper, never()).selectByOpenid(anyString());
    }

    @Test
    void legacyWechatAccountIsAdoptedInsteadOfDuplicated() {
        SysUser legacy = enabledUser();
        when(sysUserMapper.selectByOpenid("openid-1")).thenReturn(null);
        when(sysUserMapper.selectByUserName("wx_openid-1")).thenReturn(legacy);

        service.wechatLogin("  openid-1  ");

        verify(sysUserMapper).updateOpenid(USER_ID, "openid-1");
        verify(sysUserMapper, never()).insert(any());
    }

    @Test
    void newWechatAccountStartsWithoutAUsablePassword() {
        when(sysUserMapper.selectByOpenid("openid-1")).thenReturn(null);
        when(sysUserMapper.selectByUserName(anyString())).thenReturn(null);

        AuthTokenResponse response = service.wechatLogin("openid-1");

        ArgumentCaptor<SysUser> created = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(created.capture());
        assertEquals("openid-1", created.getValue().getOpenid());
        assertFalse(created.getValue().getPasswordInitialized());
        assertTrue(created.getValue().getUserName().startsWith("wx_"));
        assertFalse(response.getHasPassword());
    }

    @Test
    void suspendedWechatAccountCannotLogIn() {
        SysUser user = enabledUser();
        user.setStatus("DISABLED");
        when(sysUserMapper.selectByOpenid("openid-1")).thenReturn(user);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.wechatLogin("openid-1")
        ).getCode());
    }

    // ---------- 改名 ----------

    @Test
    void renamingToANameSomeoneElseHoldsIsRejected() {
        SysUser me = enabledUser();
        SysUser other = enabledUser();
        other.setUserId(99L);
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(sysUserMapper.selectByUserName("bob")).thenReturn(other);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.updateUserName(USER_ID, "bob")
        ).getCode());
        verify(sysUserMapper, never()).updateUserName(anyLong(), anyString());
    }

    @Test
    void renamingToTheNameYouAlreadyHoldIsANoop() {
        SysUser me = enabledUser();
        me.setUserName("alice");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(sysUserMapper.selectByUserName("alice")).thenReturn(me);

        UserProfileResponse profile = service.updateUserName(USER_ID, "alice");

        assertEquals("alice", profile.getUserName());
        verify(sysUserMapper, never()).updateUserName(anyLong(), anyString());
    }

    @Test
    void renamingWritesTheTrimmedNameAndReturnsTheFreshProfile() {
        SysUser me = enabledUser();
        me.setUserName("alice");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(sysUserMapper.selectByUserName("bob")).thenReturn(null);
        when(sysUserMapper.updateUserName(USER_ID, "bob")).thenReturn(1);

        service.updateUserName(USER_ID, "  bob  ");

        verify(sysUserMapper).updateUserName(USER_ID, "bob");
    }

    @Test
    void renamingARowThatVanishedReportsNotFound() {
        SysUser me = enabledUser();
        me.setUserName("alice");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(sysUserMapper.updateUserName(anyLong(), anyString())).thenReturn(0);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.updateUserName(USER_ID, "bob")
        ).getCode());
    }

    // ---------- 改密码 ----------

    @Test
    void changingAnExistingPasswordRequiresTheOldOne() {
        SysUser me = enabledUser();
        me.setPasswordInitialized(Boolean.TRUE);
        me.setPassword("encoded:old");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.updatePassword(USER_ID, null, "new")
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.updatePassword(USER_ID, "   ", "new")
        ).getCode());
        verify(sysUserMapper, never()).updatePasswordAndInitialize(anyLong(), anyString());
    }

    @Test
    void changingAPasswordWithTheWrongOldOneIsRejected() {
        SysUser me = enabledUser();
        me.setPasswordInitialized(Boolean.TRUE);
        me.setPassword("encoded:old");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(passwordEncoder.matches("guess", "encoded:old")).thenReturn(false);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.updatePassword(USER_ID, "guess", "new")
        ).getCode());
        verify(sysUserMapper, never()).updatePasswordAndInitialize(anyLong(), anyString());
    }

    /**
     * 从没设过密码的账号（手机号/微信注册）第一次设密码时没有「旧密码」可验，
     * 这条路径必须放行，否则这些用户永远设不上密码。
     */
    @Test
    void settingTheFirstPasswordDoesNotAskForAnOldOne() {
        SysUser me = enabledUser();
        me.setPasswordInitialized(Boolean.FALSE);
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(sysUserMapper.updatePasswordAndInitialize(USER_ID, "encoded:new")).thenReturn(1);

        service.updatePassword(USER_ID, null, "new");

        verify(sysUserMapper).updatePasswordAndInitialize(USER_ID, "encoded:new");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void suspendedAccountCannotChangeItsPassword() {
        SysUser me = enabledUser();
        me.setStatus("DISABLED");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.updatePassword(USER_ID, "old", "new")
        ).getCode());
    }

    // ---------- 换绑手机的身份校验 ----------

    @Test
    void anAccountWithNoPhoneBoundNeedsNoPriorProof() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(enabledUser());

        AuthService.PhoneChangeAuthorization auth = service.authorizePhoneChange(USER_ID, null, null);

        assertNull(auth.expectedPhoneHash());
        assertNull(auth.normalizedCurrentPhone());
        assertFalse(auth.currentPhoneCodeRequired());
    }

    @Test
    void changingABoundPhoneWithNeitherPasswordNorOldNumberIsRejected() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(phoneBoundUser());

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.authorizePhoneChange(USER_ID, "  ", "  ")
        ).getCode());
    }

    @Test
    void theCurrentPasswordUnlocksThePhoneChange() {
        SysUser me = phoneBoundUser();
        me.setPasswordInitialized(Boolean.TRUE);
        me.setPassword("encoded:secret");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(passwordEncoder.matches("secret", "encoded:secret")).thenReturn(true);

        AuthService.PhoneChangeAuthorization auth = service.authorizePhoneChange(USER_ID, "secret", null);

        assertEquals(PHONE_HASH, auth.expectedPhoneHash());
        assertFalse(auth.currentPhoneCodeRequired());
    }

    @Test
    void aWrongCurrentPasswordDoesNotUnlockThePhoneChange() {
        SysUser me = phoneBoundUser();
        me.setPasswordInitialized(Boolean.TRUE);
        me.setPassword("encoded:secret");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);
        when(passwordEncoder.matches("guess", "encoded:secret")).thenReturn(false);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.authorizePhoneChange(USER_ID, "guess", null)
        ).getCode());
    }

    @Test
    void accountWithoutAPasswordMustProveTheOldNumberInstead() {
        SysUser me = phoneBoundUser();
        me.setPasswordInitialized(Boolean.FALSE);
        when(sysUserMapper.selectById(USER_ID)).thenReturn(me);

        BizException error = assertThrows(
                BizException.class,
                () -> service.authorizePhoneChange(USER_ID, "anything", null)
        );

        assertEquals(400, error.getCode());
        assertEquals("当前账号尚未设置密码，请验证原手机号", error.getMessage());
    }

    /**
     * 报上来的「原手机号」必须真的哈希成当前账号绑的那个。放宽这一步，
     * 任何人只要随便填个自己控制的号码，就能把验证码发到自己手上完成换绑。
     */
    @Test
    void aDifferentOldNumberIsRejected() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(phoneBoundUser());
        when(phoneIdentityService.hash("13900002222")).thenReturn("hash:other");

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.authorizePhoneChange(USER_ID, null, "13900002222")
        ).getCode());
    }

    @Test
    void theMatchingOldNumberStillRequiresAnSmsCode() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(phoneBoundUser());

        AuthService.PhoneChangeAuthorization auth = service.authorizePhoneChange(USER_ID, null, "+86 138-0000-1111");

        assertEquals(PHONE_HASH, auth.expectedPhoneHash());
        assertEquals(PHONE, auth.normalizedCurrentPhone());
        assertTrue(auth.currentPhoneCodeRequired());
    }

    @Test
    void anInvalidOldNumberIsRejectedByFormat() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(phoneBoundUser());

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.authorizePhoneChange(USER_ID, null, "12345")
        ).getCode());
    }

    // ---------- 手机号占用与绑定 ----------

    @Test
    void aNumberBoundToSomeoneElseIsNotAvailable() {
        SysUser other = enabledUser();
        other.setUserId(99L);
        when(sysUserMapper.selectByPhoneHash(PHONE_HASH)).thenReturn(other);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.ensurePhoneAvailable(USER_ID, PHONE)
        ).getCode());
    }

    @Test
    void yourOwnNumberIsStillAvailableToYou() {
        when(sysUserMapper.selectByPhoneHash(PHONE_HASH)).thenReturn(enabledUser());

        service.ensurePhoneAvailable(USER_ID, PHONE);
    }

    /**
     * 绑定要在事务里复核「当前绑的还是我发起时看到的那个号」。少了这一步，
     * 两个并发的换绑请求会互相覆盖，后到的那个把先到的号顶掉。
     */
    @Test
    void bindingIsRefusedWhenTheBindingStateChangedUnderneath() {
        SysUser me = enabledUser();
        me.setPhoneHash("hash:changed-by-someone-else");
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(me);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.bindPhone(USER_ID, PHONE, "hash:what-i-saw")
        ).getCode());
        verify(sysUserMapper, never()).updatePhone(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void bindingTheNumberYouAlreadyHaveIsRejected() {
        SysUser me = enabledUser();
        me.setPhoneHash(PHONE_HASH);
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(me);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.bindPhone(USER_ID, PHONE, PHONE_HASH)
        ).getCode());
    }

    @Test
    void bindingANumberHeldByAnotherAccountIsRejected() {
        SysUser me = enabledUser();
        SysUser other = enabledUser();
        other.setUserId(99L);
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(me);
        when(sysUserMapper.selectByPhoneHashForUpdate(PHONE_HASH)).thenReturn(other);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.bindPhone(USER_ID, PHONE, null)
        ).getCode());
        verify(sysUserMapper, never()).updatePhone(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aUniqueKeyClashOnBindingReportsTheSameConflict() {
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(enabledUser());
        when(sysUserMapper.updatePhone(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.bindPhone(USER_ID, PHONE, null)
        ).getCode());
    }

    @Test
    void bindingASuspendedAccountIsRejected() {
        SysUser me = enabledUser();
        me.setStatus("DISABLED");
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(me);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.bindPhone(USER_ID, PHONE, null)
        ).getCode());
    }

    @Test
    void bindingAVanishedAccountIsRejected() {
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(null);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.bindPhone(USER_ID, PHONE, null)
        ).getCode());
    }

    @Test
    void bindingUpdatingNoRowReportsNotFound() {
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(enabledUser());
        when(sysUserMapper.updatePhone(anyLong(), anyString(), anyString(), anyString())).thenReturn(0);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.bindPhone(USER_ID, PHONE, null)
        ).getCode());
        verify(houseInvitationService, never()).acceptPending(anyString(), anyLong());
    }

    @Test
    void successfulBindingStoresTheMaskAndRedeemsInvitations() {
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(enabledUser());
        when(sysUserMapper.updatePhone(USER_ID, "+86", PHONE_HASH, "138****1111")).thenReturn(1);
        when(sysUserMapper.selectById(USER_ID)).thenReturn(phoneBoundUser());

        UserProfileResponse profile = service.bindPhone(USER_ID, PHONE, null);

        verify(sysUserMapper).updatePhone(USER_ID, "+86", PHONE_HASH, "138****1111");
        verify(houseInvitationService).acceptPending(PHONE_HASH, USER_ID);
        assertTrue(profile.getPhoneBound());
    }

    // ---------- 资料 ----------

    @Test
    void profileCarriesOnlyBusinessScopedPermissions() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(enabledUser());

        UserProfileResponse profile = service.getProfile(USER_ID);

        assertTrue(profile.getPermissions().contains("account:profile:query"));
        assertFalse(profile.getPermissions().stream().anyMatch(code -> code.startsWith("platform:")));
        assertFalse(profile.getPermissions().contains("rabbit:houses:remove"));
    }

    // ---------- fixtures ----------

    private SysUser enabledUser() {
        SysUser user = new SysUser();
        user.setUserId(USER_ID);
        user.setStatus("ENABLED");
        return user;
    }

    private SysUser phoneBoundUser() {
        SysUser user = enabledUser();
        user.setPhoneHash(PHONE_HASH);
        user.setPhoneMasked("138****1111");
        user.setPhoneBoundTime(new Date());
        return user;
    }
}

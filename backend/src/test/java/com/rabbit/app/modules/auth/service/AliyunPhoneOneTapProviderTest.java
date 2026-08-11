package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.GetMobileResponse;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody.GetMobileResponseBodyGetMobileResultDTO;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AliyunPhoneOneTapProviderTest {
    @Test
    void disabledProviderCanBeCreatedBySpringWithTheProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(AliyunPhoneOneTapProvider.class)
                .run(context -> {
                    Assertions.assertTrue(context.isRunning());
                    Assertions.assertNotNull(context.getBean(AliyunPhoneOneTapProvider.class));
                });
    }

    @Test
    void resolvesPhoneThroughGetMobile() throws Exception {
        Client client = mock(Client.class);
        GetMobileResponseBody body = new GetMobileResponseBody()
                .setCode("OK")
                .setGetMobileResultDTO(
                        new GetMobileResponseBodyGetMobileResultDTO().setMobile("13800138000")
                );
        when(client.getMobileWithOptions(any(), any())).thenReturn(new GetMobileResponse().setBody(body));
        AliyunPhoneOneTapProvider provider = provider(true, "key-id", "key-secret", client);

        assertEquals("13800138000", provider.resolvePhone("opaque-token", "request-1"));

        ArgumentCaptor<RuntimeOptions> runtimeCaptor = ArgumentCaptor.forClass(RuntimeOptions.class);
        verify(client).getMobileWithOptions(any(), runtimeCaptor.capture());
        RuntimeOptions runtime = runtimeCaptor.getValue();
        assertEquals(Boolean.FALSE, runtime.getAutoretry());
        assertEquals(1, runtime.getMaxAttempts().intValue());
        assertEquals(2000, runtime.getConnectTimeout().intValue());
        assertEquals(3000, runtime.getReadTimeout().intValue());
    }

    @Test
    void disabledConfigurationCanStartButEnabledIncompleteConfigurationFailsAtStartup() {
        Client client = mock(Client.class);

        AliyunPhoneOneTapProvider disabled = provider(false, "", "", client);
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> provider(true, "", "", client)
        );
        IllegalArgumentException blankEndpoint = assertThrows(
                IllegalArgumentException.class,
                () -> new AliyunPhoneOneTapProvider(
                        true,
                        " ",
                        "key-id",
                        "key-secret",
                        2000,
                        3000,
                        () -> client
                )
        );

        assertEquals("aliyun", disabled.providerId());
        assertEquals("一键登录阿里云配置不完整", missing.getMessage());
        assertEquals("一键登录阿里云配置不完整", blankEndpoint.getMessage());
    }

    @Test
    void enabledConfigurationRejectsUnsafeTimeouts() {
        Client client = mock(Client.class);

        IllegalArgumentException zeroConnectTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> new AliyunPhoneOneTapProvider(
                        true,
                        "dypnsapi.aliyuncs.com",
                        "key-id",
                        "key-secret",
                        0,
                        3000,
                        () -> client
                )
        );
        IllegalArgumentException excessiveReadTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> new AliyunPhoneOneTapProvider(
                        true,
                        "dypnsapi.aliyuncs.com",
                        "key-id",
                        "key-secret",
                        2000,
                        30_001,
                        () -> client
                )
        );

        assertEquals("一键登录阿里云超时配置不正确", zeroConnectTimeout.getMessage());
        assertEquals("一键登录阿里云超时配置不正确", excessiveReadTimeout.getMessage());
    }

    @Test
    void sdkExceptionsAreReducedToASanitizedReason() throws Exception {
        Client client = mock(Client.class);
        when(client.getMobileWithOptions(any(), any())).thenThrow(
                new IllegalStateException("opaque-token=secret, mobile=13800138000")
        );
        AliyunPhoneOneTapProvider provider = provider(true, "key-id", "key-secret", client);

        PhoneOneTapProviderException error = assertThrows(
                PhoneOneTapProviderException.class,
                () -> provider.resolvePhone("opaque-token", "request-1")
        );

        assertEquals(PhoneOneTapProviderException.Reason.UNAVAILABLE, error.getReason());
        assertEquals("UNAVAILABLE", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "isv.TOKEN_INVALID",
            "isv.TOKEN_UNAUTHORIZED_USED",
            "isv.CSRF_CHECK_FAILED",
            "isv.ACCESS_CODE_ILLEGAL"
    })
    void explicitTokenErrorsAreRejected(String code) throws Exception {
        assertProviderCodeReason(code, PhoneOneTapProviderException.Reason.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "isv.RAM_PERMISSION_DENY",
            "isv.VERIFY_SCHEME_NOT_EXIST",
            "isv.INVALID_PARAMETERS",
            "InvalidParameter.MissingPackageName"
    })
    void integrationAndPermissionErrorsAreMisconfigured(String code) throws Exception {
        assertProviderCodeReason(code, PhoneOneTapProviderException.Reason.MISCONFIGURED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "isp.QPS_LIMIT",
            "isp.SYSTEM_ERROR",
            "isp.UNKNOWN",
            "unrecognized.provider.code"
    })
    void temporaryAndUnknownErrorsAreUnavailable(String code) throws Exception {
        assertProviderCodeReason(code, PhoneOneTapProviderException.Reason.UNAVAILABLE);
    }

    @Test
    void sdkCredentialErrorsAreMisconfiguredWithoutExposingTheirMessage() throws Exception {
        Client client = mock(Client.class);
        TeaException sdkError = new TeaException();
        sdkError.setCode("InvalidAccessKeyId.NotFound");
        sdkError.setMessage("opaque-token=secret, mobile=13800138000");
        when(client.getMobileWithOptions(any(), any())).thenThrow(sdkError);
        AliyunPhoneOneTapProvider provider = provider(true, "key-id", "key-secret", client);

        PhoneOneTapProviderException error = assertThrows(
                PhoneOneTapProviderException.class,
                () -> provider.resolvePhone("opaque-token", "request-1")
        );

        assertEquals(PhoneOneTapProviderException.Reason.MISCONFIGURED, error.getReason());
        assertEquals("MISCONFIGURED", error.getMessage());
    }

    private void assertProviderCodeReason(
            String code,
            PhoneOneTapProviderException.Reason expectedReason
    ) throws Exception {
        Client client = mock(Client.class);
        GetMobileResponseBody body = new GetMobileResponseBody()
                .setCode(code)
                .setMessage("opaque-token=secret, mobile=13800138000")
                .setRequestId("provider-request-1");
        when(client.getMobileWithOptions(any(), any()))
                .thenReturn(new GetMobileResponse().setBody(body));
        AliyunPhoneOneTapProvider provider = provider(true, "key-id", "key-secret", client);

        PhoneOneTapProviderException error = assertThrows(
                PhoneOneTapProviderException.class,
                () -> provider.resolvePhone("opaque-token", "request-1")
        );

        assertEquals(expectedReason, error.getReason());
        assertEquals(expectedReason.name(), error.getMessage());
    }

    private AliyunPhoneOneTapProvider provider(
            boolean enabled,
            String accessKeyId,
            String accessKeySecret,
            Client client
    ) {
        return new AliyunPhoneOneTapProvider(
                enabled,
                "dypnsapi.aliyuncs.com",
                accessKeyId,
                accessKeySecret,
                2000,
                3000,
                () -> client
        );
    }
}

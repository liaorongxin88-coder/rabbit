package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;

public class PhoneOneTapLeaseLostException extends BizException {
    public PhoneOneTapLeaseLostException() {
        super(409, "一键登录请求已被后续重试接管");
    }
}

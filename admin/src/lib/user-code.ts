/**
 * 兔号：用户在「账号安全」里能看到、可以报给别人的唯一标识。
 *
 * 形如 `R3F9A0C21B7`——R 前缀加 10 位十六进制。十六进制字母表里没有 O/I/L，
 * 所以口头传达时的「零还是欧」「一还是艾」可以在归一化阶段直接消掉。
 *
 * 这份规则和后端 `UserCodes`、App 端 `UserCode` 是同一套，三边必须一致。
 */
const USER_CODE_PATTERN = /^R[0-9A-F]{10}$/
const SEPARATORS = /[\s\-_]/g
const MAINLAND_MOBILE = /^1[3-9]\d{9}$/

/** 去掉空格连字符、转大写，并把十六进制里不存在的 O/I/L 当成 0/1/1。 */
export function normalizeUserCode(raw: string): string {
  return raw
    .trim()
    .toUpperCase()
    .replace(SEPARATORS, '')
    .replaceAll('O', '0')
    .replaceAll('I', '1')
    .replaceAll('L', '1')
}

/** 归一化之后是不是一个兔号。手机号是纯数字，不会命中。 */
export function looksLikeUserCode(raw: string): boolean {
  return USER_CODE_PATTERN.test(normalizeUserCode(raw))
}

export function normalizeMainlandPhone(raw: string): string {
  return raw.replace(SEPARATORS, '').replace(/^\+?86/, '')
}

export function looksLikeMainlandPhone(raw: string): boolean {
  return MAINLAND_MOBILE.test(normalizeMainlandPhone(raw))
}

/**
 * 邀请输入框收下的两种形态。返回归一化后的值，让请求体和幂等指纹都稳定：
 * 同一个人用大小写不同的写法重试，不该变成两条邀请。
 */
export function normalizeInviteIdentifier(raw: string): {
  identifier: string
  kind: 'phone' | 'code' | 'invalid'
} {
  if (looksLikeMainlandPhone(raw)) {
    return { identifier: normalizeMainlandPhone(raw), kind: 'phone' }
  }
  if (looksLikeUserCode(raw)) {
    return { identifier: normalizeUserCode(raw), kind: 'code' }
  }
  return { identifier: raw.trim(), kind: 'invalid' }
}

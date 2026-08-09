export type RequestSessionScope = 'admin' | 'workspace'

export function shouldClearRequestSession(
  scope: RequestSessionScope,
  code: number,
  message: string,
) {
  return code === 401 || (scope === 'workspace' && code === 403 && message === '账号已停用')
}

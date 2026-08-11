import assert from 'node:assert/strict'
import test from 'node:test'
import { shouldClearRequestSession } from '../src/lib/request-auth.ts'

test('clears both session scopes for an unauthorized response', () => {
  assert.equal(shouldClearRequestSession('admin', 401, '登录已过期'), true)
  assert.equal(shouldClearRequestSession('workspace', 401, '登录已过期'), true)
})

test('clears only the workspace session for the exact disabled-account response', () => {
  assert.equal(shouldClearRequestSession('workspace', 403, '账号已停用'), true)
  assert.equal(shouldClearRequestSession('admin', 403, '账号已停用'), false)
})

test('keeps the workspace session for ordinary permission failures', () => {
  assert.equal(shouldClearRequestSession('workspace', 403, '权限不足'), false)
  assert.equal(shouldClearRequestSession('workspace', 403, '无兔场权限'), false)
  assert.equal(shouldClearRequestSession('workspace', 403, '兔场已停用'), false)
  assert.equal(shouldClearRequestSession('workspace', 403, '账号已停用 '), false)
})

import { createAlova } from "alova";
import adapterFetch from "alova/fetch";
import { toast } from "sonner";
import {
  clearSession,
  getToken,
  clearWorkspaceSession,
  getWorkspaceToken,
} from "@/lib/auth";
import {
  shouldClearRequestSession,
  type RequestSessionScope,
} from "@/lib/request-auth";
import type { ApiResponse } from "@/types/api";
import {
  parseContentDispositionFilename,
  XLSX_MEDIA_TYPE,
} from "@/lib/download";

type JsonBody = object | string;

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, "") ?? "";

/**
 * 携带业务码的错误，让调用方能区分「功能未启用」（501）和「服务不可用」（503）。
 * 仍然继承 Error，现有的 `error instanceof Error ? error.message` 写法不受影响。
 */
export class ApiError extends Error {
  readonly code: number;

  constructor(message: string, code: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

function getRequestErrorMessage(error: unknown) {
  if (error instanceof TypeError && error.message === "Failed to fetch") {
    return "无法连接 API 服务，请检查网络或跨域配置";
  }
  return error instanceof Error ? error.message : "网络请求失败";
}

async function parseApiResponse(response: Response) {
  const text = await response.text();
  if (!text.trim()) {
    throw new Error(`服务返回空响应：HTTP ${response.status}`);
  }

  try {
    return JSON.parse(text) as ApiResponse<unknown>;
  } catch {
    const proxyHint =
      response.status === 404
        ? "，请检查 VITE_API_BASE_URL 或服务器 /api 反向代理配置"
        : "";
    throw new Error(
      `服务返回非 JSON 响应：HTTP ${response.status}${proxyHint}`,
    );
  }
}

function createRequestClient(
  token: () => string,
  clearAuth: () => void,
  scope: RequestSessionScope,
) {
  return createAlova({
    baseURL: API_BASE_URL,
    requestAdapter: adapterFetch(),
    beforeRequest(method) {
      const accessToken = token();
      if (accessToken) {
        method.config.headers = {
          ...method.config.headers,
          Authorization: `Bearer ${accessToken}`,
        };
      }
    },
    responded: {
      async onSuccess(response) {
        const payload = await parseApiResponse(response);
        if (!payload || typeof payload.code !== "number") {
          throw new Error("响应格式不合法");
        }
        if (payload.code !== 0) {
          const message = payload.message || "请求失败";
          if (shouldClearRequestSession(scope, payload.code, message)) {
            const hadSession = Boolean(token());
            clearAuth();
            if (hadSession) {
              toast.error(message);
            }
            throw new ApiError(message, payload.code);
          }
          // 501 表示该功能在本部署未启用，由调用方决定如何降级，不该弹成错误。
          if (payload.code !== 501) {
            toast.error(message);
          }
          throw new ApiError(message, payload.code);
        }
        return payload.data;
      },
      onError(error) {
        const message = getRequestErrorMessage(error);
        toast.error(message);
        throw new Error(message);
      },
    },
  });
}

const alova = createRequestClient(getToken, clearSession, "admin");
const workspaceAlova = createRequestClient(
  getWorkspaceToken,
  clearWorkspaceSession,
  "workspace",
);

export function getJson<T>(url: string, params?: Record<string, unknown>) {
  return alova.Get<T>(url, {
    params,
    cacheFor: 0,
  });
}

export function postJson<T>(url: string, data?: JsonBody) {
  return alova.Post<T>(url, data);
}

export function putJson<T>(url: string, data?: JsonBody) {
  return alova.Put<T>(url, data);
}

export function deleteJson<T>(url: string) {
  return alova.Delete<T>(url);
}

interface WorkspaceRequestOptions {
  houseId?: number | null;
  params?: Record<string, unknown>;
}

function workspaceConfig(options?: WorkspaceRequestOptions) {
  return {
    params: options?.params,
    headers: options?.houseId
      ? { "X-House-Id": String(options.houseId) }
      : undefined,
  };
}

export function workspaceGetJson<T>(
  url: string,
  options?: WorkspaceRequestOptions,
) {
  return workspaceAlova.Get<T>(url, {
    ...workspaceConfig(options),
    cacheFor: 0,
  });
}

export function workspacePostJson<T>(
  url: string,
  data?: JsonBody,
  options?: WorkspaceRequestOptions,
) {
  return workspaceAlova.Post<T>(url, data, workspaceConfig(options));
}

export function workspacePutJson<T>(
  url: string,
  data?: JsonBody,
  options?: WorkspaceRequestOptions,
) {
  return workspaceAlova.Put<T>(url, data, workspaceConfig(options));
}

export function workspaceDeleteJson<T>(
  url: string,
  options?: WorkspaceRequestOptions,
) {
  return workspaceAlova.Delete<T>(url, undefined, workspaceConfig(options));
}

export interface WorkspaceDownloadResult {
  blob: Blob;
  filename: string;
}

/** Authenticated raw download for house-scoped business reports. */
export async function workspaceDownloadBlob(
  url: string,
  houseId: number,
  fallbackFilename: string,
): Promise<WorkspaceDownloadResult> {
  const token = getWorkspaceToken();
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${url}`, {
      headers: {
        Accept: XLSX_MEDIA_TYPE,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        "X-House-Id": String(houseId),
      },
    });
  } catch (error) {
    throw new Error(getRequestErrorMessage(error));
  }

  const contentType = (response.headers.get("Content-Type") ?? "")
    .split(";", 1)[0]
    .trim()
    .toLowerCase();
  if (!response.ok || contentType.includes("json")) {
    const text = await response.text();
    let payload: ApiResponse<unknown> | null = null;
    try {
      payload = JSON.parse(text) as ApiResponse<unknown>;
    } catch {
      // Raw download failures may be returned by a proxy without an API envelope.
    }
    const code =
      typeof payload?.code === "number" ? payload.code : response.status;
    const message =
      payload?.code === 0
        ? "报表接口返回了 JSON，未生成 Excel 文件"
        : payload?.message || `报表下载失败：HTTP ${response.status}`;
    if (shouldClearRequestSession("workspace", code, message)) {
      clearWorkspaceSession();
    }
    throw new ApiError(message, code);
  }

  if (contentType !== XLSX_MEDIA_TYPE) {
    throw new Error(
      `报表响应格式不正确：${contentType || "未提供 Content-Type"}`,
    );
  }

  const blob = await response.blob();
  if (blob.size === 0) {
    throw new Error("报表文件为空，请稍后重试");
  }

  return {
    blob,
    filename: parseContentDispositionFilename(
      response.headers.get("Content-Disposition"),
      fallbackFilename,
    ),
  };
}

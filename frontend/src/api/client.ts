export class ApiError extends Error { constructor(readonly code: string, message: string) { super(message); } }
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isForm = init.body instanceof FormData;
  const response = await fetch(path, { credentials: "include", ...init, headers: { ...(isForm ? {} : { "Content-Type": "application/json" }), ...(init.headers ?? {}) } });
  if (!response.ok) { const body = await response.json().catch(() => ({})); throw new ApiError(body.code ?? "REQUEST_FAILED", body.message ?? "Something went wrong."); }
  if (response.status === 204) return undefined as T;
  const contentType = response.headers.get("content-type") ?? "";
  return (contentType.includes("application/json") ? await response.json() : await response.text()) as T;
}

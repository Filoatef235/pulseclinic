export type HealthResponse = {
  status: string;
};

export async function fetchHealth(): Promise<HealthResponse> {
  const base = import.meta.env.VITE_API_BASE_URL ?? "";
  const response = await fetch(`${base}/api/v1/health`);
  if (!response.ok) {
    throw new Error("API unavailable");
  }
  return (await response.json()) as HealthResponse;
}

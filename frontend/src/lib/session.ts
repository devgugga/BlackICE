export interface SessionResponse {
  subject: string;
  username: string;
  roles: string[];
}

export async function fetchSession(): Promise<SessionResponse | null> {
  const res = await fetch('/api/me', { credentials: 'include' });
  if (res.status === 401) return null;
  if (!res.ok) throw new Error(`/api/me falhou: ${res.status}`);
  return (await res.json()) as SessionResponse;
}

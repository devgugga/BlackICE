import type { SessionResponse } from '@/features/session/session.types';

export async function fetchSession(): Promise<SessionResponse | null> {
  const res = await fetch('/api/me', { credentials: 'include' });
  if (res.status === 401) return null;
  if (!res.ok) throw new Error(`/api/me falhou: ${res.status}`);
  return (await res.json()) as SessionResponse;
}

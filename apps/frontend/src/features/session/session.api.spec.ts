import { describe, it, expect, vi, afterEach } from 'vitest';
import { fetchSession } from '@/features/session/session.api';

afterEach(() => vi.restoreAllMocks());

describe('fetchSession', () => {
  it('retorna null em 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401, ok: false }));
    expect(await fetchSession()).toBeNull();
  });

  it('retorna a sessão em 200', async () => {
    const body = { subject: 's', username: 'dr.teste', roles: ['user'] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      status: 200, ok: true, json: () => Promise.resolve(body),
    }));
    expect(await fetchSession()).toEqual(body);
  });
});

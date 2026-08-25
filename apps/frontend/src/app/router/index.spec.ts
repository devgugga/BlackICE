import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import router from '@/app/router';
import { fetchSession } from '@/features/session/session.api';
import type { SessionResponse } from '@/features/session/session.types';

vi.mock('@/features/session/session.api', () => ({
  fetchSession: vi.fn(),
}));
const fetchSessionMock = vi.mocked(fetchSession);

describe('Router', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...originalLocation, href: '' },
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: originalLocation,
    });
  });

  it('registra as rotas principais protegidas incluindo a rota do viewer', () => {
    const routes = router.getRoutes();

    const homeRoute = routes.find((r) => r.name === 'home');
    expect(homeRoute).toBeDefined();
    expect(homeRoute?.path).toBe('/');
    expect(homeRoute?.meta.protected).toBe(true);

    const ingestRoute = routes.find((r) => r.name === 'ingest');
    expect(ingestRoute).toBeDefined();
    expect(ingestRoute?.path).toBe('/ingest');
    expect(ingestRoute?.meta.protected).toBe(true);

    const worklistRoute = routes.find((r) => r.name === 'worklist');
    expect(worklistRoute).toBeDefined();
    expect(worklistRoute?.path).toBe('/studies');
    expect(worklistRoute?.meta.protected).toBe(true);

    const viewerRoute = routes.find((r) => r.name === 'viewer');
    expect(viewerRoute).toBeDefined();
    expect(viewerRoute?.path).toBe('/studies/:studyUid');
    expect(viewerRoute?.meta.protected).toBe(true);
  });

  it('permite navegação em rota protegida quando o usuário estiver autenticado', async () => {
    const session: SessionResponse = {
      subject: 'user-1',
      username: 'user@example.com',
      roles: ['PHYSICIAN'],
    };
    fetchSessionMock.mockResolvedValue(session);

    await router.push('/studies/1.2.840.113619.2.55.3.123');
    expect(router.currentRoute.value.name).toBe('viewer');
    expect(router.currentRoute.value.params.studyUid).toBe('1.2.840.113619.2.55.3.123');
    expect(window.location.href).toBe('');
  });

  it('redireciona para /api/login quando a sessão for nula', async () => {
    fetchSessionMock.mockResolvedValue(null);

    await router.push('/studies/1.2.840.113619.2.55.3.456');
    expect(window.location.href).toBe('/api/login');
  });

  it('redireciona para /api/login quando fetchSession lançar erro', async () => {
    fetchSessionMock.mockRejectedValue(new Error('Network error'));

    await router.push('/studies/1.2.840.113619.2.55.3.789');
    expect(window.location.href).toBe('/api/login');
  });
});

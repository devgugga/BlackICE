import {
  apiErrorFromResponse,
  clientError,
} from '@/shared/api/problems/parse-problem';

export function readCookie(cookies: string, name: string): string | null {
  const match = cookies.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Obtém o token CSRF que as requisições mutantes precisam ecoar.
 *
 * <p>Um `204` sem cookie é falha local, não do servidor: ele cumpriu o
 * contrato, mas o browser não guardou o cookie — daí `CLIENT_CSRF_COOKIE_MISSING`.
 */
export async function fetchCsrfToken(
  readCookies: () => string = () => document.cookie,
): Promise<string> {
  let response: Response;
  try {
    response = await fetch('/api/csrf', { credentials: 'include' });
  } catch {
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (!response.ok) throw await apiErrorFromResponse(response);

  const token = readCookie(readCookies(), 'csrf-token');
  if (!token) {
    throw clientError('CLIENT_CSRF_COOKIE_MISSING', response.headers.get('X-Trace-ID') ?? undefined);
  }
  return token;
}

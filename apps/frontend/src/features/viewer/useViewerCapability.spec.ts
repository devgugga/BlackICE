import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useViewerCapability, VIEWER_MEDIA_QUERY } from './useViewerCapability';

describe('useViewerCapability', () => {
  let listeners: Set<(e: MediaQueryListEvent) => void>;
  let userAgentSpy: ReturnType<typeof vi.spyOn>;

  function createMediaQueryList(matches: boolean): MediaQueryList {
    return {
      matches,
      media: VIEWER_MEDIA_QUERY,
      onchange: null,
      addListener: vi.fn((cb: (e: MediaQueryListEvent) => void) => listeners.add(cb)),
      removeListener: vi.fn((cb: (e: MediaQueryListEvent) => void) => listeners.delete(cb)),
      addEventListener: vi.fn((type: string, cb: (e: MediaQueryListEvent) => void) => {
        if (type === 'change') listeners.add(cb);
      }),
      removeEventListener: vi.fn((type: string, cb: (e: MediaQueryListEvent) => void) => {
        if (type === 'change') listeners.delete(cb);
      }),
      dispatchEvent: vi.fn(),
    } as unknown as MediaQueryList;
  }

  function simulateViewport(width: number): MediaQueryList {
    // Media query: '(min-width: 1024px)'
    const matches = width >= 1024;
    return createMediaQueryList(matches);
  }

  beforeEach(() => {
    listeners = new Set();
    userAgentSpy = vi.spyOn(navigator, 'userAgent', 'get');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function setMatchMedia(mql: MediaQueryList | undefined) {
    if (mql === undefined) {
      // @ts-expect-error simulating environment without matchMedia
      window.matchMedia = undefined;
    } else {
      window.matchMedia = vi.fn().mockReturnValue(mql) as unknown as typeof window.matchMedia;
    }
  }

  it('queries the exact media query specification', () => {
    const matchMediaMock = vi.fn().mockReturnValue(createMediaQueryList(true));
    window.matchMedia = matchMediaMock as unknown as typeof window.matchMedia;

    const { dispose } = useViewerCapability();
    expect(matchMediaMock).toHaveBeenCalledWith('(min-width: 1024px)');
    expect(VIEWER_MEDIA_QUERY).toBe('(min-width: 1024px)');
    dispose();
  });

  it('permits rendering on any viewport width >= 1024px', () => {
    setMatchMedia(simulateViewport(1024));
    const exact1024 = useViewerCapability();
    expect(exact1024.canRenderViewer.value).toBe(true);
    exact1024.dispose();

    setMatchMedia(simulateViewport(1440));
    const split1440 = useViewerCapability();
    expect(split1440.canRenderViewer.value).toBe(true);
    split1440.dispose();

    setMatchMedia(simulateViewport(1920));
    const desktop = useViewerCapability();
    expect(desktop.canRenderViewer.value).toBe(true);
    desktop.dispose();
  });

  it('rejects rendering on viewport widths below 1024px (1023px and 768px landscape blocked)', () => {
    setMatchMedia(simulateViewport(1023));
    const below1024 = useViewerCapability();
    expect(below1024.canRenderViewer.value).toBe(false);
    below1024.dispose();

    setMatchMedia(simulateViewport(768));
    const landscape768 = useViewerCapability();
    expect(landscape768.canRenderViewer.value).toBe(false);
    landscape768.dispose();

    setMatchMedia(simulateViewport(375));
    const mobilePortrait = useViewerCapability();
    expect(mobilePortrait.canRenderViewer.value).toBe(false);
    mobilePortrait.dispose();
  });

  it('dynamically reacts to media query change events', () => {
    const mql = simulateViewport(768); // initially false
    setMatchMedia(mql);

    const { canRenderViewer, dispose } = useViewerCapability();
    expect(canRenderViewer.value).toBe(false);

    // Rotate to landscape (768px landscape -> matches)
    listeners.forEach((listener) => {
      listener({ matches: true, media: VIEWER_MEDIA_QUERY } as MediaQueryListEvent);
    });

    expect(canRenderViewer.value).toBe(true);
    dispose();
  });

  it('cleans up event listeners on dispose', () => {
    const mql = simulateViewport(1024);
    setMatchMedia(mql);

    const { dispose } = useViewerCapability();
    expect(listeners.size).toBe(1);

    dispose();
    expect(listeners.size).toBe(0);
    expect(mql.removeEventListener).toHaveBeenCalled();
  });

  it('never inspects navigator.userAgent', () => {
    setMatchMedia(simulateViewport(1280));
    const { dispose } = useViewerCapability();

    expect(userAgentSpy).not.toHaveBeenCalled();
    dispose();
  });

  it('falls back safely to false when matchMedia is unavailable', () => {
    setMatchMedia(undefined);

    const { canRenderViewer, dispose } = useViewerCapability();
    expect(canRenderViewer.value).toBe(false);
    expect(() => dispose()).not.toThrow();
  });
});

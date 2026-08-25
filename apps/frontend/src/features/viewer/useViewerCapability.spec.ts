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

  function simulateViewport(width: number, orientation: 'landscape' | 'portrait'): MediaQueryList {
    // Media query: '(min-width: 1024px), (min-width: 768px) and (orientation: landscape)'
    const matches = width >= 1024 || (width >= 768 && orientation === 'landscape');
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
    expect(matchMediaMock).toHaveBeenCalledWith(
      '(min-width: 1024px), (min-width: 768px) and (orientation: landscape)',
    );
    expect(VIEWER_MEDIA_QUERY).toBe(
      '(min-width: 1024px), (min-width: 768px) and (orientation: landscape)',
    );
    dispose();
  });

  it('permits rendering on 768px landscape', () => {
    setMatchMedia(simulateViewport(768, 'landscape'));
    const { canRenderViewer, dispose } = useViewerCapability();

    expect(canRenderViewer.value).toBe(true);
    dispose();
  });

  it('permits rendering on any viewport width >= 1024px regardless of orientation', () => {
    setMatchMedia(simulateViewport(1024, 'portrait'));
    const portrait1024 = useViewerCapability();
    expect(portrait1024.canRenderViewer.value).toBe(true);
    portrait1024.dispose();

    setMatchMedia(simulateViewport(1024, 'landscape'));
    const landscape1024 = useViewerCapability();
    expect(landscape1024.canRenderViewer.value).toBe(true);
    landscape1024.dispose();

    setMatchMedia(simulateViewport(1920, 'landscape'));
    const desktop = useViewerCapability();
    expect(desktop.canRenderViewer.value).toBe(true);
    desktop.dispose();
  });

  it('rejects rendering on 767px landscape', () => {
    setMatchMedia(simulateViewport(767, 'landscape'));
    const { canRenderViewer, dispose } = useViewerCapability();

    expect(canRenderViewer.value).toBe(false);
    dispose();
  });

  it('rejects rendering on portrait widths below 1024px', () => {
    setMatchMedia(simulateViewport(768, 'portrait'));
    const tabletPortrait = useViewerCapability();
    expect(tabletPortrait.canRenderViewer.value).toBe(false);
    tabletPortrait.dispose();

    setMatchMedia(simulateViewport(375, 'portrait'));
    const mobilePortrait = useViewerCapability();
    expect(mobilePortrait.canRenderViewer.value).toBe(false);
    mobilePortrait.dispose();
  });

  it('dynamically reacts to media query change events', () => {
    const mql = simulateViewport(768, 'portrait'); // initially false
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
    const mql = simulateViewport(1024, 'landscape');
    setMatchMedia(mql);

    const { dispose } = useViewerCapability();
    expect(listeners.size).toBe(1);

    dispose();
    expect(listeners.size).toBe(0);
    expect(mql.removeEventListener).toHaveBeenCalled();
  });

  it('never inspects navigator.userAgent', () => {
    setMatchMedia(simulateViewport(1280, 'landscape'));
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

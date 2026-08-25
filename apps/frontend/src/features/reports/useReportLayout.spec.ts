import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  useReportLayout,
  SPLIT_MEDIA_QUERY,
  DRAWER_MEDIA_QUERY,
  MIN_REPORT_WIDTH,
  MIN_VIEWPORT_WIDTH,
} from './useReportLayout';

describe('useReportLayout', () => {
  let listeners: Map<string, Set<(e: MediaQueryListEvent) => void>>;
  let userAgentSpy: ReturnType<typeof vi.spyOn>;

  function createMediaQueryList(query: string, matches: boolean): MediaQueryList {
    if (!listeners.has(query)) {
      listeners.set(query, new Set());
    }
    const queryListeners = listeners.get(query)!;

    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn((cb: (e: MediaQueryListEvent) => void) => queryListeners.add(cb)),
      removeListener: vi.fn((cb: (e: MediaQueryListEvent) => void) => queryListeners.delete(cb)),
      addEventListener: vi.fn((type: string, cb: (e: MediaQueryListEvent) => void) => {
        if (type === 'change') queryListeners.add(cb);
      }),
      removeEventListener: vi.fn((type: string, cb: (e: MediaQueryListEvent) => void) => {
        if (type === 'change') queryListeners.delete(cb);
      }),
      dispatchEvent: vi.fn(),
    } as unknown as MediaQueryList;
  }

  function setupViewportWidth(width: number) {
    const isSplit = width >= 1440;
    const isDrawer = width >= 1024 && width < 1440;

    window.matchMedia = vi.fn((query: string) => {
      if (query === SPLIT_MEDIA_QUERY) {
        return createMediaQueryList(query, isSplit);
      }
      if (query === DRAWER_MEDIA_QUERY || query === '(min-width: 1024px)') {
        return createMediaQueryList(query, isDrawer);
      }
      return createMediaQueryList(query, false);
    }) as unknown as typeof window.matchMedia;
  }

  beforeEach(() => {
    listeners = new Map();
    userAgentSpy = vi.spyOn(navigator, 'userAgent', 'get');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('determines SPLIT mode at 1920px with panel open by default', () => {
    setupViewportWidth(1920);
    const layout = useReportLayout();

    expect(layout.mode.value).toBe('SPLIT');
    expect(layout.isOpen.value).toBe(true);
    layout.dispose();
  });

  it('determines DRAWER mode at 1366px and 1024px with panel closed by default', () => {
    setupViewportWidth(1366);
    const layout1366 = useReportLayout();
    expect(layout1366.mode.value).toBe('DRAWER');
    expect(layout1366.isOpen.value).toBe(false);
    layout1366.dispose();

    setupViewportWidth(1024);
    const layout1024 = useReportLayout();
    expect(layout1024.mode.value).toBe('DRAWER');
    expect(layout1024.isOpen.value).toBe(false);
    layout1024.dispose();
  });

  it('determines REPORT_ONLY mode below 1024px (e.g. 390px) with full-width panel open by default', () => {
    setupViewportWidth(390);
    const layout = useReportLayout();

    expect(layout.mode.value).toBe('REPORT_ONLY');
    expect(layout.isOpen.value).toBe(true);
    layout.dispose();
  });

  it('supports manual open, close and toggle methods', () => {
    setupViewportWidth(1366);
    const layout = useReportLayout();
    expect(layout.isOpen.value).toBe(false);

    layout.open();
    expect(layout.isOpen.value).toBe(true);

    layout.close();
    expect(layout.isOpen.value).toBe(false);

    layout.toggle();
    expect(layout.isOpen.value).toBe(true);

    layout.dispose();
  });

  it('clamps resizing in SPLIT mode between minimum width and measured workspace leaving >= 720px for viewport', () => {
    setupViewportWidth(1920);
    const layout = useReportLayout();

    // Workspace width 1920 -> max report width = 1920 - 720 = 1200
    layout.setPanelWidth(500, 1920);
    expect(layout.panelWidth.value).toBe(500);

    // Below minimum width (e.g. 200px -> clamped to MIN_REPORT_WIDTH)
    layout.setPanelWidth(200, 1920);
    expect(layout.panelWidth.value).toBe(MIN_REPORT_WIDTH);

    // Above maximum width (e.g. 1500px -> clamped to 1200)
    layout.setPanelWidth(1500, 1920);
    expect(layout.panelWidth.value).toBe(1920 - MIN_VIEWPORT_WIDTH);

    layout.dispose();
  });

  it('dynamically adapts mode and visibility on media query changes', () => {
    setupViewportWidth(1920);
    const layout = useReportLayout();
    expect(layout.mode.value).toBe('SPLIT');
    expect(layout.isOpen.value).toBe(true);

    // Resize down to 1366px (DRAWER)
    const splitListeners = listeners.get(SPLIT_MEDIA_QUERY);
    const drawerListeners = listeners.get(DRAWER_MEDIA_QUERY) || listeners.get('(min-width: 1024px)');

    splitListeners?.forEach((l) => l({ matches: false, media: SPLIT_MEDIA_QUERY } as MediaQueryListEvent));
    drawerListeners?.forEach((l) => l({ matches: true, media: DRAWER_MEDIA_QUERY } as MediaQueryListEvent));

    expect(layout.mode.value).toBe('DRAWER');
    expect(layout.isOpen.value).toBe(false);

    // Resize down to 390px (REPORT_ONLY)
    drawerListeners?.forEach((l) => l({ matches: false, media: DRAWER_MEDIA_QUERY } as MediaQueryListEvent));

    expect(layout.mode.value).toBe('REPORT_ONLY');
    expect(layout.isOpen.value).toBe(true);

    layout.dispose();
  });

  it('cleans up all media query event listeners on dispose', () => {
    setupViewportWidth(1920);
    const layout = useReportLayout();

    layout.dispose();
    listeners.forEach((set) => {
      expect(set.size).toBe(0);
    });
  });

  it('never inspects navigator.userAgent', () => {
    setupViewportWidth(1920);
    const layout = useReportLayout();
    expect(userAgentSpy).not.toHaveBeenCalled();
    layout.dispose();
  });

  it('falls back safely to REPORT_ONLY when matchMedia is unavailable', () => {
    // @ts-expect-error simulating environment without matchMedia
    window.matchMedia = undefined;

    const layout = useReportLayout();
    expect(layout.mode.value).toBe('REPORT_ONLY');
    expect(layout.isOpen.value).toBe(true);
    expect(() => layout.dispose()).not.toThrow();
  });
});

import { ref, readonly, getCurrentScope, onScopeDispose, type Ref } from 'vue';

export type ReportLayoutMode = 'SPLIT' | 'DRAWER' | 'REPORT_ONLY';

export const SPLIT_MEDIA_QUERY = '(min-width: 1440px)';
export const DRAWER_MEDIA_QUERY = '(min-width: 1024px) and (max-width: 1439.98px)';

export const MIN_REPORT_WIDTH = 360;
export const MIN_VIEWPORT_WIDTH = 720;
export const DEFAULT_REPORT_WIDTH = 480;

export interface ReportLayoutController {
  readonly mode: Readonly<Ref<ReportLayoutMode>>;
  readonly isOpen: Ref<boolean>;
  readonly panelWidth: Ref<number>;
  open(): void;
  close(): void;
  toggle(): void;
  setPanelWidth(width: number, workspaceWidth?: number): void;
  dispose(): void;
}

export function useReportLayout(): ReportLayoutController {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    const fallbackMode = ref<ReportLayoutMode>('REPORT_ONLY');
    const fallbackIsOpen = ref(true);
    const fallbackPanelWidth = ref(DEFAULT_REPORT_WIDTH);

    return {
      mode: readonly(fallbackMode),
      isOpen: fallbackIsOpen,
      panelWidth: fallbackPanelWidth,
      open: () => {
        fallbackIsOpen.value = true;
      },
      close: () => {
        fallbackIsOpen.value = false;
      },
      toggle: () => {
        fallbackIsOpen.value = !fallbackIsOpen.value;
      },
      setPanelWidth: (w: number) => {
        fallbackPanelWidth.value = w;
      },
      dispose: () => {},
    };
  }

  const mqlSplit = window.matchMedia(SPLIT_MEDIA_QUERY);
  const mqlDrawer = window.matchMedia(DRAWER_MEDIA_QUERY);

  let splitMatches = mqlSplit.matches;
  let drawerMatches = mqlDrawer.matches;

  function computeMode(): ReportLayoutMode {
    if (splitMatches) return 'SPLIT';
    if (drawerMatches) return 'DRAWER';
    return 'REPORT_ONLY';
  }

  const initialMode = computeMode();
  const mode = ref<ReportLayoutMode>(initialMode);
  const isOpen = ref<boolean>(initialMode !== 'DRAWER');
  const panelWidth = ref<number>(DEFAULT_REPORT_WIDTH);

  function update() {
    const nextMode = computeMode();
    const prevMode = mode.value;
    mode.value = nextMode;

    if (nextMode !== prevMode) {
      if (nextMode === 'SPLIT') {
        isOpen.value = true;
      } else if (nextMode === 'DRAWER') {
        isOpen.value = false;
      } else if (nextMode === 'REPORT_ONLY') {
        isOpen.value = true;
      }
    }
  }

  const handleSplitChange = (e: MediaQueryListEvent | MediaQueryList) => {
    splitMatches = e.matches;
    update();
  };

  const handleDrawerChange = (e: MediaQueryListEvent | MediaQueryList) => {
    drawerMatches = e.matches;
    update();
  };

  const attachListener = (
    mql: MediaQueryList,
    handler: (e: MediaQueryListEvent | MediaQueryList) => void,
  ) => {
    if (typeof mql.addEventListener === 'function') {
      mql.addEventListener('change', handler as EventListener);
    } else if (typeof mql.addListener === 'function') {
      mql.addListener(handler);
    }
  };

  const removeListener = (
    mql: MediaQueryList,
    handler: (e: MediaQueryListEvent | MediaQueryList) => void,
  ) => {
    if (typeof mql.removeEventListener === 'function') {
      mql.removeEventListener('change', handler as EventListener);
    } else if (typeof mql.removeListener === 'function') {
      mql.removeListener(handler);
    }
  };

  attachListener(mqlSplit, handleSplitChange);
  attachListener(mqlDrawer, handleDrawerChange);

  let disposed = false;
  function dispose() {
    if (disposed) return;
    disposed = true;
    removeListener(mqlSplit, handleSplitChange);
    removeListener(mqlDrawer, handleDrawerChange);
  }

  if (getCurrentScope()) {
    onScopeDispose(dispose);
  }

  function setPanelWidth(width: number, workspaceWidth: number = window.innerWidth): void {
    const maxAllowed = Math.max(MIN_REPORT_WIDTH, workspaceWidth - MIN_VIEWPORT_WIDTH);
    panelWidth.value = Math.max(MIN_REPORT_WIDTH, Math.min(width, maxAllowed));
  }

  function open(): void {
    isOpen.value = true;
  }

  function close(): void {
    isOpen.value = false;
  }

  function toggle(): void {
    isOpen.value = !isOpen.value;
  }

  return {
    mode: readonly(mode) as Readonly<Ref<ReportLayoutMode>>,
    isOpen,
    panelWidth,
    open,
    close,
    toggle,
    setPanelWidth,
    dispose,
  };
}

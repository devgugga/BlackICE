import { ref, readonly, getCurrentScope, onScopeDispose, type Ref } from 'vue';

export const VIEWER_MEDIA_QUERY = '(min-width: 1024px)';

export interface ViewerCapability {
  readonly canRenderViewer: Readonly<Ref<boolean>>;
  dispose(): void;
}

export function useViewerCapability(): ViewerCapability {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return {
      canRenderViewer: readonly(ref(false)),
      dispose: () => {},
    };
  }

  const mql = window.matchMedia(VIEWER_MEDIA_QUERY);
  const canRenderViewer = ref<boolean>(mql.matches);

  const update = (event?: MediaQueryListEvent | MediaQueryList) => {
    canRenderViewer.value = event ? event.matches : mql.matches;
  };

  if (typeof mql.addEventListener === 'function') {
    mql.addEventListener('change', update);
  } else if (typeof mql.addListener === 'function') {
    mql.addListener(update);
  }

  let disposed = false;
  function dispose(): void {
    if (disposed) return;
    disposed = true;
    if (typeof mql.removeEventListener === 'function') {
      mql.removeEventListener('change', update);
    } else if (typeof mql.removeListener === 'function') {
      mql.removeListener(update);
    }
  }

  if (getCurrentScope()) {
    onScopeDispose(dispose);
  }

  return {
    canRenderViewer: readonly(canRenderViewer),
    dispose,
  };
}

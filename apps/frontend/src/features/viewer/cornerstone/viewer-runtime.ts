import {
  RenderingEngine,
  Enums,
  imageRetrievalPoolManager,
  imageLoadPoolManager,
} from '@cornerstonejs/core';
import {
  ToolGroupManager,
  WindowLevelTool,
  ZoomTool,
  PanTool,
  StackScrollTool,
  LengthTool,
  Enums as ToolsEnums,
  utilities as toolsUtilities,
  annotation,
} from '@cornerstonejs/tools';
import type { ViewerSeriesInstances, ViewerTool } from '../viewer.types';
import type { ProblemCode } from '@/shared/api/problems/problem-types.generated';
import { PROBLEM_TYPES } from '@/shared/api/problems/problem-types.generated';
import { initCornerstoneOnce } from './cornerstone-init';
import {
  registerInstancesMetadata,
  clearMetadata,
  buildImageId,
} from './viewer-metadata';

export { buildImageId };

export interface ViewerRuntime {
  setSeries(series: ViewerSeriesInstances): Promise<void>;
  setTool(tool: ViewerTool): void;
  reset(): void;
  dispose(): void;
}

let instanceCounter = 0;

function mapErrorToCode(err: unknown): ProblemCode {
  if (typeof err === 'object' && err !== null && 'code' in err) {
    const candidateCode = (err as { code: string }).code;
    if (candidateCode in PROBLEM_TYPES) {
      return candidateCode as ProblemCode;
    }
  }

  const message = (err instanceof Error ? err.message : String(err)).toLowerCase();
  if (message.includes('network') || message.includes('failed to fetch')) {
    return 'CLIENT_NETWORK_UNAVAILABLE';
  }
  if (message.includes('timeout') || message.includes('timed out')) {
    return 'CLIENT_REQUEST_TIMEOUT';
  }
  if (
    message.includes('response') ||
    message.includes('invalid') ||
    message.includes('corrupt')
  ) {
    return 'CLIENT_RESPONSE_INVALID';
  }

  return 'CLIENT_DICOM_IMAGE_UNSUPPORTED';
}

export async function createViewerRuntime(
  element: HTMLDivElement,
  onFailure: (code: ProblemCode) => void,
): Promise<ViewerRuntime> {
  await initCornerstoneOnce();

  instanceCounter += 1;
  const renderingEngineId = `blackice-engine-${instanceCounter}`;
  const viewportId = `blackice-viewport-${instanceCounter}`;
  const toolGroupId = `blackice-toolgroup-${instanceCounter}`;

  let isDisposed = false;
  const visitedImageIds = new Set<string>();

  const renderingEngine = new RenderingEngine(renderingEngineId);
  renderingEngine.enableElement({
    viewportId,
    type: Enums.ViewportType.STACK,
    element,
  });

  const toolGroup = ToolGroupManager.createToolGroup(toolGroupId);
  if (toolGroup) {
    toolGroup.addTool(WindowLevelTool.toolName);
    toolGroup.addTool(ZoomTool.toolName);
    toolGroup.addTool(PanTool.toolName);
    toolGroup.addTool(StackScrollTool.toolName);
    toolGroup.addTool(LengthTool.toolName);
    toolGroup.addViewport(viewportId, renderingEngineId);
  }

  const applyToolBindings = (tool: ViewerTool) => {
    if (!toolGroup || isDisposed) return;

    const toolNameMap: Record<ViewerTool, string> = {
      WINDOW_LEVEL: WindowLevelTool.toolName,
      ZOOM: ZoomTool.toolName,
      PAN: PanTool.toolName,
      STACK_SCROLL: StackScrollTool.toolName,
      LENGTH: LengthTool.toolName,
    };

    const primaryToolName = toolNameMap[tool];

    for (const [t, name] of Object.entries(toolNameMap) as [ViewerTool, string][]) {
      if (t !== tool && name !== StackScrollTool.toolName) {
        toolGroup.setToolPassive(name);
      }
    }

    if (tool === 'STACK_SCROLL') {
      toolGroup.setToolActive(StackScrollTool.toolName, {
        bindings: [
          { mouseButton: ToolsEnums.MouseBindings.Primary },
          { mouseButton: ToolsEnums.MouseBindings.Wheel },
        ],
      });
    } else {
      toolGroup.setToolActive(primaryToolName, {
        bindings: [{ mouseButton: ToolsEnums.MouseBindings.Primary }],
      });
      toolGroup.setToolActive(StackScrollTool.toolName, {
        bindings: [{ mouseButton: ToolsEnums.MouseBindings.Wheel }],
      });
    }
  };

  applyToolBindings('WINDOW_LEVEL');

  const setSeries = async (series: ViewerSeriesInstances): Promise<void> => {
    if (isDisposed) return;

    try {
      try {
        toolsUtilities.stackPrefetch.disable(element);
      } catch {
        // stackPrefetch may not have been enabled yet
      }
      imageRetrievalPoolManager.clearRequestStack(Enums.RequestType.Prefetch);
      imageLoadPoolManager.clearRequestStack(Enums.RequestType.Prefetch);

      const imageIds = registerInstancesMetadata(
        series.instances,
        series.studyInstanceUid,
        series.seriesInstanceUid,
      );

      for (const id of imageIds) {
        visitedImageIds.add(id);
      }

      if (imageIds.length === 0) {
        return;
      }

      const viewport = renderingEngine.getViewport(viewportId) as any;
      if (!viewport) return;

      await viewport.setStack(imageIds, 0);
      viewport.render();

      toolsUtilities.stackPrefetch.setConfiguration({
        maxImagesToPrefetch: 3,
        preserveExistingPool: false,
      });
      imageRetrievalPoolManager.setMaxSimultaneousRequests(Enums.RequestType.Prefetch, 2);
      imageLoadPoolManager.setMaxSimultaneousRequests(Enums.RequestType.Prefetch, 2);
      toolsUtilities.stackPrefetch.enable(element);
    } catch (err: unknown) {
      const code = mapErrorToCode(err);
      onFailure(code);
    }
  };

  const setTool = (tool: ViewerTool): void => {
    if (isDisposed) return;
    applyToolBindings(tool);
  };

  const reset = (): void => {
    if (isDisposed) return;
    try {
      const viewport = renderingEngine.getViewport(viewportId) as any;
      if (viewport) {
        viewport.resetCamera?.();
        viewport.resetProperties?.();
        viewport.render();
      }
    } catch {
      // Ignore reset rendering errors
    }
  };

  const dispose = (): void => {
    if (isDisposed) return;
    isDisposed = true;

    try {
      toolsUtilities.stackPrefetch.disable(element);
    } catch {}

    try {
      const requestTypes = [
        Enums.RequestType.Prefetch,
        Enums.RequestType.Interaction,
        Enums.RequestType.Thumbnail,
        Enums.RequestType.Compute,
      ];
      for (const type of requestTypes) {
        imageRetrievalPoolManager.clearRequestStack(type);
        imageLoadPoolManager.clearRequestStack(type);
      }
    } catch {}

    try {
      annotation.state.removeAllAnnotations();
    } catch {}

    try {
      ToolGroupManager.destroyToolGroup(toolGroupId);
    } catch {}

    try {
      renderingEngine.disableElement(viewportId);
    } catch {}

    try {
      renderingEngine.destroy();
    } catch {}

    clearMetadata(Array.from(visitedImageIds));
    visitedImageIds.clear();
  };

  return {
    setSeries,
    setTool,
    reset,
    dispose,
  };
}

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { ViewerSeriesInstances } from '../viewer.types';
import type { ProblemCode } from '@/shared/api/problems/problem-types.generated';

const mocks = vi.hoisted(() => {
  const mockViewport = {
    setStack: vi.fn().mockResolvedValue(undefined),
    render: vi.fn(),
    resetCamera: vi.fn(),
    resetProperties: vi.fn(),
  };

  const mockRenderingEngine = {
    enableElement: vi.fn(),
    disableElement: vi.fn(),
    destroy: vi.fn(),
    getViewport: vi.fn().mockReturnValue(mockViewport),
  };

  const mockToolGroup = {
    addTool: vi.fn(),
    addViewport: vi.fn(),
    setToolActive: vi.fn(),
    setToolPassive: vi.fn(),
    setToolDisabled: vi.fn(),
  };

  const mockStackPrefetch = {
    enable: vi.fn(),
    disable: vi.fn(),
    setConfiguration: vi.fn(),
  };

  const mockImageRetrievalPoolManager = {
    setMaxSimultaneousRequests: vi.fn(),
    clearRequestStack: vi.fn(),
  };

  const mockImageLoadPoolManager = {
    setMaxSimultaneousRequests: vi.fn(),
    clearRequestStack: vi.fn(),
  };

  const mockAnnotationState = {
    removeAllAnnotations: vi.fn(),
    getAllAnnotations: vi.fn().mockReturnValue([]),
  };

  return {
    mockViewport,
    mockRenderingEngine,
    mockToolGroup,
    mockStackPrefetch,
    mockImageRetrievalPoolManager,
    mockImageLoadPoolManager,
    mockAnnotationState,
  };
});

const {
  mockViewport,
  mockRenderingEngine,
  mockToolGroup,
  mockStackPrefetch,
  mockImageRetrievalPoolManager,
  mockAnnotationState,
} = mocks;

vi.mock('@cornerstonejs/core', () => {
  return {
    init: vi.fn().mockResolvedValue(undefined),
    RenderingEngine: vi.fn(function () {
      return mocks.mockRenderingEngine;
    }),
    Enums: {
      ViewportType: {
        STACK: 'stack',
      },
      RequestType: {
        Prefetch: 'prefetch',
        Interaction: 'interaction',
        Thumbnail: 'thumbnail',
        Compute: 'compute',
      },
    },
    metaData: {
      addProvider: vi.fn(),
      get: vi.fn(),
    },
    imageRetrievalPoolManager: mocks.mockImageRetrievalPoolManager,
    imageLoadPoolManager: mocks.mockImageLoadPoolManager,
    eventTarget: {
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    },
  };
});

vi.mock('@cornerstonejs/tools', () => {
  return {
    init: vi.fn().mockResolvedValue(undefined),
    addTool: vi.fn(),
    WindowLevelTool: { toolName: 'WindowLevel' },
    ZoomTool: { toolName: 'Zoom' },
    PanTool: { toolName: 'Pan' },
    StackScrollTool: { toolName: 'StackScroll' },
    LengthTool: { toolName: 'Length' },
    ToolGroupManager: {
      createToolGroup: vi.fn().mockImplementation(() => mocks.mockToolGroup),
      destroyToolGroup: vi.fn(),
    },
    Enums: {
      MouseBindings: {
        Primary: 1,
        Wheel: 524288,
      },
      Events: {
        ANNOTATION_ADDED: 'CORNERSTONE_TOOLS_ANNOTATION_ADDED',
        ANNOTATION_REMOVED: 'CORNERSTONE_TOOLS_ANNOTATION_REMOVED',
        ANNOTATION_COMPLETED: 'CORNERSTONE_TOOLS_ANNOTATION_COMPLETED',
        ANNOTATION_MODIFIED: 'CORNERSTONE_TOOLS_ANNOTATION_MODIFIED',
      },
    },
    utilities: {
      stackPrefetch: mocks.mockStackPrefetch,
    },
    annotation: {
      state: mocks.mockAnnotationState,
    },
  };
});

vi.mock('@cornerstonejs/dicom-image-loader', () => {
  return {
    default: {
      init: vi.fn(),
    },
  };
});

import { createViewerRuntime } from './viewer-runtime';
import { ToolGroupManager } from '@cornerstonejs/tools';

describe('viewer-runtime', () => {
  let element: HTMLDivElement;
  let onFailure: (code: ProblemCode) => void;
  let failureCalls: ProblemCode[];

  const testSeries1: ViewerSeriesInstances = {
    studyInstanceUid: '1.2.840.10008.1.1',
    seriesInstanceUid: '1.2.840.10008.1.2.1',
    instances: [
      {
        sopInstanceUid: '1.2.840.10008.1.3.1',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
        instanceNumber: 1,
        rows: 512,
        columns: 512,
        samplesPerPixel: 1,
        photometricInterpretation: 'MONOCHROME2',
        bitsAllocated: 16,
        bitsStored: 12,
        highBit: 11,
        pixelRepresentation: 0,
        planarConfiguration: null,
        imagePositionPatient: [0, 0, 0],
        imageOrientationPatient: [1, 0, 0, 0, 1, 0],
        pixelSpacing: [1, 1],
        frameOfReferenceUid: '1.2.840.10008.1.99',
        rescaleIntercept: 0,
        rescaleSlope: 1,
        windowCenter: [40],
        windowWidth: [400],
      },
      {
        sopInstanceUid: '1.2.840.10008.1.3.2',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
        instanceNumber: 2,
        rows: 512,
        columns: 512,
        samplesPerPixel: 1,
        photometricInterpretation: 'MONOCHROME2',
        bitsAllocated: 16,
        bitsStored: 12,
        highBit: 11,
        pixelRepresentation: 0,
        planarConfiguration: null,
        imagePositionPatient: [0, 0, 5],
        imageOrientationPatient: [1, 0, 0, 0, 1, 0],
        pixelSpacing: [1, 1],
        frameOfReferenceUid: '1.2.840.10008.1.99',
        rescaleIntercept: 0,
        rescaleSlope: 1,
        windowCenter: [40],
        windowWidth: [400],
      },
    ],
  };

  const testSeries2: ViewerSeriesInstances = {
    studyInstanceUid: '1.2.840.10008.1.1',
    seriesInstanceUid: '1.2.840.10008.1.2.2',
    instances: [
      {
        sopInstanceUid: '1.2.840.10008.1.3.3',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
        instanceNumber: 1,
        rows: 512,
        columns: 512,
        samplesPerPixel: 1,
        photometricInterpretation: 'MONOCHROME2',
        bitsAllocated: 16,
        bitsStored: 12,
        highBit: 11,
        pixelRepresentation: 0,
        planarConfiguration: null,
        imagePositionPatient: [0, 0, 0],
        imageOrientationPatient: [1, 0, 0, 0, 1, 0],
        pixelSpacing: [1, 1],
        frameOfReferenceUid: '1.2.840.10008.1.99',
        rescaleIntercept: 0,
        rescaleSlope: 1,
        windowCenter: [40],
        windowWidth: [400],
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    element = document.createElement('div');
    failureCalls = [];
    onFailure = vi.fn((code: ProblemCode) => {
      failureCalls.push(code);
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('creation and lifecycle', () => {
    it('creates exactly one RenderingEngine, enables stack viewport, creates ToolGroup and registers tools', async () => {
      const runtime = await createViewerRuntime(element, onFailure);
      expect(runtime).toBeDefined();

      expect(mockRenderingEngine.enableElement).toHaveBeenCalledTimes(1);
      expect(mockRenderingEngine.enableElement).toHaveBeenCalledWith(
        expect.objectContaining({
          element,
          type: 'stack',
        }),
      );

      expect(mockToolGroup.addTool).toHaveBeenCalledWith('WindowLevel');
      expect(mockToolGroup.addTool).toHaveBeenCalledWith('Zoom');
      expect(mockToolGroup.addTool).toHaveBeenCalledWith('Pan');
      expect(mockToolGroup.addTool).toHaveBeenCalledWith('StackScroll');
      expect(mockToolGroup.addTool).toHaveBeenCalledWith('Length');
      expect(mockToolGroup.addViewport).toHaveBeenCalledTimes(1);
    });
  });

  describe('tool management', () => {
    it('binds active tool to primary mouse button and retains wheel navigation for stack scroll', async () => {
      const runtime = await createViewerRuntime(element, onFailure);

      // Default active tool is WINDOW_LEVEL
      expect(mockToolGroup.setToolActive).toHaveBeenCalledWith(
        'WindowLevel',
        expect.objectContaining({ bindings: [{ mouseButton: 1 }] }),
      );
      expect(mockToolGroup.setToolActive).toHaveBeenCalledWith(
        'StackScroll',
        expect.objectContaining({ bindings: [{ mouseButton: 524288 }] }),
      );

      // Switch to ZOOM
      runtime.setTool('ZOOM');
      expect(mockToolGroup.setToolActive).toHaveBeenCalledWith(
        'Zoom',
        expect.objectContaining({ bindings: [{ mouseButton: 1 }] }),
      );
      expect(mockToolGroup.setToolPassive).toHaveBeenCalledWith('WindowLevel');

      // Switch to PAN
      runtime.setTool('PAN');
      expect(mockToolGroup.setToolActive).toHaveBeenCalledWith(
        'Pan',
        expect.objectContaining({ bindings: [{ mouseButton: 1 }] }),
      );

      // Switch to LENGTH
      runtime.setTool('LENGTH');
      expect(mockToolGroup.setToolActive).toHaveBeenCalledWith(
        'Length',
        expect.objectContaining({ bindings: [{ mouseButton: 1 }] }),
      );

      // Switch to STACK_SCROLL as primary tool
      runtime.setTool('STACK_SCROLL');
      expect(mockToolGroup.setToolActive).toHaveBeenCalledWith(
        'StackScroll',
        expect.objectContaining({
          bindings: [{ mouseButton: 1 }, { mouseButton: 524288 }],
        }),
      );
    });
  });

  describe('setSeries and prefetching', () => {
    it('sets stack, loads first frame, and enables prefetch with at most 3 queued / 2 background requests', async () => {
      const runtime = await createViewerRuntime(element, onFailure);
      await runtime.setSeries(testSeries1);

      expect(mockViewport.setStack).toHaveBeenCalledTimes(1);
      expect(mockViewport.setStack).toHaveBeenCalledWith(
        [
          'wadors:/api/dicomweb/studies/1.2.840.10008.1.1/series/1.2.840.10008.1.2.1/instances/1.2.840.10008.1.3.1/frames/1',
          'wadors:/api/dicomweb/studies/1.2.840.10008.1.1/series/1.2.840.10008.1.2.1/instances/1.2.840.10008.1.3.2/frames/1',
        ],
        0,
      );
      expect(mockViewport.render).toHaveBeenCalled();

      // Prefetch configuration
      expect(mockStackPrefetch.setConfiguration).toHaveBeenCalledWith(
        expect.objectContaining({ maxImagesToPrefetch: 3, preserveExistingPool: false }),
      );
      expect(mockImageRetrievalPoolManager.setMaxSimultaneousRequests).toHaveBeenCalledWith('prefetch', 2);
      expect(mockStackPrefetch.enable).toHaveBeenCalledWith(element);
    });

    it('cancels background prefetch and preserves Length annotations when switching series', async () => {
      const runtime = await createViewerRuntime(element, onFailure);

      await runtime.setSeries(testSeries1);
      expect(mockAnnotationState.removeAllAnnotations).not.toHaveBeenCalled();

      // Switch series
      await runtime.setSeries(testSeries2);

      // Should cancel previous prefetch requests
      expect(mockStackPrefetch.disable).toHaveBeenCalledWith(element);
      expect(mockImageRetrievalPoolManager.clearRequestStack).toHaveBeenCalledWith('prefetch');

      // Annotations are NOT removed on series switch
      expect(mockAnnotationState.removeAllAnnotations).not.toHaveBeenCalled();
    });
  });

  describe('reset', () => {
    it('resets camera without clearing annotations', async () => {
      const runtime = await createViewerRuntime(element, onFailure);
      await runtime.setSeries(testSeries1);

      runtime.reset();
      expect(mockViewport.resetCamera).toHaveBeenCalled();
      expect(mockViewport.render).toHaveBeenCalled();
      expect(mockAnnotationState.removeAllAnnotations).not.toHaveBeenCalled();
    });
  });

  describe('dispose', () => {
    it('clears all resources, destroys toolGroup, renderingEngine and removes annotations idempotently', async () => {
      const runtime = await createViewerRuntime(element, onFailure);
      await runtime.setSeries(testSeries1);

      runtime.dispose();

      expect(mockStackPrefetch.disable).toHaveBeenCalled();
      expect(mockAnnotationState.removeAllAnnotations).toHaveBeenCalled();
      expect(ToolGroupManager.destroyToolGroup).toHaveBeenCalled();
      expect(mockRenderingEngine.disableElement).toHaveBeenCalled();
      expect(mockRenderingEngine.destroy).toHaveBeenCalled();

      // Calling dispose again is safe and does not double-destroy
      runtime.dispose();
      expect(mockRenderingEngine.destroy).toHaveBeenCalledTimes(1);
    });
  });

  describe('error sanitization and mapping', () => {
    it('maps image load error to CLIENT_DICOM_IMAGE_UNSUPPORTED without leaking raw info', async () => {
      mockViewport.setStack.mockRejectedValueOnce(new Error('Unknown transfer syntax 1.2.840.10008.1.2.4.90'));
      const runtime = await createViewerRuntime(element, onFailure);

      await runtime.setSeries(testSeries1);

      expect(onFailure).toHaveBeenCalledWith('CLIENT_DICOM_IMAGE_UNSUPPORTED');
      expect(failureCalls).toEqual(['CLIENT_DICOM_IMAGE_UNSUPPORTED']);
    });

    it('maps network errors to CLIENT_NETWORK_UNAVAILABLE', async () => {
      mockViewport.setStack.mockRejectedValueOnce(new Error('Failed to fetch'));
      const runtime = await createViewerRuntime(element, onFailure);

      await runtime.setSeries(testSeries1);

      expect(onFailure).toHaveBeenCalledWith('CLIENT_NETWORK_UNAVAILABLE');
    });

    it('maps timeout errors to CLIENT_REQUEST_TIMEOUT', async () => {
      mockViewport.setStack.mockRejectedValueOnce(new Error('The request timed out'));
      const runtime = await createViewerRuntime(element, onFailure);

      await runtime.setSeries(testSeries1);

      expect(onFailure).toHaveBeenCalledWith('CLIENT_REQUEST_TIMEOUT');
    });
  });
});

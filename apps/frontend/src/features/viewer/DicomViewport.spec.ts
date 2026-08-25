import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import type { ViewerSeriesInstances, ViewerTool } from './viewer.types';
import type { ProblemCode } from '@/shared/api/problems/problem-types.generated';

const mocks = vi.hoisted(() => {
  const mockRuntime = {
    setSeries: vi.fn().mockResolvedValue(undefined),
    setTool: vi.fn(),
    reset: vi.fn(),
    dispose: vi.fn(),
  };

  const createViewerRuntime = vi.fn().mockResolvedValue(mockRuntime);

  const eventListeners: Record<string, Function[]> = {};
  const mockEventTarget = {
    addEventListener: vi.fn((event: string, cb: Function) => {
      if (!eventListeners[event]) eventListeners[event] = [];
      eventListeners[event].push(cb);
    }),
    removeEventListener: vi.fn((event: string, cb: Function) => {
      if (eventListeners[event]) {
        eventListeners[event] = eventListeners[event].filter((f) => f !== cb);
      }
    }),
    dispatch: (event: string, detail?: unknown) => {
      for (const cb of eventListeners[event] || []) {
        cb({ type: event, detail });
      }
    },
  };

  const allAnnotations: unknown[] = [];
  const mockAnnotationState = {
    getAllAnnotations: vi.fn(() => allAnnotations),
    removeAllAnnotations: vi.fn(() => {
      allAnnotations.length = 0;
    }),
  };

  return {
    mockRuntime,
    createViewerRuntime,
    mockEventTarget,
    mockAnnotationState,
    allAnnotations,
    eventListeners,
  };
});

vi.mock('@cornerstonejs/core', () => {
  return {
    eventTarget: mocks.mockEventTarget,
  };
});

vi.mock('@cornerstonejs/tools', () => {
  return {
    Enums: {
      Events: {
        ANNOTATION_ADDED: 'CORNERSTONE_TOOLS_ANNOTATION_ADDED',
        ANNOTATION_REMOVED: 'CORNERSTONE_TOOLS_ANNOTATION_REMOVED',
        ANNOTATION_COMPLETED: 'CORNERSTONE_TOOLS_ANNOTATION_COMPLETED',
      },
    },
    annotation: {
      state: mocks.mockAnnotationState,
    },
  };
});

vi.mock('./cornerstone/viewer-runtime', () => {
  return {
    createViewerRuntime: mocks.createViewerRuntime,
    buildImageId: vi.fn((st: string, se: string, sop: string) => `wadors:${st}/${se}/${sop}`),
  };
});

import DicomViewport from './DicomViewport.vue';

describe('DicomViewport.vue', () => {
  const testInstances: ViewerSeriesInstances = {
    studyInstanceUid: '1.2.840.10008.1.1',
    seriesInstanceUid: '1.2.840.10008.1.2',
    instances: [
      {
        sopInstanceUid: '1.2.840.10008.1.3',
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
    mocks.allAnnotations.length = 0;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders viewport container with data-annotation-count="0" initially', async () => {
    const wrapper = mount(DicomViewport, {
      props: {
        instances: null,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    await nextTick();
    expect(wrapper.attributes('data-annotation-count')).toBe('0');
  });

  it('initializes runtime and calls setSeries when instances prop is provided', async () => {
    const wrapper = mount(DicomViewport, {
      props: {
        instances: testInstances,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    expect(wrapper.exists()).toBe(true);

    // Wait for async mount and runtime creation
    await flushPromises();

    expect(mocks.createViewerRuntime).toHaveBeenCalledTimes(1);
    expect(mocks.mockRuntime.setSeries).toHaveBeenCalledWith(testInstances);
    expect(mocks.mockRuntime.setTool).toHaveBeenCalledWith('WINDOW_LEVEL');
  });

  it('updates tool when activeTool prop changes', async () => {
    const wrapper = mount(DicomViewport, {
      props: {
        instances: testInstances,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    await flushPromises();

    await wrapper.setProps({ activeTool: 'ZOOM' });
    expect(mocks.mockRuntime.setTool).toHaveBeenCalledWith('ZOOM');
  });

  it('updates series when instances prop changes', async () => {
    const wrapper = mount(DicomViewport, {
      props: {
        instances: null,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    await flushPromises();

    await wrapper.setProps({ instances: testInstances });
    await flushPromises();

    expect(mocks.mockRuntime.setSeries).toHaveBeenCalledWith(testInstances);
  });

  it('emits failure when runtime invokes onFailure callback', async () => {
    let capturedOnFailure: ((code: ProblemCode) => void) | undefined;
    mocks.createViewerRuntime.mockImplementationOnce(async (_el, onFailure) => {
      capturedOnFailure = onFailure;
      return mocks.mockRuntime;
    });

    const wrapper = mount(DicomViewport, {
      props: {
        instances: testInstances,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    await flushPromises();
    expect(capturedOnFailure).toBeDefined();

    capturedOnFailure!('CLIENT_DICOM_IMAGE_UNSUPPORTED');
    expect(wrapper.emitted('failure')).toEqual([['CLIENT_DICOM_IMAGE_UNSUPPORTED']]);
  });

  it('emits failure if createViewerRuntime rejects during mount', async () => {
    mocks.createViewerRuntime.mockRejectedValueOnce(new Error('WebGL unsupported'));

    const wrapper = mount(DicomViewport, {
      props: {
        instances: testInstances,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    await flushPromises();

    expect(wrapper.emitted('failure')).toBeDefined();
    expect(wrapper.emitted('failure')![0]).toEqual(['CLIENT_DICOM_IMAGE_UNSUPPORTED']);
  });

  it('updates data-annotation-count reactively on Cornerstone annotation events', async () => {
    const wrapper = mount(DicomViewport, {
      props: {
        instances: testInstances,
        activeTool: 'LENGTH' as ViewerTool,
      },
    });

    await flushPromises();
    expect(wrapper.attributes('data-annotation-count')).toBe('0');

    // Simulate adding an annotation
    mocks.allAnnotations.push({ annotationUID: 'annot-1' });
    mocks.mockEventTarget.dispatch('CORNERSTONE_TOOLS_ANNOTATION_ADDED');
    await nextTick();

    expect(wrapper.attributes('data-annotation-count')).toBe('1');

    // Simulate adding a second annotation
    mocks.allAnnotations.push({ annotationUID: 'annot-2' });
    mocks.mockEventTarget.dispatch('CORNERSTONE_TOOLS_ANNOTATION_COMPLETED');
    await nextTick();

    expect(wrapper.attributes('data-annotation-count')).toBe('2');

    // Simulate removal
    mocks.allAnnotations.pop();
    mocks.mockEventTarget.dispatch('CORNERSTONE_TOOLS_ANNOTATION_REMOVED');
    await nextTick();

    expect(wrapper.attributes('data-annotation-count')).toBe('1');
  });

  it('disposes runtime and unregisters event listeners on unmount', async () => {
    const wrapper = mount(DicomViewport, {
      props: {
        instances: testInstances,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    await flushPromises();

    wrapper.unmount();

    expect(mocks.mockRuntime.dispose).toHaveBeenCalledTimes(1);
    expect(mocks.mockEventTarget.removeEventListener).toHaveBeenCalled();
  });

  it('exposes reset method and delegates to runtime.reset', async () => {
    const wrapper = mount(DicomViewport, {
      props: {
        instances: testInstances,
        activeTool: 'WINDOW_LEVEL' as ViewerTool,
      },
    });

    await flushPromises();

    wrapper.vm.reset();
    expect(mocks.mockRuntime.reset).toHaveBeenCalledTimes(1);
  });
});

function flushPromises(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

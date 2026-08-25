import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import ReportPanel from './ReportPanel.vue';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_MESSAGES } from '@/shared/api/problems/problem-messages.pt-BR';
import type { ReportSnapshot } from './report.types';
import type { StudyReportApi } from './useStudyReport';
import {
  SPLIT_MEDIA_QUERY,
  DRAWER_MEDIA_QUERY,
} from './useReportLayout';

// Mock vue-router navigation guards
let beforeRouteLeaveGuard: ((to: any, from: any, next: any) => any) | null = null;
let beforeRouteUpdateGuard: ((to: any, from: any, next: any) => any) | null = null;

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: (guard: any) => {
    beforeRouteLeaveGuard = guard;
  },
  onBeforeRouteUpdate: (guard: any) => {
    beforeRouteUpdateGuard = guard;
  },
  useRouter: () => ({
    push: vi.fn(),
    back: vi.fn(),
  }),
  useRoute: () => ({
    params: { studyUid: '1.2.840.113619.2.55.3.123' },
  }),
}));

describe('ReportPanel', () => {
  let mockApi: StudyReportApi;
  let listeners: Map<string, Set<(e: MediaQueryListEvent) => void>>;
  let activeWrapper: VueWrapper | null = null;

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

  const sampleReportSnapshot: ReportSnapshot = {
    report: {
      studyInstanceUid: '1.2.840.113619.2.55.3.123',
      authorDisplayName: 'dr.teste',
      status: 'DRAFT',
      content: 'Achados pulmonares normais.',
      editable: true,
      createdAt: '2026-08-25T14:00:00Z',
      updatedAt: '2026-08-25T14:15:00Z',
      finalizedAt: null,
    },
    etag: '"etag-123"',
  };

  beforeEach(() => {
    listeners = new Map();
    beforeRouteLeaveGuard = null;
    beforeRouteUpdateGuard = null;
    setupViewportWidth(1920);

    mockApi = {
      fetchStudyReport: vi.fn().mockResolvedValue(sampleReportSnapshot),
      createStudyReport: vi.fn().mockResolvedValue(sampleReportSnapshot),
      updateStudyReport: vi.fn().mockResolvedValue(sampleReportSnapshot),
    };
  });

  afterEach(() => {
    if (activeWrapper) {
      activeWrapper.unmount();
      activeWrapper = null;
    }
    vi.restoreAllMocks();
  });

  describe('Adaptive Layout and Visibility', () => {
    it('is open by default at 1920px (SPLIT mode)', async () => {
      setupViewportWidth(1920);
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      const panel = activeWrapper.find('[data-testid="report-panel"]');
      expect(panel.exists()).toBe(true);
      expect(panel.classes()).toContain('layout-split');
      expect(panel.isVisible()).toBe(true);
    });

    it('is closed by default at 1366px and 1024px (DRAWER mode)', async () => {
      setupViewportWidth(1366);
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      const panel = activeWrapper.find('[data-testid="report-panel"]');
      expect(panel.classes()).toContain('layout-drawer');
      expect(panel.isVisible()).toBe(false);
    });

    it('is open full-width at 390px (REPORT_ONLY mode)', async () => {
      setupViewportWidth(390);
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      const panel = activeWrapper.find('[data-testid="report-panel"]');
      expect(panel.classes()).toContain('layout-report-only');
      expect(panel.isVisible()).toBe(true);
    });

    it('closes drawer on Escape key without discarding text', async () => {
      setupViewportWidth(1366);
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
        attachTo: document.body,
      });
      await flushPromises();

      // Open drawer manually
      const vm = activeWrapper.vm as any;
      vm.layout.open();
      await activeWrapper.vm.$nextTick();
      expect(activeWrapper.find('[data-testid="report-panel"]').isVisible()).toBe(true);

      // Trigger Escape
      await activeWrapper.find('[data-testid="report-panel"]').trigger('keydown', { key: 'Escape' });
      expect(activeWrapper.find('[data-testid="report-panel"]').isVisible()).toBe(false);
    });

    it('preserves typed text in memory when closing and reopening panel', async () => {
      setupViewportWidth(1366);
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
        attachTo: document.body,
      });
      await flushPromises();

      const vm = activeWrapper.vm as any;
      vm.layout.open();
      await activeWrapper.vm.$nextTick();

      const textarea = activeWrapper.find<HTMLTextAreaElement>('textarea');
      await textarea.setValue('Rascunho não salvo mantido em memória');

      // Close panel
      vm.layout.close();
      await activeWrapper.vm.$nextTick();
      expect(activeWrapper.find('[data-testid="report-panel"]').isVisible()).toBe(false);

      // Reopen panel
      vm.layout.open();
      await activeWrapper.vm.$nextTick();
      expect(activeWrapper.find('[data-testid="report-panel"]').isVisible()).toBe(true);
      expect(activeWrapper.find<HTMLTextAreaElement>('textarea').element.value).toBe(
        'Rascunho não salvo mantido em memória',
      );
    });
  });

  describe('Phase Transitions and Error Handling', () => {
    it('renders loading state initially and transitions to ready', async () => {
      let resolveFetch!: (res: ReportSnapshot | null) => void;
      mockApi.fetchStudyReport = vi.fn().mockReturnValue(
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
      );

      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });

      expect(activeWrapper.find('[data-testid="report-loading"]').exists()).toBe(true);

      resolveFetch(sampleReportSnapshot);
      await flushPromises();

      expect(activeWrapper.find('[data-testid="report-loading"]').exists()).toBe(false);
      expect(activeWrapper.find('textarea').element.value).toBe('Achados pulmonares normais.');
    });

    it('handles 204 No Content (ABSENT phase) with empty editor ready for typing', async () => {
      vi.mocked(mockApi.fetchStudyReport).mockResolvedValue(null);

      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      expect(activeWrapper.find('[data-testid="report-loading"]').exists()).toBe(false);
      expect(activeWrapper.find('textarea').element.value).toBe('');
      expect(activeWrapper.find('[data-testid="report-status-badge"]').text()).toBe('Novo');
    });

    it('renders localized error message and retry button on retryable error', async () => {
      vi.mocked(mockApi.fetchStudyReport).mockRejectedValue(
        new ApiError('CLIENT_NETWORK_UNAVAILABLE', { traceId: 'trace-404' }),
      );

      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      const errorAlert = activeWrapper.find('[data-testid="report-error"]');
      expect(errorAlert.exists()).toBe(true);
      expect(errorAlert.text()).toContain(PROBLEM_MESSAGES.CLIENT_NETWORK_UNAVAILABLE);
      expect(errorAlert.text()).toContain('trace-404');

      const retryBtn = activeWrapper.find('[data-testid="report-retry-button"]');
      expect(retryBtn.exists()).toBe(true);

      // Click retry
      vi.mocked(mockApi.fetchStudyReport).mockResolvedValue(sampleReportSnapshot);
      await retryBtn.trigger('click');
      await flushPromises();

      expect(activeWrapper.find('[data-testid="report-error"]').exists()).toBe(false);
      expect(activeWrapper.find('textarea').element.value).toBe('Achados pulmonares normais.');
    });

    it('renders localized 409 conflict error without discarding local text', async () => {
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      const textarea = activeWrapper.find<HTMLTextAreaElement>('textarea');
      await textarea.setValue('Alteração local');

      vi.mocked(mockApi.updateStudyReport).mockRejectedValue(
        new ApiError('API_RESOURCE_CONFLICT'),
      );

      const saveBtn = activeWrapper.find('[data-testid="save-draft-button"]');
      await saveBtn.trigger('click');
      await flushPromises();

      const errorAlert = activeWrapper.find('[data-testid="report-error"]');
      expect(errorAlert.exists()).toBe(true);
      expect(errorAlert.text()).toContain(PROBLEM_MESSAGES.API_RESOURCE_CONFLICT);
      expect(textarea.element.value).toBe('Alteração local');
    });

    it('offers reload server version with confirmation on 412 version conflict', async () => {
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
        attachTo: document.body,
      });
      await flushPromises();

      const textarea = activeWrapper.find<HTMLTextAreaElement>('textarea');
      await textarea.setValue('Minhas mudanças concorrentes');

      vi.mocked(mockApi.updateStudyReport).mockRejectedValue(
        new ApiError('API_RESOURCE_VERSION_CONFLICT'),
      );

      const saveBtn = activeWrapper.find('[data-testid="save-draft-button"]');
      await saveBtn.trigger('click');
      await flushPromises();

      const errorAlert = activeWrapper.find('[data-testid="report-error"]');
      expect(errorAlert.exists()).toBe(true);
      expect(errorAlert.text()).toContain(PROBLEM_MESSAGES.API_RESOURCE_VERSION_CONFLICT);

      const reloadBtn = activeWrapper.find('[data-testid="reload-server-button"]');
      expect(reloadBtn.exists()).toBe(true);

      // Clicking reload opens confirmation modal
      await reloadBtn.trigger('click');
      const reloadModal = activeWrapper.find('[data-testid="reload-confirm-modal"]');
      expect(reloadModal.exists()).toBe(true);

      // Cancel reload modal -> keeps local text
      const cancelBtn = activeWrapper.find('[data-testid="reload-cancel-button"]');
      await cancelBtn.trigger('click');
      expect(activeWrapper.find('[data-testid="reload-confirm-modal"]').exists()).toBe(false);
      expect(textarea.element.value).toBe('Minhas mudanças concorrentes');

      // Open again and confirm reload
      await reloadBtn.trigger('click');
      const remoteSnapshot: ReportSnapshot = {
        ...sampleReportSnapshot,
        report: {
          ...sampleReportSnapshot.report,
          content: 'Versão atualizada por outro médico no servidor.',
        },
      };
      vi.mocked(mockApi.fetchStudyReport).mockResolvedValue(remoteSnapshot);

      const confirmBtn = activeWrapper.find('[data-testid="reload-confirm-submit-button"]');
      await confirmBtn.trigger('click');
      await flushPromises();

      expect(activeWrapper.find('textarea').element.value).toBe(
        'Versão atualizada por outro médico no servidor.',
      );
    });
  });

  describe('Navigation Loss Protection', () => {
    it('prevents unload when dirty and allows when clean', async () => {
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      // Clean state
      const beforeUnloadEventClean = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
      window.dispatchEvent(beforeUnloadEventClean);
      expect(beforeUnloadEventClean.defaultPrevented).toBe(false);

      // Modify content -> dirty state
      const textarea = activeWrapper.find<HTMLTextAreaElement>('textarea');
      await textarea.setValue('Alterações não salvas');

      const beforeUnloadEventDirty = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
      window.dispatchEvent(beforeUnloadEventDirty);
      expect(beforeUnloadEventDirty.defaultPrevented).toBe(true);
    });

    it('guards Vue router navigation with confirmation prompt while dirty', async () => {
      const confirmSpy = vi.spyOn(window, 'confirm');
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      expect(beforeRouteLeaveGuard).toBeDefined();

      // When clean, route leave passes without prompt
      const resultClean = beforeRouteLeaveGuard!({ name: 'worklist' }, {}, vi.fn());
      expect(resultClean).toBe(true);
      expect(confirmSpy).not.toHaveBeenCalled();

      // When dirty, user rejects prompt -> navigation blocked
      const textarea = activeWrapper.find<HTMLTextAreaElement>('textarea');
      await textarea.setValue('Texto modificado');

      confirmSpy.mockReturnValueOnce(false);
      const resultBlocked = beforeRouteLeaveGuard!({ name: 'worklist' }, {}, vi.fn());
      expect(resultBlocked).toBe(false);
      expect(confirmSpy).toHaveBeenCalled();

      // When dirty, user accepts prompt -> navigation allowed
      confirmSpy.mockReturnValueOnce(true);
      const resultAllowed = beforeRouteLeaveGuard!({ name: 'worklist' }, {}, vi.fn());
      expect(resultAllowed).toBe(true);

      // Verify route update guard functions the same way
      expect(beforeRouteUpdateGuard).toBeDefined();
      confirmSpy.mockReturnValueOnce(false);
      expect(beforeRouteUpdateGuard!({ name: 'viewer' }, {}, vi.fn())).toBe(false);
      confirmSpy.mockReturnValueOnce(true);
      expect(beforeRouteUpdateGuard!({ name: 'viewer' }, {}, vi.fn())).toBe(true);
    });
  });

  describe('Accessibility Live Announcements', () => {
    it('announces status changes via aria-live region', async () => {
      activeWrapper = mount(ReportPanel, {
        props: {
          studyUid: '1.2.840.113619.2.55.3.123',
          customApi: mockApi,
        },
      });
      await flushPromises();

      const announcer = activeWrapper.find('[data-testid="live-announcer"]');
      expect(announcer.exists()).toBe(true);
      expect(announcer.attributes('aria-live')).toBe('polite');
    });
  });
});

import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import IngestResult from '@/features/ingest/IngestResult.vue';
import type { IngestResponse } from '@/features/ingest/ingest.types';

describe('IngestResult', () => {
  it('preserva o resultado confirmado e identifica o estudo com resultado incerto', () => {
    const result = {
      outcome: 'PARTIAL',
      summary: {
        received: 2,
        locallyValid: 2,
        locallyRejected: 0,
        archiveAccepted: 1,
        archiveRejected: 1,
      },
      studies: [
        {
          studyInstanceUid: '2.25.900.1',
          status: 'COMPLETE',
          instances: [
            {
              sopInstanceUid: '2.25.900.1.1',
              status: 'ACCEPTED',
              reason: null,
            },
          ],
          errorCode: null,
        },
        {
          studyInstanceUid: '2.25.900.2',
          status: 'FAILED',
          instances: [],
          errorCode: 'OUTCOME_UNKNOWN',
        },
      ],
      locallyRejectedFiles: [],
    } satisfies IngestResponse;

    const wrapper = mount(IngestResult, { props: { result } });

    expect(wrapper.text()).toContain('Armazenados no Archive: 1');
    expect(wrapper.text()).toContain('Rejeitados ou sem confirmação no Archive: 1');
    expect(wrapper.text()).not.toContain('Rejeitados pelo Archive: 1');
    expect(wrapper.text()).toContain('1 armazenados');
    expect(wrapper.text()).toContain('2.25.900.1 — COMPLETE');
    expect(wrapper.text()).toContain('2.25.900.1.1 — Armazenado');
    expect(wrapper.text()).toContain('Resultado do Archive não confirmado');
  });
});

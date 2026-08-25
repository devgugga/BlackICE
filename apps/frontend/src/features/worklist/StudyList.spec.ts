import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyList from '@/features/worklist/StudyList.vue';
import type { StudySummary } from '@/features/worklist/worklist.types';

function createStudy(overrides?: Partial<StudySummary>): StudySummary {
  return {
    studyInstanceUid: '1.2.840.113619.2.55.3.604688435.123.1599720123.467',
    patientName: 'MARIA^SILVA',
    patientId: '12345',
    patientIdIssuer: 'HOSPITAL-A',
    studyDate: '2026-08-22',
    studyTime: '10:35:12',
    modalities: ['CT'],
    description: 'CT CHEST',
    seriesCount: 3,
    instanceCount: 187,
    ...overrides,
  };
}

describe('StudyList', () => {
  it('renderiza ação "Abrir estudo" na tabela desktop e no cartão mobile', () => {
    const study = createStudy();
    const wrapper = mount(StudyList, {
      props: {
        items: [study],
      },
    });

    const desktopRow = wrapper.find('[data-testid="study-row"]');
    expect(desktopRow.exists()).toBe(true);
    const desktopOpenBtn = desktopRow.find('[data-testid="open-study"]');
    expect(desktopOpenBtn.exists()).toBe(true);
    expect(desktopOpenBtn.text()).toContain('Abrir estudo');

    const mobileCard = wrapper.find('[data-testid="study-card"]');
    expect(mobileCard.exists()).toBe(true);
    const mobileOpenBtn = mobileCard.find('[data-testid="open-study"]');
    expect(mobileOpenBtn.exists()).toBe(true);
    expect(mobileOpenBtn.text()).toContain('Abrir estudo');
  });

  it('emite evento "open" com o StudyInstanceUID exato ao clicar na ação desktop', async () => {
    const study = createStudy({ studyInstanceUid: '1.2.840.113619.2.55.3.999.888' });
    const wrapper = mount(StudyList, {
      props: {
        items: [study],
      },
    });

    const desktopOpenBtn = wrapper.find('[data-testid="study-row"] [data-testid="open-study"]');
    await desktopOpenBtn.trigger('click');

    expect(wrapper.emitted('open')).toBeTruthy();
    expect(wrapper.emitted('open')![0]).toEqual(['1.2.840.113619.2.55.3.999.888']);
  });

  it('emite evento "open" com o StudyInstanceUID exato ao clicar na ação mobile', async () => {
    const study = createStudy({ studyInstanceUid: '1.2.840.113619.2.55.3.777.666' });
    const wrapper = mount(StudyList, {
      props: {
        items: [study],
      },
    });

    const mobileOpenBtn = wrapper.find('[data-testid="study-card"] [data-testid="open-study"]');
    await mobileOpenBtn.trigger('click');

    expect(wrapper.emitted('open')).toBeTruthy();
    expect(wrapper.emitted('open')![0]).toEqual(['1.2.840.113619.2.55.3.777.666']);
  });

  it('nunca renderiza o StudyInstanceUID como texto visível na interface', () => {
    const rawUid = '1.2.840.113619.2.55.3.604688435.123.1599720123.467';
    const study = createStudy({ studyInstanceUid: rawUid });
    const wrapper = mount(StudyList, {
      props: {
        items: [study],
      },
    });

    expect(wrapper.text()).not.toContain(rawUid);
  });

  it('exibe campos ausentes como "Não informado" sem impactar ação de abertura', async () => {
    const study: StudySummary = {
      studyInstanceUid: '1.2.840.113619.2.55.3.999.000',
      patientName: null,
      patientId: null,
      patientIdIssuer: null,
      studyDate: null,
      studyTime: null,
      modalities: [],
      description: null,
      seriesCount: null,
      instanceCount: null,
    };
    const wrapper = mount(StudyList, {
      props: {
        items: [study],
      },
    });

    expect(wrapper.text()).toContain('Não informado');
    const openBtn = wrapper.find('[data-testid="study-row"] [data-testid="open-study"]');
    await openBtn.trigger('click');

    expect(wrapper.emitted('open')![0]).toEqual(['1.2.840.113619.2.55.3.999.000']);
  });
});

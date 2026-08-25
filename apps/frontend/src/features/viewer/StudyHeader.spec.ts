import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyHeader from './StudyHeader.vue';
import type { StudyViewerSummary } from './viewer.types';

function createStudySummary(overrides?: Partial<StudyViewerSummary>): StudyViewerSummary {
  return {
    studyInstanceUid: '1.2.840.113619.2.55.3.604688435.123.1599720123.467',
    patientName: 'MARIA^SILVA',
    patientId: '12345',
    patientIdIssuer: 'HOSPITAL-A',
    studyDate: '2026-08-24',
    studyTime: '14:30:00',
    description: 'TC TORAX COM CONTRASTE',
    series: [],
    ...overrides,
  };
}

describe('StudyHeader', () => {
  it('renders patient name, ID with issuer, study date/time, and description', () => {
    const study = createStudySummary();
    const wrapper = mount(StudyHeader, {
      props: { study },
    });

    expect(wrapper.text()).toContain('MARIA^SILVA');
    expect(wrapper.text()).toContain('12345 · HOSPITAL-A');
    expect(wrapper.text()).toContain('2026-08-24 14:30:00');
    expect(wrapper.text()).toContain('TC TORAX COM CONTRASTE');
  });

  it('emits "back" event when the back button is clicked', async () => {
    const study = createStudySummary();
    const wrapper = mount(StudyHeader, {
      props: { study },
    });

    const backButton = wrapper.find('[data-testid="back-button"]');
    expect(backButton.exists()).toBe(true);

    await backButton.trigger('click');
    expect(wrapper.emitted('back')).toBeTruthy();
    expect(wrapper.emitted('back')?.length).toBe(1);
  });

  it('displays "Não informado" for missing optional attributes', () => {
    const study = createStudySummary({
      patientName: null,
      patientId: null,
      patientIdIssuer: null,
      studyDate: null,
      studyTime: null,
      description: null,
    });
    const wrapper = mount(StudyHeader, {
      props: { study },
    });

    expect(wrapper.text()).toContain('Não informado');
    expect(wrapper.text()).not.toContain('null');
    expect(wrapper.text()).not.toContain('undefined');
  });

  it('displays "Não informado" when study prop is null', () => {
    const wrapper = mount(StudyHeader, {
      props: { study: null },
    });

    expect(wrapper.text()).toContain('Não informado');
    const backButton = wrapper.find('[data-testid="back-button"]');
    expect(backButton.exists()).toBe(true);
  });

  it('never renders raw StudyInstanceUID in visible text', () => {
    const rawUid = '1.2.840.113619.2.55.3.604688435.123.1599720123.467';
    const study = createStudySummary({ studyInstanceUid: rawUid });
    const wrapper = mount(StudyHeader, {
      props: { study },
    });

    expect(wrapper.text()).not.toContain(rawUid);
  });

  it('renders patient ID without issuer when issuer is null', () => {
    const study = createStudySummary({
      patientId: 'PAT-999',
      patientIdIssuer: null,
    });
    const wrapper = mount(StudyHeader, {
      props: { study },
    });

    expect(wrapper.text()).toContain('PAT-999');
    expect(wrapper.text()).not.toContain('·');
  });
});

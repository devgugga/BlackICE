import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import SeriesRail from './SeriesRail.vue';
import type { ViewerSeriesSummary } from './viewer.types';

function createSeries(overrides?: Partial<ViewerSeriesSummary>): ViewerSeriesSummary {
  return {
    seriesInstanceUid: '1.2.840.113619.2.55.3.1.100',
    seriesNumber: 1,
    modality: 'CT',
    description: 'CHEST 5mm',
    instanceCount: 45,
    availability: 'SUPPORTED',
    unsupportedReason: null,
    ...overrides,
  };
}

describe('SeriesRail', () => {
  it('renders all provided series with number, modality, description and instance count', () => {
    const seriesList: ViewerSeriesSummary[] = [
      createSeries({
        seriesInstanceUid: '1.2.840.113619.2.55.3.1.101',
        seriesNumber: 1,
        modality: 'CT',
        description: 'CHEST 5mm',
        instanceCount: 50,
      }),
      createSeries({
        seriesInstanceUid: '1.2.840.113619.2.55.3.1.102',
        seriesNumber: 2,
        modality: 'CT',
        description: 'LUNG 1mm',
        instanceCount: 250,
      }),
    ];

    const wrapper = mount(SeriesRail, {
      props: {
        series: seriesList,
        selectedSeriesUid: '1.2.840.113619.2.55.3.1.101',
      },
    });

    const items = wrapper.findAll('[data-testid="series-item"]');
    expect(items.length).toBe(2);

    expect(wrapper.text()).toContain('Série 1');
    expect(wrapper.text()).toContain('CT');
    expect(wrapper.text()).toContain('CHEST 5mm');
    expect(wrapper.text()).toContain('50');

    expect(wrapper.text()).toContain('Série 2');
    expect(wrapper.text()).toContain('LUNG 1mm');
    expect(wrapper.text()).toContain('250');
  });

  it('marks selected series with aria-selected="true" and unselected with aria-selected="false"', () => {
    const s1 = createSeries({ seriesInstanceUid: '1.2.840.1.1' });
    const s2 = createSeries({ seriesInstanceUid: '1.2.840.1.2' });

    const wrapper = mount(SeriesRail, {
      props: {
        series: [s1, s2],
        selectedSeriesUid: '1.2.840.1.1',
      },
    });

    const buttons = wrapper.findAll('[data-testid="series-card"]');
    expect(buttons[0].attributes('aria-selected')).toBe('true');
    expect(buttons[1].attributes('aria-selected')).toBe('false');
  });

  it('emits "selectSeries" with seriesInstanceUid when a supported series is clicked', async () => {
    const s1 = createSeries({ seriesInstanceUid: '1.2.840.1.1' });
    const s2 = createSeries({ seriesInstanceUid: '1.2.840.1.2' });

    const wrapper = mount(SeriesRail, {
      props: {
        series: [s1, s2],
        selectedSeriesUid: '1.2.840.1.1',
      },
    });

    const buttons = wrapper.findAll('[data-testid="series-card"]');
    await buttons[1].trigger('click');

    expect(wrapper.emitted('selectSeries')).toBeTruthy();
    expect(wrapper.emitted('selectSeries')![0]).toEqual(['1.2.840.1.2']);
  });

  it('disables unsupported series and displays exact PT-BR reason for MULTI_FRAME', async () => {
    const unsupported = createSeries({
      seriesInstanceUid: '1.2.840.1.multi',
      availability: 'UNSUPPORTED',
      unsupportedReason: 'MULTI_FRAME',
    });

    const wrapper = mount(SeriesRail, {
      props: {
        series: [unsupported],
        selectedSeriesUid: null,
      },
    });

    expect(wrapper.text()).toContain('Objeto multi-frame ainda não suportado');

    const card = wrapper.find('[data-testid="series-card"]');
    expect(card.attributes('disabled')).toBeDefined();
    expect(card.attributes('aria-disabled')).toBe('true');

    await card.trigger('click');
    expect(wrapper.emitted('selectSeries')).toBeFalsy();
  });

  it('disables unsupported series and displays exact PT-BR reason for NON_IMAGE_OBJECT', async () => {
    const unsupported = createSeries({
      seriesInstanceUid: '1.2.840.1.sr',
      availability: 'UNSUPPORTED',
      unsupportedReason: 'NON_IMAGE_OBJECT',
    });

    const wrapper = mount(SeriesRail, {
      props: {
        series: [unsupported],
        selectedSeriesUid: null,
      },
    });

    expect(wrapper.text()).toContain('Tipo de objeto não suportado');

    const card = wrapper.find('[data-testid="series-card"]');
    expect(card.attributes('disabled')).toBeDefined();
    expect(card.attributes('aria-disabled')).toBe('true');

    await card.trigger('click');
    expect(wrapper.emitted('selectSeries')).toBeFalsy();
  });

  it('disables unsupported series and displays exact PT-BR reason for IMAGE_SOP_CLASS_UNSUPPORTED', async () => {
    const unsupported = createSeries({
      seriesInstanceUid: '1.2.840.1.sc',
      availability: 'UNSUPPORTED',
      unsupportedReason: 'IMAGE_SOP_CLASS_UNSUPPORTED',
    });

    const wrapper = mount(SeriesRail, {
      props: {
        series: [unsupported],
        selectedSeriesUid: null,
      },
    });

    expect(wrapper.text()).toContain('Formato de imagem não suportado');

    const card = wrapper.find('[data-testid="series-card"]');
    expect(card.attributes('disabled')).toBeDefined();
    expect(card.attributes('aria-disabled')).toBe('true');

    await card.trigger('click');
    expect(wrapper.emitted('selectSeries')).toBeFalsy();
  });

  it('never renders raw seriesInstanceUid in visible text', () => {
    const rawUid = '1.2.840.113619.2.55.3.1.99999999';
    const series = createSeries({ seriesInstanceUid: rawUid });

    const wrapper = mount(SeriesRail, {
      props: {
        series: [series],
        selectedSeriesUid: rawUid,
      },
    });

    expect(wrapper.text()).not.toContain(rawUid);
  });

  it('contains NO thumbnail elements (img/canvas)', () => {
    const series = createSeries();
    const wrapper = mount(SeriesRail, {
      props: {
        series: [series],
        selectedSeriesUid: null,
      },
    });

    expect(wrapper.find('img').exists()).toBe(false);
    expect(wrapper.find('canvas').exists()).toBe(false);
  });

  it('supports collapsible toggle and updates aria-expanded', async () => {
    const series = createSeries();
    const wrapper = mount(SeriesRail, {
      props: {
        series: [series],
        selectedSeriesUid: null,
      },
    });

    const toggle = wrapper.find('[data-testid="rail-toggle"]');
    expect(toggle.exists()).toBe(true);
    expect(toggle.attributes('aria-expanded')).toBe('true');

    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('false');

    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('true');
  });

  it('handles null seriesNumber and null description gracefully', () => {
    const series = createSeries({
      seriesNumber: null,
      description: null,
    });

    const wrapper = mount(SeriesRail, {
      props: {
        series: [series],
        selectedSeriesUid: null,
      },
    });

    expect(wrapper.text()).toContain('Não informado');
  });
});

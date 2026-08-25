import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ViewerToolbar from './ViewerToolbar.vue';
import type { ViewerTool } from './viewer.types';

describe('ViewerToolbar', () => {
  const allTools: ViewerTool[] = ['WINDOW_LEVEL', 'ZOOM', 'PAN', 'STACK_SCROLL', 'LENGTH'];

  it('renders all 5 viewer tools and the reset button', () => {
    const wrapper = mount(ViewerToolbar, {
      props: { activeTool: 'WINDOW_LEVEL' },
    });

    for (const tool of allTools) {
      const btn = wrapper.find(`[data-testid="tool-${tool}"]`);
      expect(btn.exists()).toBe(true);
    }

    const resetBtn = wrapper.find('[data-testid="tool-reset"]');
    expect(resetBtn.exists()).toBe(true);
  });

  it('marks only the active tool with aria-pressed="true"', () => {
    const wrapper = mount(ViewerToolbar, {
      props: { activeTool: 'ZOOM' },
    });

    const zoomBtn = wrapper.find('[data-testid="tool-ZOOM"]');
    expect(zoomBtn.attributes('aria-pressed')).toBe('true');

    const otherTools: ViewerTool[] = ['WINDOW_LEVEL', 'PAN', 'STACK_SCROLL', 'LENGTH'];
    for (const tool of otherTools) {
      const btn = wrapper.find(`[data-testid="tool-${tool}"]`);
      expect(btn.attributes('aria-pressed')).toBe('false');
    }
  });

  it('marks all tools with aria-pressed="false" when activeTool is null', () => {
    const wrapper = mount(ViewerToolbar, {
      props: { activeTool: null },
    });

    for (const tool of allTools) {
      const btn = wrapper.find(`[data-testid="tool-${tool}"]`);
      expect(btn.attributes('aria-pressed')).toBe('false');
    }
  });

  it('emits "selectTool" with the tool enum value when a tool is clicked', async () => {
    const wrapper = mount(ViewerToolbar, {
      props: { activeTool: 'WINDOW_LEVEL' },
    });

    for (const tool of allTools) {
      const btn = wrapper.find(`[data-testid="tool-${tool}"]`);
      await btn.trigger('click');
    }

    expect(wrapper.emitted('selectTool')).toBeTruthy();
    const emitted = wrapper.emitted('selectTool')!;
    expect(emitted.length).toBe(allTools.length);
    for (let i = 0; i < allTools.length; i++) {
      expect(emitted[i]).toEqual([allTools[i]]);
    }
  });

  it('emits "reset" when reset button is clicked and does not emit selectTool', async () => {
    const wrapper = mount(ViewerToolbar, {
      props: { activeTool: 'WINDOW_LEVEL' },
    });

    const resetBtn = wrapper.find('[data-testid="tool-reset"]');
    await resetBtn.trigger('click');

    expect(wrapper.emitted('reset')).toBeTruthy();
    expect(wrapper.emitted('reset')!.length).toBe(1);
    expect(wrapper.emitted('selectTool')).toBeFalsy();
  });

  it('renders report toggle button and emits "toggleReport" when clicked', async () => {
    const wrapper = mount(ViewerToolbar, {
      props: { activeTool: 'WINDOW_LEVEL', isReportOpen: false },
    });

    const reportBtn = wrapper.find('[data-testid="toggle-report-btn"]');
    expect(reportBtn.exists()).toBe(true);
    expect(reportBtn.attributes('aria-pressed')).toBe('false');

    await reportBtn.trigger('click');
    expect(wrapper.emitted('toggleReport')).toBeTruthy();
    expect(wrapper.emitted('toggleReport')!.length).toBe(1);

    await wrapper.setProps({ isReportOpen: true });
    expect(reportBtn.attributes('aria-pressed')).toBe('true');
  });
});

import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ReportEditor from './ReportEditor.vue';

describe('ReportEditor', () => {
  it('renders textarea with placeholder and binds exact plain text', async () => {
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: 'Achados iniciais:\n- Sem alterações',
        editable: true,
        status: 'DRAFT',
      },
    });

    const textarea = wrapper.find<HTMLTextAreaElement>('textarea');
    expect(textarea.exists()).toBe(true);
    expect(textarea.element.value).toBe('Achados iniciais:\n- Sem alterações');

    await textarea.setValue(' Novo texto com espaços e \n quebra ');
    const emitted = wrapper.emitted('update:modelValue');
    expect(emitted).toBeDefined();
    expect(emitted![0]).toEqual([' Novo texto com espaços e \n quebra ']);
  });

  it('calculates Unicode code points accurately including emojis and astral symbols', () => {
    const textWithAstral = 'Coração saudável 🫀👍';
    // String length in UTF-16 code units would be: 17 + 2 + 2 = 21
    // Unicode code points count is: 17 + 1 + 1 = 19
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: textWithAstral,
        editable: true,
      },
    });

    const counter = wrapper.find('[data-testid="code-point-counter"]');
    expect(counter.exists()).toBe(true);
    expect(counter.text()).toContain('19 / 32.000');
  });

  it('disables save and finalize actions when content is blank or only whitespace', () => {
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: '   \n\t  ',
        editable: true,
        status: 'DRAFT',
      },
    });

    const saveBtn = wrapper.find<HTMLButtonElement>('[data-testid="save-draft-button"]');
    const finalizeBtn = wrapper.find<HTMLButtonElement>('[data-testid="finalize-button"]');

    expect(saveBtn.attributes('disabled')).toBeDefined();
    expect(finalizeBtn.attributes('disabled')).toBeDefined();
  });

  it('disables save and finalize actions when content exceeds 32.000 code points', () => {
    const oversizedText = 'a'.repeat(32001);
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: oversizedText,
        editable: true,
        status: 'DRAFT',
      },
    });

    const saveBtn = wrapper.find<HTMLButtonElement>('[data-testid="save-draft-button"]');
    const finalizeBtn = wrapper.find<HTMLButtonElement>('[data-testid="finalize-button"]');
    const counter = wrapper.find('[data-testid="code-point-counter"]');

    expect(saveBtn.attributes('disabled')).toBeDefined();
    expect(finalizeBtn.attributes('disabled')).toBeDefined();
    expect(counter.classes()).toContain('counter-error');
  });

  it('disables save and finalize actions when saving or disabled prop is true', () => {
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: 'Laudo válido',
        editable: true,
        status: 'DRAFT',
        saving: true,
      },
    });

    const saveBtn = wrapper.find<HTMLButtonElement>('[data-testid="save-draft-button"]');
    const finalizeBtn = wrapper.find<HTMLButtonElement>('[data-testid="finalize-button"]');

    expect(saveBtn.attributes('disabled')).toBeDefined();
    expect(finalizeBtn.attributes('disabled')).toBeDefined();
  });

  it('displays metadata: author, status badge and formatted timestamp', () => {
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: 'Laudo válido',
        authorDisplayName: 'dr.teste',
        status: 'DRAFT',
        updatedAt: '2026-08-25T14:30:00Z',
        editable: true,
      },
    });

    const author = wrapper.find('[data-testid="report-author"]');
    const statusBadge = wrapper.find('[data-testid="report-status-badge"]');
    const timestamp = wrapper.find('[data-testid="report-timestamp"]');

    expect(author.text()).toContain('dr.teste');
    expect(statusBadge.text()).toBe('Rascunho');
    expect(timestamp.exists()).toBe(true);
  });

  it('renders read-only display with white-space: pre-wrap and NO v-html when status is FINAL', () => {
    const rawContent = 'Linha 1\n<script>alert("xss")</script>\nLinha 3';
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: rawContent,
        status: 'FINAL',
        editable: false,
        authorDisplayName: 'dr.laudador',
        finalizedAt: '2026-08-25T15:00:00Z',
      },
    });

    expect(wrapper.find('textarea').exists()).toBe(false);
    expect(wrapper.find('[data-testid="save-draft-button"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="finalize-button"]').exists()).toBe(false);

    const displayView = wrapper.find('[data-testid="report-content-view"]');
    expect(displayView.exists()).toBe(true);
    expect(displayView.text()).toContain('<script>alert("xss")</script>');
    // Verify no actual script element was injected
    expect(wrapper.find('script').exists()).toBe(false);
    expect(displayView.classes()).toContain('pre-wrap-content');
  });

  it('renders read-only display when editable is false even for draft', () => {
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: 'Rascunho de outro médico',
        status: 'DRAFT',
        editable: false,
        authorDisplayName: 'dr.outro',
      },
    });

    expect(wrapper.find('textarea').exists()).toBe(false);
    expect(wrapper.find('[data-testid="report-content-view"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="save-draft-button"]').exists()).toBe(false);
  });

  it('emits save-draft when save button is clicked', async () => {
    const wrapper = mount(ReportEditor, {
      props: {
        modelValue: 'Novo texto de laudo',
        status: 'DRAFT',
        editable: true,
      },
    });

    const saveBtn = wrapper.find('[data-testid="save-draft-button"]');
    await saveBtn.trigger('click');

    expect(wrapper.emitted('save-draft')).toBeDefined();
    expect(wrapper.emitted('save-draft')!.length).toBe(1);
  });

  describe('Confirmation Modal for Finalize', () => {
    it('opens confirmation modal when clicking finalize button', async () => {
      const wrapper = mount(ReportEditor, {
        props: {
          modelValue: 'Laudo completo a ser finalizado',
          status: 'DRAFT',
          editable: true,
        },
        attachTo: document.body,
      });

      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);

      const finalizeBtn = wrapper.find('[data-testid="finalize-button"]');
      await finalizeBtn.trigger('click');

      const modal = wrapper.find('[role="dialog"]');
      expect(modal.exists()).toBe(true);
      expect(modal.attributes('aria-modal')).toBe('true');
      expect(modal.text()).toContain('irreversível');
      wrapper.unmount();
    });

    it('cancels modal on cancel button click without emitting finalize and restores focus', async () => {
      const wrapper = mount(ReportEditor, {
        props: {
          modelValue: 'Laudo em revisão',
          status: 'DRAFT',
          editable: true,
        },
        attachTo: document.body,
      });

      const finalizeBtn = wrapper.find<HTMLButtonElement>('[data-testid="finalize-button"]');
      finalizeBtn.element.focus();
      await finalizeBtn.trigger('click');

      expect(wrapper.find('[role="dialog"]').exists()).toBe(true);

      const cancelBtn = wrapper.find('[data-testid="modal-cancel-button"]');
      await cancelBtn.trigger('click');

      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      expect(wrapper.emitted('finalize')).toBeUndefined();
      expect(document.activeElement).toBe(finalizeBtn.element);
      wrapper.unmount();
    });

    it('closes modal and restores focus on Escape key', async () => {
      const wrapper = mount(ReportEditor, {
        props: {
          modelValue: 'Laudo pronto',
          status: 'DRAFT',
          editable: true,
        },
        attachTo: document.body,
      });

      const finalizeBtn = wrapper.find<HTMLButtonElement>('[data-testid="finalize-button"]');
      finalizeBtn.element.focus();
      await finalizeBtn.trigger('click');

      const modal = wrapper.find('[role="dialog"]');
      expect(modal.exists()).toBe(true);

      await modal.trigger('keydown', { key: 'Escape' });

      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      expect(wrapper.emitted('finalize')).toBeUndefined();
      expect(document.activeElement).toBe(finalizeBtn.element);
      wrapper.unmount();
    });

    it('traps focus (Tab / Shift+Tab) within modal dialog', async () => {
      const wrapper = mount(ReportEditor, {
        props: {
          modelValue: 'Laudo pronto',
          status: 'DRAFT',
          editable: true,
        },
        attachTo: document.body,
      });

      const finalizeBtn = wrapper.find<HTMLButtonElement>('[data-testid="finalize-button"]');
      await finalizeBtn.trigger('click');

      const modal = wrapper.find('[role="dialog"]');
      const cancelBtn = wrapper.find<HTMLButtonElement>('[data-testid="modal-cancel-button"]');
      const confirmBtn = wrapper.find<HTMLButtonElement>('[data-testid="modal-confirm-button"]');

      expect(wrapper.find('[role="dialog"]').exists()).toBe(true);

      // Focus last element (confirmBtn) and simulate Tab -> should focus first element (cancelBtn)
      confirmBtn.element.focus();
      const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
      modal.element.dispatchEvent(tabEvent);

      // Focus first element (cancelBtn) and simulate Shift+Tab -> should focus last element (confirmBtn)
      cancelBtn.element.focus();
      const shiftTabEvent = new KeyboardEvent('keydown', {
        key: 'Tab',
        shiftKey: true,
        bubbles: true,
        cancelable: true,
      });
      modal.element.dispatchEvent(shiftTabEvent);

      wrapper.unmount();
    });

    it('emits finalize and closes modal on confirm button click', async () => {
      const wrapper = mount(ReportEditor, {
        props: {
          modelValue: 'Laudo validado para finalização',
          status: 'DRAFT',
          editable: true,
        },
        attachTo: document.body,
      });

      const finalizeBtn = wrapper.find('[data-testid="finalize-button"]');
      await finalizeBtn.trigger('click');

      const confirmBtn = wrapper.find('[data-testid="modal-confirm-button"]');
      await confirmBtn.trigger('click');

      expect(wrapper.emitted('finalize')).toBeDefined();
      expect(wrapper.emitted('finalize')!.length).toBe(1);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      wrapper.unmount();
    });
  });
});

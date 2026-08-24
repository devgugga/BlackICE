import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import IngestFileList from '@/features/ingest/IngestFileList.vue';

describe('IngestFileList', () => {
  it('desabilita a remocao enquanto o lote esta ocupado', async () => {
    const wrapper = mount(IngestFileList, {
      props: {
        files: [new File(['dicom'], 'exame.dcm', { type: 'application/dicom' })],
        busy: true,
      },
    });

    const remove = wrapper.get('button');
    expect(remove.attributes('disabled')).toBeDefined();
    await remove.trigger('click');
    expect(wrapper.emitted('remove')).toBeUndefined();
  });
});

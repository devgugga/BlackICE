<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { eventTarget } from '@cornerstonejs/core';
import { Enums as ToolsEnums, annotation } from '@cornerstonejs/tools';
import type { ViewerSeriesInstances, ViewerTool } from './viewer.types';
import type { ProblemCode } from '@/shared/api/problems/problem-types.generated';
import { createViewerRuntime, type ViewerRuntime } from './cornerstone/viewer-runtime';

const props = withDefaults(
  defineProps<{
    instances: ViewerSeriesInstances | null;
    activeTool?: ViewerTool;
  }>(),
  {
    activeTool: 'WINDOW_LEVEL',
  },
);

const emit = defineEmits<{
  (e: 'failure', code: ProblemCode): void;
}>();

const viewportRef = ref<HTMLDivElement | null>(null);
const annotationCount = ref<number>(0);

let runtime: ViewerRuntime | null = null;

const updateAnnotationCount = () => {
  try {
    const annotations = annotation.state.getAllAnnotations();
    annotationCount.value = Array.isArray(annotations) ? annotations.length : 0;
  } catch {
    annotationCount.value = 0;
  }
};

onMounted(async () => {
  if (!viewportRef.value) return;

  updateAnnotationCount();

  eventTarget.addEventListener(ToolsEnums.Events.ANNOTATION_ADDED, updateAnnotationCount);
  eventTarget.addEventListener(ToolsEnums.Events.ANNOTATION_REMOVED, updateAnnotationCount);
  eventTarget.addEventListener(ToolsEnums.Events.ANNOTATION_COMPLETED, updateAnnotationCount);

  try {
    runtime = await createViewerRuntime(viewportRef.value, (code: ProblemCode) => {
      emit('failure', code);
    });

    if (props.activeTool) {
      runtime.setTool(props.activeTool);
    }

    if (props.instances) {
      await runtime.setSeries(props.instances);
    }
  } catch (_err: unknown) {
    emit('failure', 'CLIENT_DICOM_IMAGE_UNSUPPORTED');
  }
});

watch(
  () => props.instances,
  async (newInstances) => {
    if (runtime && newInstances) {
      await runtime.setSeries(newInstances);
    }
  },
);

watch(
  () => props.activeTool,
  (newTool) => {
    if (runtime && newTool) {
      runtime.setTool(newTool);
    }
  },
);

onUnmounted(() => {
  eventTarget.removeEventListener(ToolsEnums.Events.ANNOTATION_ADDED, updateAnnotationCount);
  eventTarget.removeEventListener(ToolsEnums.Events.ANNOTATION_REMOVED, updateAnnotationCount);
  eventTarget.removeEventListener(ToolsEnums.Events.ANNOTATION_COMPLETED, updateAnnotationCount);

  if (runtime) {
    runtime.dispose();
    runtime = null;
  }
});

const reset = (): void => {
  if (runtime) {
    runtime.reset();
  }
};

defineExpose({
  reset,
});
</script>

<template>
  <div
    ref="viewportRef"
    class="dicom-viewport"
    :data-annotation-count="annotationCount"
    data-testid="dicom-viewport"
  />
</template>

<style scoped>
.dicom-viewport {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: #000000;
  user-select: none;
  display: block;
}
</style>

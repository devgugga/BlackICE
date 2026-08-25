<script setup lang="ts">
import type { ViewerTool } from './viewer.types';

withDefaults(
  defineProps<{
    activeTool?: ViewerTool | null;
    isReportOpen?: boolean;
  }>(),
  {
    activeTool: null,
    isReportOpen: false,
  },
);

const emit = defineEmits<{
  (e: 'selectTool', tool: ViewerTool): void;
  (e: 'reset'): void;
  (e: 'toggleReport'): void;
}>();

interface ToolItem {
  id: ViewerTool;
  label: string;
  shortLabel: string;
  icon: string;
}

const tools: readonly ToolItem[] = [
  {
    id: 'WINDOW_LEVEL',
    label: 'Janela e Nível (Window/Level)',
    shortLabel: 'Janela/Nível',
    icon: '◐',
  },
  {
    id: 'ZOOM',
    label: 'Zoom',
    shortLabel: 'Zoom',
    icon: '🔍',
  },
  {
    id: 'PAN',
    label: 'Panorâmica (Pan)',
    shortLabel: 'Pan',
    icon: '✋',
  },
  {
    id: 'STACK_SCROLL',
    label: 'Navegar cortes (Stack Scroll)',
    shortLabel: 'Navegar',
    icon: '↕',
  },
  {
    id: 'LENGTH',
    label: 'Medição de comprimento (Length)',
    shortLabel: 'Medir',
    icon: '📏',
  },
];
</script>

<template>
  <nav class="viewer-toolbar" role="toolbar" aria-label="Ferramentas do visualizador">
    <div class="tool-group">
      <button
        v-for="t in tools"
        :key="t.id"
        type="button"
        class="toolbar-btn"
        :class="{ 'is-active': activeTool === t.id }"
        :data-testid="`tool-${t.id}`"
        :aria-pressed="activeTool === t.id ? 'true' : 'false'"
        :aria-label="t.label"
        :title="t.label"
        @click="emit('selectTool', t.id)"
      >
        <span class="tool-icon" aria-hidden="true">{{ t.icon }}</span>
        <span class="tool-label">{{ t.shortLabel }}</span>
      </button>
    </div>

    <div class="toolbar-divider" role="separator" aria-orientation="vertical" />

    <div class="action-group">
      <button
        type="button"
        class="toolbar-btn reset-btn"
        data-testid="tool-reset"
        aria-label="Redefinir visualização"
        title="Redefinir visualização"
        @click="emit('reset')"
      >
        <span class="tool-icon" aria-hidden="true">↺</span>
        <span class="tool-label">Redefinir</span>
      </button>
    </div>

    <div class="toolbar-divider" role="separator" aria-orientation="vertical" />

    <div class="report-group">
      <button
        type="button"
        class="toolbar-btn report-btn"
        :class="{ 'is-active': isReportOpen }"
        data-testid="toggle-report-btn"
        :aria-pressed="isReportOpen ? 'true' : 'false'"
        :aria-label="isReportOpen ? 'Ocultar laudo' : 'Exibir laudo'"
        :title="isReportOpen ? 'Ocultar laudo' : 'Exibir laudo'"
        @click="emit('toggleReport')"
      >
        <span class="tool-icon" aria-hidden="true">📝</span>
        <span class="tool-label">Laudo</span>
      </button>
    </div>
  </nav>
</template>

<style scoped>
.viewer-toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background-color: #1a1d20;
  color: #e2e8f0;
  padding: 0.375rem 0.75rem;
  border-bottom: 1px solid #2d3748;
  user-select: none;
  min-height: 44px;
}

.tool-group,
.action-group,
.report-group {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background-color: #4a5568;
  margin: 0 0.25rem;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.625rem;
  background-color: #23272d;
  color: #cbd5e0;
  border: 1px solid #374151;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 500;
  transition: all 0.15s ease-in-out;
}

.toolbar-btn:hover {
  background-color: #2d3748;
  color: #f7fafc;
  border-color: #4a5568;
}

.toolbar-btn:focus-visible {
  outline: 2px solid #3182ce;
  outline-offset: 2px;
}

.toolbar-btn.is-active {
  background-color: #2b4360;
  color: #63b3ed;
  border-color: #3182ce;
  box-shadow: 0 0 0 1px #3182ce;
}

.tool-icon {
  font-size: 0.875rem;
  line-height: 1;
}

.tool-label {
  font-size: 0.75rem;
}

.reset-btn:hover {
  background-color: #3b2a2a;
  border-color: #742a2a;
  color: #feb2b2;
}
</style>

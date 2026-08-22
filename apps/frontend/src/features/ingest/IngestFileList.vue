<script setup lang="ts">
defineProps<{
  files: readonly File[];
}>();

defineEmits<{
  remove: [index: number];
}>();

const formatBytes = (bytes: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'unit', unit: 'megabyte', maximumFractionDigits: 2 })
    .format(bytes / 1_048_576);
</script>

<template>
  <ul aria-label="Arquivos selecionados">
    <li v-for="(file, index) in files" :key="`${file.name}-${file.size}-${index}`">
      <span>{{ file.name }}</span>
      <span>{{ formatBytes(file.size) }}</span>
      <button type="button" :aria-label="`Remover ${file.name}`" @click="$emit('remove', index)">
        Remover
      </button>
    </li>
  </ul>
</template>

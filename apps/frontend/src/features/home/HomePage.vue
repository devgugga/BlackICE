<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { RouterLink } from 'vue-router';
import { fetchSession } from '@/features/session/session.api';

const username = ref<string>('');
onMounted(async () => {
  username.value = (await fetchSession())?.username ?? '';
});
</script>

<template>
  <main style="font-family: system-ui; padding: 2rem">
    <h1>BlackICE</h1>
    <p v-if="username">Autenticado como <strong>{{ username }}</strong>.</p>
    <nav>
      <RouterLink to="/ingest">Importar DICOM</RouterLink>
    </nav>
  </main>
</template>


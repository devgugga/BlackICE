import { createRouter, createWebHistory } from 'vue-router';
import HomePage from '@/features/home/HomePage.vue';
import IngestPage from '@/features/ingest/IngestPage.vue';
import { fetchSession } from '@/features/session/session.api';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage, meta: { protected: true } },
    { path: '/ingest', name: 'ingest', component: IngestPage, meta: { protected: true } },
  ],
});

router.beforeEach(async (to) => {
  if (!to.meta.protected) return true;
  try {
    const session = await fetchSession();
    if (session) return true;
  } catch (err) {
    // Erro nao-401 (5xx do backend, rede fora do ar) ao consultar /api/me:
    // sem este catch a navegacao e rejeitada e o usuario fica numa pagina em
    // branco. Caimos no fluxo de login, que reexpoe o estado real ao browser
    // em vez de abortar silenciosamente.
    console.error('guarda de rota: /api/me falhou, redirecionando ao login', err);
  }
  window.location.href = '/api/login'; // dispara o redirect OIDC (browser navega)
  return false;
});

export default router;

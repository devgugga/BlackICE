import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';
import { fetchSession } from '../lib/session';

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', name: 'home', component: HomeView, meta: { protected: true } }],
});

router.beforeEach(async (to) => {
  if (!to.meta.protected) return true;
  const session = await fetchSession();
  if (session) return true;
  window.location.href = '/api/login'; // dispara o redirect OIDC (browser navega)
  return false;
});

export default router;

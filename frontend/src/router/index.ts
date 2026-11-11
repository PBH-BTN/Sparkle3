import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, standalone: true },
    },
    {
      path: '/userapp',
      name: 'user-apps',
      component: () => import('@/views/UserAppsView.vue'),
    },
    {
      path: '/userapp/create',
      name: 'user-app-create',
      component: () => import('@/views/UserAppCreateView.vue'),
    },
    {
      path: '/userapp/created',
      name: 'user-app-credentials',
      component: () => import('@/views/UserAppCredentialsView.vue'),
    },
    {
      path: '/user/profile',
      alias: '/user',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
    },
    {
      path: '/user/points',
      name: 'points',
      component: () => import('@/views/PointsView.vue'),
    },
    {
      path: '/user/profile/:uid',
      name: 'public-profile',
      component: () => import('@/views/PublicProfileView.vue'),
    },
    {
      path: '/user/privacy-mode',
      name: 'privacy',
      component: () => import('@/views/PrivacyView.vue'),
    },
    {
      path: '/banhistory',
      name: 'ban-history',
      component: () => import('@/views/BanHistoryView.vue'),
    },
    {
      path: '/client-discovery',
      name: 'client-discovery',
      component: () => import('@/views/ClientDiscoveryView.vue'),
    },
    {
      path: '/swarm-tracker',
      name: 'swarm-tracker',
      component: () => import('@/views/SwarmTrackerView.vue'),
    },
    {
      path: '/rules/preview',
      name: 'rule-preview',
      component: () => import('@/views/RulePreviewView.vue'),
    },
    {
      path: '/ranking/swarmStatistics',
      name: 'ranking',
      component: () => import('@/views/RankingView.vue'),
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { admin: true },
      redirect: '/admin/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/AdminDashboardView.vue'),
        },
        {
          path: 'homepage-statistics',
          name: 'admin-homepage-statistics',
          component: () => import('@/views/admin/AdminHomepageStatisticsView.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/AdminUsersView.vue'),
        },
        {
          path: 'userapps',
          name: 'admin-userapps',
          component: () => import('@/views/admin/AdminUserAppsView.vue'),
        },
        {
          path: 'rules',
          name: 'admin-rules',
          component: () => import('@/views/admin/AdminRulesView.vue'),
        },
        {
          path: 'geoip-city-blacklist',
          name: 'admin-geoip-city-blacklist',
          component: () => import('@/views/admin/AdminGeoIpCityBlacklistView.vue'),
        },
        {
          path: 'modules',
          name: 'admin-modules',
          component: () => import('@/views/admin/AdminModulesView.vue'),
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: () => import('@/views/admin/AdminSettingsView.vue'),
        },
        {
          path: 'announcements',
          name: 'admin-announcements',
          component: () => import('@/views/admin/AdminAnnouncementsView.vue'),
        },
        {
          path: 'navigation',
          name: 'admin-navigation',
          component: () => import('@/views/admin/AdminNavigationView.vue'),
        },
        {
          path: 'storage',
          name: 'admin-storage',
          component: () => import('@/views/admin/AdminStorageView.vue'),
        },
        {
          path: 'version-alerts',
          name: 'admin-version-alerts',
          component: () => import('@/views/admin/AdminVersionAlertsView.vue'),
        },
        {
          path: 'user-contact-emails',
          name: 'admin-user-contact-emails',
          component: () => import('@/views/admin/AdminUserContactEmailsView.vue'),
        },
        {
          path: 'database-config',
          name: 'admin-database-config',
          component: () => import('@/views/admin/AdminDatabaseConfigView.vue'),
        },
      ],
    },
    {
      path: '/oauth/error',
      name: 'oauth-error',
      component: () => import('@/views/OAuthErrorView.vue'),
      meta: { public: true, standalone: true },
    },
    {
      path: '/ping/queryIp/widget',
      name: 'ip-query-widget',
      component: () => import('@/views/IpQueryWidgetView.vue'),
      meta: { public: true, standalone: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

export default router

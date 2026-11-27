import { adminRoutes } from './adminRoutes'
import { createRouter, createWebHistory } from 'vue-router'
import { scrollBehavior } from './scrollBehavior'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior,
  routes: [
    {
      path: '/mail-unsubscribe',
      name: 'mail-unsubscribe',
      component: () => import('@/views/MailUnsubscribeView.vue'),
      meta: { public: true, standalone: true },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/ip-query',
      name: 'ip-query',
      component: () => import('@/views/IpQueryView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, standalone: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegistrationView.vue'),
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
      path: '/user/traffic',
      name: 'traffic',
      component: () => import('@/views/TrafficStatisticsView.vue'),
    },
    {
      path: '/user/submissions',
      name: 'submissions',
      component: () => import('@/views/SubmissionsView.vue'),
    },
    {
      path: '/user/inbox',
      name: 'inbox',
      component: () => import('@/views/InboxView.vue'),
    },
    {
      path: '/user/profile/:uid',
      name: 'public-profile',
      component: () => import('@/views/PublicProfileView.vue'),
    },
    {
      path: '/user/privacy-mode',
      name: 'privacy',
      redirect: '/user/settings/privacy',
    },
    {
      path: '/user/settings',
      component: () => import('@/layouts/AccountSettingsLayout.vue'),
      redirect: '/user/settings/account',
      children: [
        {
          path: 'personalization',
          name: 'personalization-settings',
          component: () => import('@/views/PersonalizationView.vue'),
        },
        {
          path: 'account',
          name: 'account-settings',
          component: () => import('@/views/AccountSettingsView.vue'),
        },
        {
          path: 'privacy',
          name: 'privacy-settings',
          component: () => import('@/views/PrivacyView.vue'),
        },
        {
          path: 'notifications',
          name: 'notification-settings',
          component: () => import('@/views/NotificationSettingsView.vue'),
        },
        {
          path: 'community-groups',
          name: 'community-group-settings',
          component: () => import('@/views/CommunityGroupsView.vue'),
        },
        {
          path: 'afdian',
          redirect: { name: 'membership-settings' },
        },
        {
          path: 'memberships',
          name: 'membership-settings',
          component: () => import('@/views/MembershipPlatformsView.vue'),
        },
      ],
    },
    {
      path: '/account-deletion',
      name: 'account-deletion-result',
      component: () => import('@/views/AccountDeletionResultView.vue'),
      meta: { public: true, standalone: true },
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
      redirect: '/admin/tasks',
      children: adminRoutes,
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

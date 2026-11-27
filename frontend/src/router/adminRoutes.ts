import type { RouteRecordRaw } from 'vue-router'
import { adminPages, adminLegacyRedirects } from './adminNavigation'

const views: Record<string, NonNullable<RouteRecordRaw['component']>> = {
  AdminAiModerationView: () => import('@/views/admin/AdminAiModerationView.vue'),
  AdminAnnouncementsView: () => import('@/views/admin/AdminAnnouncementsView.vue'),
  AdminBanSubmissionsView: () => import('@/views/admin/AdminBanSubmissionsView.vue'),
  AdminBtnConfigView: () => import('@/views/admin/AdminBtnConfigView.vue'),
  AdminCommunityGroupsView: () => import('@/views/admin/AdminCommunityGroupsView.vue'),
  AdminContentModerationView: () => import('@/views/admin/AdminContentModerationView.vue'),
  AdminDashboardView: () => import('@/views/admin/AdminDashboardView.vue'),
  AdminDataExportsView: () => import('@/views/admin/AdminDataExportsView.vue'),
  AdminDatabaseConfigView: () => import('@/views/admin/AdminDatabaseConfigView.vue'),
  AdminFlarumIntegrationView: () => import('@/views/admin/AdminFlarumIntegrationView.vue'),
  AdminFooterSettingsView: () => import('@/views/admin/AdminFooterSettingsView.vue'),
  AdminGithubSyncView: () => import('@/views/admin/AdminGithubSyncView.vue'),
  AdminHomepageStatisticsView: () => import('@/views/admin/AdminHomepageStatisticsView.vue'),
  AdminIpQueryCommentsView: () => import('@/views/admin/AdminIpQueryCommentsView.vue'),
  AdminMailSettingsView: () => import('@/views/admin/AdminMailSettingsView.vue'),
  AdminMembershipsView: () => import('@/views/admin/AdminMembershipsView.vue'),
  AdminMessagesView: () => import('@/views/admin/AdminMessagesView.vue'),
  AdminModulesView: () => import('@/views/admin/AdminModulesView.vue'),
  AdminNavigationView: () => import('@/views/admin/AdminNavigationView.vue'),
  AdminRulesView: () => import('@/views/admin/AdminRulesView.vue'),
  AdminRuntimeSettingsView: () => import('@/views/admin/AdminRuntimeSettingsView.vue'),
  AdminStorageView: () => import('@/views/admin/AdminStorageView.vue'),
  AdminTaskStatusView: () => import('@/views/admin/AdminTaskStatusView.vue'),
  AdminUserAppsView: () => import('@/views/admin/AdminUserAppsView.vue'),
  AdminUserContactEmailsView: () => import('@/views/admin/AdminUserContactEmailsView.vue'),
  AdminUsersView: () => import('@/views/admin/AdminUsersView.vue'),
  AdminVersionAlertsView: () => import('@/views/admin/AdminVersionAlertsView.vue'),
}

export const adminRoutes: RouteRecordRaw[] = [
  ...adminPages.map((page): RouteRecordRaw => ({
    path: page.path.slice('/admin/'.length),
    name: page.name,
    component: views[page.view],
    props: page.props,
    meta: { admin: true, moderator: page.moderator },
  })),
  ...adminLegacyRedirects.map((entry): RouteRecordRaw => ({
    path: entry.path.slice('/admin/'.length),
    name: entry.name,
    redirect: entry.to,
  })),
]

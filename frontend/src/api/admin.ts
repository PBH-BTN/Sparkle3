import { request } from './http'
import type {
  Announcement,
  AnnouncementInput,
  AnalyseModule,
  AnalyseModuleRunResult,
  AnalyseModuleUpdate,
  AdminNavigationItem,
  AdminApplication,
  AdminApplicationCredentials,
  AdminApplicationInput,
  AdminApplicationQuery,
  AdminDashboard,
  AdminPage,
  AdminRuleQuery,
  AdminUser,
  AdminUserInput,
  AdminUserQuery,
  BackgroundTaskStatusResponse,
  BanHistoryDeletePreview,
  BanHistoryDeleteResult,
  BtnConfigView,
  CompressionPolicyInput,
  ConfigMigrationResult,
  DatabaseConfigEntry,
  DatabaseConfigSummary,
  HomepageStatisticsAdminResponse,
  HomepageStatisticsConfigUpdate,
  GeoIpCityBlacklistEntry,
  GeoIpCityBlacklistGenerationStatus,
  GeoIpCityBlacklistInput,
  ManualRule,
  NavigationItemInput,
  RetentionPolicyInput,
  RuleInput,
  RuleSummary,
  RuntimeConfig,
  StorageOverview,
  SwarmStatisticsStatus,
  TableStorage,
  TaskTriggerResponse,
  UserContactEmailAudit,
  VersionAlertDelivery,
  VersionAlertMatch,
  VersionAlertRule,
  VersionAlertRuleInput,
} from './adminTypes'

function queryString(values: object) {
  const params = new URLSearchParams()
  Object.entries(values as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      params.set(key, String(value))
    }
  })
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const adminApi = {
  dashboard: () => request<AdminDashboard>('/api/admin/dashboard'),
  taskStatus: () => request<BackgroundTaskStatusResponse>('/api/admin/tasks'),
  runTask: (id: string) =>
    request<TaskTriggerResponse>(`/api/admin/tasks/${encodeURIComponent(id)}/run`, {
      method: 'POST',
    }),

  homepageStatistics: () =>
    request<HomepageStatisticsAdminResponse>('/api/admin/homepage-statistics'),
  updateHomepageStatisticsConfig: (body: HomepageStatisticsConfigUpdate) =>
    request<HomepageStatisticsAdminResponse>('/api/admin/homepage-statistics/config', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  refreshHomepageStatistics: (module = 'all') =>
    request<HomepageStatisticsAdminResponse>('/api/admin/homepage-statistics/refresh', {
      method: 'POST',
      body: JSON.stringify({ module }),
    }),

  users: (query: AdminUserQuery) =>
    request<AdminPage<AdminUser>>(`/api/admin/users${queryString(query)}`),
  createUser: (body: AdminUserInput) =>
    request<AdminUser>('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateUser: (id: string, body: AdminUserInput) =>
    request<AdminUser>(`/api/admin/users/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  setUserBanned: (id: string, banned: boolean, reason?: string) =>
    request<AdminUser>(`/api/admin/users/${encodeURIComponent(id)}/ban`, {
      method: 'POST',
      body: JSON.stringify({ banned, reason }),
    }),
  adjustUserPoints: (id: string, amount: string, reason: string) =>
    request<AdminUser>(`/api/admin/users/${encodeURIComponent(id)}/points`, {
      method: 'POST',
      body: JSON.stringify({ amount, reason }),
    }),
  deleteUser: (id: string) =>
    request<void>(`/api/admin/users/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    }),

  applications: (query: AdminApplicationQuery) =>
    request<AdminPage<AdminApplication>>(`/api/admin/userapps${queryString(query)}`),
  createApplication: (body: AdminApplicationInput) =>
    request<AdminApplicationCredentials>('/api/admin/userapps', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateApplication: (id: number, body: AdminApplicationInput) =>
    request<AdminApplication>(`/api/admin/userapps/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  banApplication: (id: number, reason: string) =>
    request<AdminApplication>(`/api/admin/userapps/${id}/ban`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
  resetApplicationSecret: (id: number) =>
    request<AdminApplicationCredentials>(`/api/admin/userapps/${id}/reset-secret`, {
      method: 'POST',
    }),
  deleteApplication: (id: number) =>
    request<void>(`/api/admin/userapps/${id}`, {
      method: 'DELETE',
    }),

  rules: (query: AdminRuleQuery) =>
    request<AdminPage<ManualRule>>(`/api/admin/rules${queryString(query)}`),
  ruleSummary: () => request<RuleSummary[]>('/api/admin/rules/summary'),
  createRule: (body: RuleInput) =>
    request<ManualRule>('/api/admin/rules', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateRule: (id: number, body: RuleInput) =>
    request<ManualRule>(`/api/admin/rules/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteRule: (id: number) =>
    request<void>(`/api/admin/rules/${id}`, {
      method: 'DELETE',
    }),
  generatedRules: () => request<string>('/api/admin/rules/generated-content'),

  geoIpCityBlacklist: () =>
    request<GeoIpCityBlacklistEntry[]>('/api/admin/geoip-city-blacklist'),
  geoIpCityBlacklistStatus: () =>
    request<GeoIpCityBlacklistGenerationStatus>('/api/admin/geoip-city-blacklist/status'),
  refreshGeoIpCityBlacklist: () =>
    request<GeoIpCityBlacklistGenerationStatus>('/api/admin/geoip-city-blacklist/refresh', {
      method: 'POST',
    }),
  createGeoIpCityBlacklistEntry: (body: GeoIpCityBlacklistInput) =>
    request<GeoIpCityBlacklistEntry>('/api/admin/geoip-city-blacklist', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateGeoIpCityBlacklistEntry: (id: number, body: GeoIpCityBlacklistInput) =>
    request<GeoIpCityBlacklistEntry>(`/api/admin/geoip-city-blacklist/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteGeoIpCityBlacklistEntry: (id: number) =>
    request<void>(`/api/admin/geoip-city-blacklist/${id}`, {
      method: 'DELETE',
    }),
  generatedGeoIpCityBlacklistContent: (id: number) =>
    request<{ content: string }>(
      `/api/admin/geoip-city-blacklist/${id}/generated-content`,
    ),

  config: () => request<RuntimeConfig>('/api/admin/config'),
  updateConfig: (body: RuntimeConfig) =>
    request<RuntimeConfig>('/api/admin/config', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  swarmStatisticsStatus: () =>
    request<SwarmStatisticsStatus>('/api/admin/swarm-statistics/status'),
  refreshSwarmStatistics: () =>
    request<SwarmStatisticsStatus>('/api/admin/swarm-statistics/refresh', {
      method: 'POST',
    }),
  userContactEmails: (page: number, size: number, keyword = '') =>
    request<AdminPage<UserContactEmailAudit>>(
      `/api/admin/user-contact-emails${queryString({ page, size, keyword })}`,
    ),

  announcements: () => request<Announcement[]>('/api/admin/notices'),
  createAnnouncement: (body: AnnouncementInput) =>
    request<Announcement>('/api/admin/notices', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateAnnouncement: (id: number, body: AnnouncementInput) =>
    request<Announcement>(`/api/admin/notices/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteAnnouncement: (id: number) =>
    request<void>(`/api/admin/notices/${id}`, {
      method: 'DELETE',
    }),

  navigation: () => request<AdminNavigationItem[]>('/api/admin/navigation'),
  createNavigationItem: (body: NavigationItemInput) =>
    request<AdminNavigationItem>('/api/admin/navigation', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateNavigationItem: (id: string, body: NavigationItemInput) =>
    request<AdminNavigationItem>(`/api/admin/navigation/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteNavigationItem: (id: string) =>
    request<void>(`/api/admin/navigation/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    }),

  analyseModules: () => request<AnalyseModule[]>('/api/admin/modules'),
  updateAnalyseModule: (id: string, body: AnalyseModuleUpdate) =>
    request<AnalyseModule>(`/api/admin/modules/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  runAnalyseModule: (id: string) =>
    request<AnalyseModuleRunResult>(
      `/api/admin/modules/${encodeURIComponent(id)}/run`,
      { method: 'POST' },
    ),

  storage: () => request<StorageOverview>('/api/admin/storage/tables'),
  updateCompressionPolicy: (
    schema: string,
    table: string,
    body: CompressionPolicyInput,
  ) =>
    request<TableStorage>(
      `/api/admin/storage/hypertables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}/compression-policy`,
      {
        method: 'PUT',
        body: JSON.stringify(body),
      },
    ),
  updateRetentionPolicy: (schema: string, table: string, body: RetentionPolicyInput) =>
    request<TableStorage>(
      `/api/admin/storage/hypertables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}/retention-policy`,
      {
        method: 'PUT',
        body: JSON.stringify(body),
      },
    ),
  previewBanHistoryDelete: (cutoff: string) =>
    request<BanHistoryDeletePreview>('/api/admin/storage/ban-history/preview-delete', {
      method: 'POST',
      body: JSON.stringify({ cutoff }),
    }),
  deleteBanHistory: (cutoff: string, confirmation: string) =>
    request<BanHistoryDeleteResult>('/api/admin/storage/ban-history/delete', {
      method: 'POST',
      body: JSON.stringify({ cutoff, confirmation }),
    }),

  versionAlerts: () => request<VersionAlertRule[]>('/api/admin/version-alerts'),
  createVersionAlert: (body: VersionAlertRuleInput) =>
    request<VersionAlertRule>('/api/admin/version-alerts', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateVersionAlert: (id: string, body: VersionAlertRuleInput) =>
    request<VersionAlertRule>(`/api/admin/version-alerts/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteVersionAlert: (id: string) =>
    request<void>(`/api/admin/version-alerts/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    }),
  matchVersionAlert: (id: string, userAgent: string) =>
    request<VersionAlertMatch>(
      `/api/admin/version-alerts/${encodeURIComponent(id)}/match`,
      {
        method: 'POST',
        body: JSON.stringify({ userAgent }),
      },
    ),
  testVersionAlert: (id: string, recipient: string, userAgent: string) =>
    request<VersionAlertDelivery>(
      `/api/admin/version-alerts/${encodeURIComponent(id)}/test`,
      {
        method: 'POST',
        body: JSON.stringify({ recipient, userAgent }),
      },
    ),
  versionAlertDeliveries: (limit = 100) =>
    request<VersionAlertDelivery[]>(`/api/admin/version-alerts/deliveries?limit=${limit}`),

  databaseConfig: (keyword = '', group = '') =>
    request<DatabaseConfigEntry[]>(
      `/api/admin/database-config${queryString({ keyword, group })}`,
    ),
  databaseConfigSummary: () =>
    request<DatabaseConfigSummary>('/api/admin/database-config/summary'),
  migrateDatabaseConfig: () =>
    request<ConfigMigrationResult>('/api/admin/database-config/migrate', {
      method: 'POST',
    }),
  updateDatabaseConfig: (key: string, value: string) =>
    request<DatabaseConfigEntry>('/api/admin/database-config', {
      method: 'PUT',
      body: JSON.stringify({ key, value }),
    }),
  btnConfig: () => request<BtnConfigView>('/api/admin/btn'),
  updateBtnConfig: (values: Record<string, string>) =>
    request<BtnConfigView>('/api/admin/btn', {
      method: 'PUT',
      body: JSON.stringify({ values }),
    }),
}

import { request } from './http'
import type {
  Announcement,
  AnnouncementInput,
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
  BanHistoryDeletePreview,
  BanHistoryDeleteResult,
  CompressionPolicyInput,
  ConfigMigrationResult,
  DatabaseConfigEntry,
  DatabaseConfigSummary,
  ManualRule,
  RetentionPolicyInput,
  RuleInput,
  RuleSummary,
  RuntimeConfig,
  StorageOverview,
  SwarmStatisticsStatus,
  TableStorage,
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

  users: (query: AdminUserQuery) =>
    request<AdminPage<AdminUser>>(`/api/admin/users${queryString(query)}`),
  createUser: (body: AdminUserInput) =>
    request<AdminUser>('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateUser: (id: number, body: AdminUserInput) =>
    request<AdminUser>(`/api/admin/users/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  setUserBanned: (id: number, banned: boolean, reason?: string) =>
    request<AdminUser>(`/api/admin/users/${id}/ban`, {
      method: 'POST',
      body: JSON.stringify({ banned, reason }),
    }),
  deleteUser: (id: number) =>
    request<void>(`/api/admin/users/${id}`, {
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

  announcements: () => request<Announcement[]>('/api/admin/announcements'),
  createAnnouncement: (body: AnnouncementInput) =>
    request<Announcement>('/api/admin/announcements', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateAnnouncement: (id: number, body: AnnouncementInput) =>
    request<Announcement>(`/api/admin/announcements/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteAnnouncement: (id: number) =>
    request<void>(`/api/admin/announcements/${id}`, {
      method: 'DELETE',
    }),

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
}

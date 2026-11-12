import { request } from './http'
import type {
  Announcement,
  AnnouncementInput,
  AiModerationAdmin,
  AiModerationSettingsInput,
  AnalyseModule,
  AfdianSettings,
  AfdianSyncResult,
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
  CommunityGroupAdmin,
  CommunityGroupInput,
  CommunityGroupsSettings,
  CommunityGithubTeamValidationResult,
  DatabaseConfigEntry,
  DatabaseConfigSummary,
  FlarumIntegrationSettings,
  FlarumIntegrationUpdateSettings,
  HomepageStatisticsAdminResponse,
  HomepageStatisticsConfigUpdate,
  IpQueryCommentSettings,
  GeoIpCityBlacklistEntry,
  GeoIpCityBlacklistGenerationStatus,
  GeoIpCityBlacklistInput,
  GithubSyncRunResult,
  GithubSyncSettings,
  GithubSyncTarget,
  GithubSyncTargetInput,
  GithubSyncTargetRunResult,
  GithubSyncUpdateSettings,
  MailIngestionStatus,
  MailSettings,
  MailSettingsUpdate,
  MailTestResult,
  ManualRule,
  MemberPlatformConfig,
  MemberPlatformSettings,
  MemberReconciliation,
  MemberTransactionPage,
  ModerationTestResult,
  NavigationItemInput,
  RetentionPolicyInput,
  RuleInput,
  RuleSummary,
  RuntimeConfig,
  StorageOverview,
  StaffMessageInput,
  StaffMessageResult,
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
  mailSettings: () => request<MailSettings>('/api/admin/mail/settings'),
  updateMailSettings: (body: MailSettingsUpdate) =>
    request<MailSettings>('/api/admin/mail/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  testSmtp: (recipient: string) =>
    request<MailTestResult>('/api/admin/mail/settings/test-smtp', {
      method: 'POST',
      body: JSON.stringify({ recipient }),
    }),
  testPop3: () =>
    request<MailTestResult>('/api/admin/mail/settings/test-pop3', {
      method: 'POST',
    }),
  scanMail: () =>
    request<MailIngestionStatus>('/api/admin/mail/settings/scan', {
      method: 'POST',
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
  githubSync: () => request<GithubSyncSettings>('/api/admin/github-sync'),
  updateGithubSyncSettings: (body: GithubSyncUpdateSettings) =>
    request<GithubSyncSettings>('/api/admin/github-sync/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  createGithubSyncTarget: (body: GithubSyncTargetInput) =>
    request<GithubSyncTarget>('/api/admin/github-sync/targets', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateGithubSyncTarget: (id: number, body: GithubSyncTargetInput) =>
    request<GithubSyncTarget>(`/api/admin/github-sync/targets/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteGithubSyncTarget: (id: number) =>
    request<void>(`/api/admin/github-sync/targets/${id}`, {
      method: 'DELETE',
    }),
  runGithubSync: () =>
    request<GithubSyncRunResult>('/api/admin/github-sync/run', {
      method: 'POST',
    }),
  runGithubSyncTarget: (id: number) =>
    request<GithubSyncTargetRunResult>(`/api/admin/github-sync/targets/${id}/run`, {
      method: 'POST',
    }),
  flarumIntegration: () =>
    request<FlarumIntegrationSettings>('/api/admin/flarum-integration'),
  updateFlarumIntegration: (body: FlarumIntegrationUpdateSettings) =>
    request<FlarumIntegrationSettings>('/api/admin/flarum-integration/token', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  sendStaffMessage: (body: StaffMessageInput) =>
    request<StaffMessageResult>('/api/admin/messages', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  communityGroups: () =>
    request<CommunityGroupsSettings>('/api/admin/community-groups'),
  updateCommunityGroupSettings: (body: {
    personalAccessToken?: string
    clearToken?: boolean
    apiBaseUrl: string
  }) =>
    request<CommunityGroupsSettings>('/api/admin/community-groups/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  createCommunityGroup: (body: CommunityGroupInput) =>
    request<CommunityGroupAdmin>('/api/admin/community-groups', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateCommunityGroup: (id: number, body: CommunityGroupInput) =>
    request<CommunityGroupAdmin>(`/api/admin/community-groups/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteCommunityGroup: (id: number) =>
    request<void>(`/api/admin/community-groups/${id}`, {
      method: 'DELETE',
    }),
  validateCommunityGithubTeams: (organization: string, teamSlugs: string[]) =>
    request<CommunityGithubTeamValidationResult>(
      '/api/admin/community-groups/validate-teams',
      {
        method: 'POST',
        body: JSON.stringify({ organization, teamSlugs }),
      },
    ),
  afdianSettings: () => request<AfdianSettings>('/api/admin/afdian'),
  updateAfdianSettings: (body: {
    userId: string
    accessToken?: string
    clearToken?: boolean
    apiBaseUrl: string
    syncEnabled: boolean
    licensePlanIds: string
    licenseDonationAmount: string
    licenseValidYears: number
    licensePrivateKey?: string
    clearLicensePrivateKey?: boolean
  }) =>
    request<AfdianSettings>('/api/admin/afdian/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  syncAfdian: () =>
    request<AfdianSyncResult>('/api/admin/afdian/sync', {
      method: 'POST',
    }),
  memberPlatformSettings: () =>
    request<MemberPlatformSettings>('/api/admin/memberships'),
  updateMemberPlatform: (
    provider: string,
    body: {
      enabled: boolean
      apiUrl: string
      accountId: string
      accessToken?: string
      clearAccessToken?: boolean
      authHeader: string
      authScheme: string
      webhookToken?: string
      clearWebhookToken?: boolean
      currency: string
      exchangeRate: string
    },
  ) =>
    request<MemberPlatformConfig>(`/api/admin/memberships/${provider}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  updateMembershipCurrency: (body: {
    baseCurrency: string
    exchangeRateApiUrl: string
  }) =>
    request<{
      baseCurrency: string
      exchangeRateApiUrl: string
      recalculatedTransactions: number
    }>(
      '/api/admin/memberships/currency',
      {
        method: 'PUT',
        body: JSON.stringify(body),
      },
    ),
  syncMemberPlatforms: () =>
    request<{ synchronizedProviders: number }>('/api/admin/memberships/sync', {
      method: 'POST',
    }),
  memberPlatformTransactions: (params: {
    provider?: string
    keyword?: string
    type?: string
    status?: string
    remotePresent?: boolean
    occurredFrom?: string
    occurredTo?: string
    page?: number
    size?: number
  }) => {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.set(key, String(value))
      }
    })
    return request<MemberTransactionPage>(
      `/api/admin/memberships/transactions?${search.toString()}`,
    )
  },
  memberPlatformReconciliation: (params: {
    provider?: string
    limit?: number
  }) => {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.set(key, String(value))
      }
    })
    return request<MemberReconciliation>(
      `/api/admin/memberships/reconciliation?${search.toString()}`,
    )
  },
  aiModeration: () =>
    request<AiModerationAdmin>('/api/admin/ai-moderation'),
  updateAiModeration: (body: AiModerationSettingsInput) =>
    request<AiModerationAdmin>('/api/admin/ai-moderation/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  updateIpQueryComments: (body: IpQueryCommentSettings) =>
    request<AiModerationAdmin>('/api/admin/ai-moderation/comments', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  testAiModeration: (content: string) =>
    request<ModerationTestResult>('/api/admin/ai-moderation/test', {
      method: 'POST',
      body: JSON.stringify({ content }),
    }),
  requeueAllIpQueryComments: () =>
    request<{ jobsCreated: number }>(
      '/api/admin/ai-moderation/comments/requeue',
      { method: 'POST' },
    ),
  retryAiModerationJobs: (contentType?: string) => {
    const search = contentType
      ? `?contentType=${encodeURIComponent(contentType)}`
      : ''
    return request<{ jobsCreated: number }>(
      `/api/admin/ai-moderation/jobs/retry${search}`,
      { method: 'POST' },
    )
  },
}

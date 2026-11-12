import type {
  AvatarVisibility,
  NavigationIcon,
  PointsVisibility,
  UsernameVisibility,
} from './types'

export interface AdminPage<T> {
  records: T[]
  current: number
  size: number
  total: number
  pages: number
}

export interface SystemSample {
  timestamp: number
  cpuUsage: number
  memoryUsage: number
  memoryUsed: number
  memoryTotal: number
  diskUsage: number
  diskUsed: number
  diskTotal: number
}

export interface JvmInfo {
  availableProcessors: number
  systemLoadAverage: number
  uptimeMillis: number
  heapUsed: number
  heapMax: number
  threadCount: number
  javaVersion: string
}

export interface ModuleMetric {
  key: string
  value: number
}

export interface AdminDashboard {
  current: SystemSample
  history: SystemSample[]
  modules: ModuleMetric[]
  jvm: JvmInfo
  generatedAt: string
}

export type TaskGroup = 'ANALYSIS' | 'STATISTICS' | 'MAINTENANCE' | 'SYNC' | 'STARTUP'
export type TaskKind = 'SCHEDULED' | 'ASYNC'
export type TaskScheduleType =
  | 'CRON'
  | 'FIXED_RATE'
  | 'FIXED_DELAY'
  | 'INTERVAL'
  | 'EVENT'
  | 'STARTUP'
export type TaskRunResult = 'SUCCESS' | 'FAILED' | 'SKIPPED'

export interface BackgroundTaskRun {
  trigger: string
  result: TaskRunResult
  startedAt: string
  finishedAt: string
  durationMs: number
  message: string | null
}

export interface BackgroundTaskStatus {
  id: string
  group: TaskGroup
  kind: TaskKind
  scheduleType: TaskScheduleType
  moduleId: string | null
  moduleEnabled: boolean | null
  manualExecutable: boolean
  nameKey: string
  descriptionKey: string
  schedule: string
  enabled: boolean
  runningCount: number
  nextRunAt: string | null
  lastStartedAt: string | null
  lastFinishedAt: string | null
  currentDurationMs: number
  lastDurationMs: number
  lastTrigger: string | null
  lastResult: TaskRunResult | null
  lastMessage: string | null
  lastError: string | null
  scheduleError: string | null
  recentRuns: BackgroundTaskRun[]
}

export interface BackgroundTaskStatusResponse {
  tasks: BackgroundTaskStatus[]
  generatedAt: string
}

export interface TaskTriggerResponse {
  taskId: string
  started: boolean
}

export interface HomepageStatisticsModuleConfig {
  module: string
  enabled: boolean
  refreshIntervalMinutes: number
}

export interface HomepageStatisticsModuleStatus extends HomepageStatisticsModuleConfig {
  running: boolean
  lastStartedAt: string | null
  lastFinishedAt: string | null
  nextRefreshAt: string | null
  lastDurationMs: number
  trigger: 'SCHEDULED' | 'MANUAL' | null
  triggeredBy: number | null
  lastError: string | null
}

export interface HomepageStatisticsAdminResponse {
  modules: HomepageStatisticsModuleStatus[]
  generatedAt: string
}

export interface HomepageStatisticsConfigUpdate {
  modules: HomepageStatisticsModuleConfig[]
}

export interface AdminUser {
  id: string
  email: string
  nickname: string
  avatarUrl: string
  role: 'user' | 'admin' | 'moderator' | string
  avatarVisibility: AvatarVisibility
  usernameVisibility: UsernameVisibility
  pointsVisibility: PointsVisibility
  registerAt: string
  lastLoginAt: string
  bannedAt: string | null
  bannedReason: string | null
  githubLogin: string | null
  scoreBytes: string
}

export interface AdminUserQuery {
  page: number
  size: number
  keyword?: string
  role?: string
  status?: string
}

export interface AdminUserInput {
  email: string
  nickname: string
  avatarUrl: string
  role: 'user' | 'admin' | 'moderator'
  avatarVisibility: AvatarVisibility
  usernameVisibility: UsernameVisibility
  pointsVisibility: PointsVisibility
}

export interface AdminApplication {
  id: number
  appId: string
  comment: string
  ownerId: number
  ownerNickname: string | null
  ownerEmail: string | null
  createdAt: string
  lastSeenAt: string | null
  bannedAt: string | null
  bannedReason: string | null
  btnBypass: boolean | null
  installationId: string | null
  lastUserAgent: string | null
}

export interface AdminApplicationQuery {
  page: number
  size: number
  keyword?: string
  ownerId?: number
  status?: string
}

export interface AdminApplicationInput {
  ownerId: number
  comment: string
  banned?: boolean
  bannedReason?: string
  btnBypass?: boolean | null
}

export interface AdminApplicationCredentials {
  application: AdminApplication
  appId: string
  appSecret: string
}

export interface ManualRule {
  id: number
  type: RuleType
  category: string
  content: string
}

export type RuleType = 'ip_denylist' | 'peer_id' | 'client_name' | 'port' | 'script'

export interface RuleSummary {
  type: RuleType
  count: number
}

export interface AdminRuleQuery {
  page: number
  size: number
  keyword?: string
  type?: string
}

export interface RuleInput {
  type: RuleType
  category: string
  content: string
}

export interface GeoIpCityBlacklistEntry {
  id: number
  countryCode: string
  province: string
  city: string
  enabled: boolean
  ipv4CidrCount: number
  ipv6CidrCount: number
  lastGeneratedAt: string | null
  createdAt: string
  updatedAt: string
  createdBy: number | null
  updatedBy: number | null
}

export interface GeoIpCityBlacklistInput {
  countryCode: string
  province: string
  city: string
  enabled: boolean
}

export interface GeoIpCityBlacklistGenerationStatus {
  running: boolean
  lastStartedAt: string | null
  lastFinishedAt: string | null
  lastDurationMs: number
  enabledEntries: number
  ipv4CidrCount: number
  ipv6CidrCount: number
  trigger: 'MANUAL' | 'SCHEDULED' | null
  triggeredBy: number | null
  lastError: string | null
}

export interface RuntimeConfig {
  protocolMinVersion: number
  protocolMaxVersion: number
  queryIpPowCaptcha: boolean
  clientDiscoveryEnabled: boolean
  swarmStatisticsEnabled: boolean
  swarmStatisticsRefreshMinutes: number
  swarmStatisticsWindowDays: number
  userContactEmailSubjectTemplate: string
  userContactEmailBodyTemplate: string
}

export interface UserContactEmailAudit {
  id: number
  senderId: string
  senderNickname: string | null
  senderEmail: string | null
  recipientId: string
  recipientNickname: string | null
  recipientEmail: string | null
  subject: string
  senderIp: string | null
  senderUserAgent: string | null
  sentAt: string
}

export interface SwarmStatisticsStatus {
  enabled: boolean
  running: boolean
  refreshIntervalMinutes: number
  windowDays: number
  lastStartedAt: string | null
  lastFinishedAt: string | null
  lastDurationMs: number
  updatedUsers: number
  trigger: 'SCHEDULED' | 'MANUAL' | null
  triggeredBy: number | null
  dataUpdatedAt: string | null
  stale: boolean
  lastError: string | null
}

export type AnnouncementLevel = 'GREEN' | 'YELLOW' | 'BLUE' | 'RED'
export type AnnouncementPlacement = 'GLOBAL_TOP' | 'HOME_HERO'

export interface Announcement {
  id: number
  level: AnnouncementLevel
  placement: AnnouncementPlacement
  titleZh: string
  titleEn: string
  contentZh: string
  contentEn: string
  enabled: boolean
  dismissible: boolean
  sortOrder: number
  startsAt: string | null
  endsAt: string | null
  createdAt: string
  updatedAt: string
  updatedBy: number | null
}

export interface AnnouncementInput {
  level: AnnouncementLevel
  placement: AnnouncementPlacement
  titleZh: string
  titleEn: string
  contentZh: string
  contentEn: string
  enabled: boolean
  dismissible: boolean
  sortOrder: number
  startsAt: string | null
  endsAt: string | null
}

export interface AdminNavigationItem {
  id: string
  builtIn: boolean
  labelZh: string
  labelEn: string
  href: string
  icon: NavigationIcon
  sortOrder: number
  enabled: boolean
  openInNewTab: boolean
  createdAt: string
  updatedAt: string
  updatedBy: number | null
}

export interface NavigationItemInput {
  labelZh: string
  labelEn: string
  href: string
  icon: NavigationIcon
  sortOrder: number
  enabled: boolean
  openInNewTab: boolean
}

export interface AnalyseModuleField {
  field: string
  key: string
  type:
    | 'boolean'
    | 'integer'
    | 'long'
    | 'decimal'
    | 'duration-hours'
    | 'bytes-gib'
    | 'bytes-gb'
    | 'cron'
    | 'text'
  value: string
  restartRequired: boolean
}

export interface AnalyseModule {
  id: string
  enabled: boolean
  fields: AnalyseModuleField[]
}

export interface AnalyseModuleUpdate {
  values: Record<string, string>
}

export interface AnalyseModuleRunResult {
  module: string
  started: boolean
}

export interface StoragePolicy {
  enabled: boolean
  after: string | null
  afterDays: number | null
  scheduleHours: number | null
  nextStart: string | null
}

export interface TableStorage {
  schemaName: string
  tableName: string
  qualifiedName: string
  hypertable: boolean
  compressionEnabled: boolean
  compressionMode: 'NONE' | 'COMPRESSION' | 'COLUMNSTORE'
  timeColumn: string | null
  timeType: string | null
  estimatedRows: number
  chunks: number
  compressedChunks: number
  heapBytes: number
  indexBytes: number
  toastBytes: number
  totalBytes: number
  uncompressedBytes: number
  compressedBytes: number
  segmentBy: string | null
  orderBy: string | null
  compressionPolicy: StoragePolicy
  retentionPolicy: StoragePolicy
}

export interface StorageOverview {
  timescaleVersion: string | null
  tableCount: number
  hypertableCount: number
  totalBytes: number
  uncompressedBytes: number
  compressedBytes: number
  tables: TableStorage[]
}

export interface CompressionPolicyInput {
  enabled: boolean
  compressAfterDays: number
  scheduleHours: number
  segmentBy: string
  orderBy: string
}

export interface RetentionPolicyInput {
  enabled: boolean
  dropAfterDays: number
  scheduleHours: number
}

export interface BanHistoryDeletePreview {
  cutoff: string
  hypertable: boolean
  chunks: number
  compressedChunks: number
  estimatedRows: number | null
  oldestRangeStart: string | null
  newestRangeEnd: string | null
}

export interface BanHistoryDeleteResult {
  cutoff: string
  affectedChunks: number
  deletedRows: number | null
  message: string
}

export type VersionAlertSeverity = 'INFO' | 'WARNING' | 'SECURITY'

export interface VersionAlertRule {
  id: string
  name: string
  enabled: boolean
  product: string
  versionRequirement: string
  severity: VersionAlertSeverity
  subject: string
  body: string
  oncePerUser: boolean
  createdAt: string
  updatedAt: string
  updatedBy: number | null
}

export interface VersionAlertRuleInput {
  name: string
  enabled: boolean
  product: string
  versionRequirement: string
  severity: VersionAlertSeverity
  subject: string
  body: string
  oncePerUser: boolean
}

export interface VersionAlertMatch {
  matched: boolean
  product: string
  version: string | null
  versionRequirement: string
  userAgent: string
}

export interface VersionAlertDelivery {
  id: string
  ruleId: string
  ruleName: string
  userId: number | null
  email: string
  nickname: string | null
  product: string
  version: string
  userAgent: string
  status: 'SENT' | 'FAILED'
  error: string | null
  trigger: 'AUTOMATIC' | 'TEST'
  sentAt: string
}

export interface DatabaseConfigEntry {
  key: string
  group: string
  value: string | null
  valueType: 'string' | 'boolean' | 'number'
  sensitive: boolean
  hotReload: boolean
  environmentOverride: boolean
  source: 'yaml' | 'database' | 'redis-migration' | string
  updatedAt: string
  updatedBy: number | null
}

export interface DatabaseConfigSummary {
  total: number
  yamlEntries: number
  databaseEntries: number
  sensitiveEntries: number
  hotReloadEntries: number
  groups: string[]
  lastUpdatedAt: string | null
}

export interface ConfigMigrationResult {
  scanned: number
  inserted: number
  updated: number
  skipped: number
  completedAt: string
}

export interface BtnConfigField {
  key: string
  section: string
  field: string
  valueType:
    | 'boolean'
    | 'integer'
    | 'decimal'
    | 'endpoint'
    | 'url'
    | 'algorithm'
    | 'text'
    | 'list'
    | 'time_zone'
    | 'email'
    | 'secret'
    | 'cron'
  value: string | null
  defaultValue: string
  min: number | null
  max: number | null
  sensitive: boolean
  valueConfigured: boolean
  hotReload: boolean
  environmentOverride: boolean
  source: string
  updatedAt: string | null
}

export interface BtnConfigView {
  fields: BtnConfigField[]
  updatedAt: string | null
}

export interface GithubSyncSource {
  id: string
  kind: 'DENY' | 'ALLOW'
  defaultFileName: string
  available: boolean
}

export interface GithubSyncTarget {
  id: number
  name: string
  repositoryOwner: string
  repositoryName: string
  branchName: string
  filePath: string
  enabled: boolean
  sourceIds: string[]
  createdAt: string
  updatedAt: string
  lastAttemptAt: string | null
  lastSuccessAt: string | null
  lastError: string | null
  lastCommitSha: string | null
}

export interface GithubSyncTargetInput {
  name: string
  repositoryOwner: string
  repositoryName: string
  branchName: string
  filePath: string
  enabled: boolean
  sourceIds: string[]
}

export interface GithubSyncSettings {
  schedule: string
  tokenConfigured: boolean
  sources: GithubSyncSource[]
  targets: GithubSyncTarget[]
}

export interface GithubSyncUpdateSettings {
  schedule: string
  personalAccessToken?: string
  clearToken?: boolean
}

export interface GithubSyncTargetRunResult {
  targetId: number
  targetName: string
  success: boolean
  message: string
  commitSha: string | null
}

export interface GithubSyncRunResult {
  total: number
  succeeded: number
  failed: number
  results: GithubSyncTargetRunResult[]
}

export interface StaffMessageInput {
  subject: string
  content: string
  category: import('./types').NotificationCategory
  recipientId: number | null
  systemIdentity: boolean
  forceEmail: boolean
}

export interface StaffMessageResult {
  recipients: number
  dispatchId: string
  remainingToday: number
}

export type CommunityGroupType = 'GITHUB' | 'LINK'

export interface CommunityGithubTeamInput {
  slug: string
  name: string
}

export interface CommunityGroupInput {
  slug: string
  type: CommunityGroupType
  titleZh: string
  titleEn: string
  descriptionZh: string
  descriptionEn: string
  url: string
  githubOrganization: string
  enabled: boolean
  sortOrder: number
  teams: CommunityGithubTeamInput[]
}

export interface CommunityGroupAdmin {
  id: number
  slug: string
  type: CommunityGroupType
  titleZh: string
  titleEn: string
  descriptionZh: string
  descriptionEn: string
  url: string | null
  githubOrganization: string | null
  enabled: boolean
  sortOrder: number
  teams: CommunityGithubTeamInput[]
  createdAt: string
  updatedAt: string
  updatedBy: number | null
}

export interface CommunityGroupsSettings {
  tokenConfigured: boolean
  apiBaseUrl: string
  groups: CommunityGroupAdmin[]
}

export interface CommunityGithubTeamValidation {
  id: number
  slug: string
  name: string
}

export interface CommunityGithubTeamValidationResult {
  organization: string
  teams: CommunityGithubTeamValidation[]
}

export interface AfdianAdminDonation {
  outTradeNo: string
  afdianUserId: string
  afdianUserName: string | null
  planId: string | null
  planTitle: string | null
  totalAmount: string
  showAmount: string
  status: number
  remark: string | null
  createdAt: string | null
  syncedAt: string
}

export interface AfdianAdminBinding {
  btnUserId: number
  btnNickname: string
  afdianUserId: string
  afdianName: string | null
  afdianAvatar: string | null
  boundOrderNo: string
  boundAt: string
  lastSyncedAt: string | null
  lastError: string | null
  totalAmount: string
  donationCount: number
}

export interface AfdianSettings {
  userId: string
  tokenConfigured: boolean
  configured: boolean
  apiBaseUrl: string
  syncEnabled: boolean
  licensePlanIds: string
  licenseDonationAmount: string
  licenseValidYears: number
  licensePrivateKeyConfigured: boolean
  baseCurrency: string
  lastSyncedAt: string | null
  bindings: AfdianAdminBinding[]
  recentDonations: AfdianAdminDonation[]
  recentLicenses: AfdianAdminLicense[]
}

export interface AfdianAdminLicense {
  licenseId: string
  btnUserId: number
  btnNickname: string
  afdianUserId: string
  sourceType: string
  sourceOrderNo: string | null
  planId: string | null
  consumedAmount: string
  issuedAt: string
  expireAt: string
  revokedAt: string | null
  revokedReason: string | null
}

export interface AfdianSyncResult {
  success: boolean
  pages: number
  ordersSeen: number
  completedOrders: number
  bindingsUpdated: number
  message: string
}

export type MemberPlatformCode =
  | 'PATREON'
  | 'KOFI'
  | 'MIANBAODUO'
  | 'LIANDONG'

export interface MemberPlatformConfig {
  provider: MemberPlatformCode
  enabled: boolean
  apiUrl: string | null
  accountId: string | null
  accessTokenConfigured: boolean
  authHeader: string
  authScheme: string
  webhookTokenConfigured: boolean
  currency: string
  exchangeRate: string
  bindingCount: number
  transactionCount: number
  lastSyncedAt: string | null
}

export interface MemberPlatformSettings {
  providers: MemberPlatformConfig[]
  baseCurrency: string
  exchangeRateApiUrl: string
}

export interface MemberTransactionAdmin {
  id: number
  provider: MemberPlatformCode | 'AFDIAN'
  transactionId: string
  externalUserId: string
  externalUserName: string | null
  type: 'PRODUCT' | 'DONATION' | string
  sku: string | null
  productName: string | null
  amount: string
  currency: string
  amountCny: string
  amountBase: string
  baseCurrency: string
  status: string
  occurredAt: string | null
  remotePresent: boolean
  licenseConsumed: boolean
  firstSeenAt: string | null
  lastReconciledAt: string | null
  lastChangedAt: string | null
}

export interface MemberTransactionPage {
  items: MemberTransactionAdmin[]
  total: number
  page: number
  size: number
}

export interface MemberSyncRun {
  id: number
  provider: MemberPlatformCode | 'AFDIAN'
  status: 'RUNNING' | 'COMPLETED' | 'INCOMPLETE' | 'FAILED'
  startedAt: string
  completedAt: string | null
  pagesFetched: number
  recordsSeen: number
  insertedCount: number
  updatedCount: number
  unchangedCount: number
  missingCount: number
  errorMessage: string | null
}

export interface MemberAuditEvent {
  id: number
  syncRunId: number
  provider: MemberPlatformCode | 'AFDIAN'
  transactionId: string
  eventType: 'CREATED' | 'UPDATED' | 'MISSING_REMOTE' | 'RESTORED_REMOTE'
  changedFields: string[]
  createdAt: string
}

export interface MemberReconciliation {
  runs: MemberSyncRun[]
  events: MemberAuditEvent[]
}

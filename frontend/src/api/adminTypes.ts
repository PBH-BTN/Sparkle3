import type { NavigationIcon, PrivacyLevel } from './types'

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

export interface AdminUser {
  id: string
  email: string
  nickname: string
  avatarUrl: string
  role: 'user' | 'admin' | string
  privacyLevel: PrivacyLevel['name']
  registerAt: string
  lastLoginAt: string
  bannedAt: string | null
  bannedReason: string | null
  githubLogin: string | null
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
  role: 'user' | 'admin'
  privacyLevel: PrivacyLevel['name']
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

export interface RuntimeConfig {
  protocolMinVersion: number
  protocolMaxVersion: number
  queryIpPowCaptcha: boolean
  clientDiscoveryEnabled: boolean
  swarmStatisticsEnabled: boolean
  swarmStatisticsRefreshMinutes: number
  swarmStatisticsWindowDays: number
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

export interface Announcement {
  id: number
  level: AnnouncementLevel
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

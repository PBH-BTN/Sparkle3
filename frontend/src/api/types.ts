export type AvatarVisibility = 'ORIGINAL' | 'BLURRED' | 'HIDDEN'
export type UsernameVisibility = 'FULL' | 'BLURRED'
export type PointsVisibility = 'SHOW' | 'HIDDEN'

export interface PrivacySettings {
  avatarVisibility: AvatarVisibility
  usernameVisibility: UsernameVisibility
  pointsVisibility: PointsVisibility
}

export interface SessionUser {
  id: string
  nickname: string
  email: string
  avatarUrl: string
  registerAt: string | null
  bannedAt: string | null
  bannedReason: string | null
  role: string
  admin: boolean
  moderator: boolean
  privacy: PrivacySettings
  nicknameModerationStatus?: ModerationStatus | null
  avatarModerationStatus?: ModerationStatus | null
  nicknameModerationReason?: string | null
  avatarModerationReason?: string | null
}

export type ModerationStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'FAILED'

export interface TrafficSummary {
  sentTrafficSelfReport: number
  receivedTrafficSelfReport: number
  sentTrafficOtherAck: number
  receivedTrafficOtherAck: number
  lastUpdateAt: string | null
  refreshIntervalMinutes: number
  windowDays: number
}

export type NavigationIcon =
  | 'home'
  | 'apps'
  | 'ban'
  | 'search'
  | 'swarm'
  | 'rules'
  | 'ranking'
  | 'link'
  | 'file'
  | 'dashboard'

export interface NavigationItem {
  id: string
  labelZh: string
  labelEn: string
  href: string
  icon: NavigationIcon
  openInNewTab: boolean
}

export interface SessionResponse {
  authenticated: boolean
  user: SessionUser | null
  traffic: TrafficSummary | null
  navigation: NavigationItem[]
}

export type AnnouncementPlacement = 'GLOBAL_TOP' | 'HOME_HERO'

export interface Announcement {
  id: number
  level: 'GREEN' | 'YELLOW' | 'BLUE' | 'RED'
  placement: AnnouncementPlacement
  titleZh: string
  titleEn: string
  contentZh: string
  contentEn: string
  dismissible: boolean
  startsAt: string | null
  endsAt: string | null
  updatedAt: string
}

export interface HomeStatWindows {
  allTime: number
  last30Days: number
  last14Days: number
  last7Days: number
  last24Hours: number
}

export interface HomeStats {
  banHistory: HomeStatWindows
  userApps: HomeStatWindows
  swarmTracker: HomeStatWindows
  bittorrentTrafficEnabled: boolean
}

export interface ChartDatum {
  name: string
  value: number
}

export interface ChartSeries {
  name: string
  values: number[]
}

export interface DistributionChart {
  data: ChartDatum[]
}

export interface StackedAreaChart {
  categories: string[]
  series: ChartSeries[]
}

export type StatisticsSource = 'ban' | 'all'

export interface HomeNetworkStatisticsResponse {
  days: number
  source: StatisticsSource
  peerIds: DistributionChart
  clientNames: DistributionChart
  clientNameTrend: StackedAreaChart
  geoCities: StackedAreaChart
  ipVersions: StackedAreaChart
  generatedAt: string
}

export interface HomeCompanionStatisticsResponse {
  companionVersions: DistributionChart
  companionIpVersions: DistributionChart
  companionGeoIp: DistributionChart
  generatedAt: string
}

export interface TrafficTotals {
  uploadBytes: string
  downloadBytes: string
  transferBytes: string
}

export interface TrafficCityDatum {
  name: string
  bytes: string
}

export interface TrafficDistribution {
  data: TrafficCityDatum[]
  totalBytes: string
}

export interface HomeBittorrentTrafficResponse {
  days: number
  totals: TrafficTotals
  topUploadCities: TrafficDistribution
  topDownloadCities: TrafficDistribution
  generatedAt: string
}

export type RulePreviewType =
  | 'ip_denylist'
  | 'ip_allowlist'
  | 'peer_id'
  | 'client_name'
  | 'port'
  | 'script'

export interface RulePreviewEntry {
  source: string
  category: string | null
  content: string
}

export interface RulePreviewSection {
  type: RulePreviewType
  entries: RulePreviewEntry[]
}

export interface RulePreviewResponse {
  sections: RulePreviewSection[]
  generatedAt: string
}

export interface UserRelation {
  platformUserId: string
  platform: string
  platformUserEmail: string
  platformUserLogin: string
}

export interface UserProfileResponse {
  user: SessionUser
  relation: UserRelation | null
  scoreBytesDisplay: string
  scoreBytesRaw: string
}

export interface PointCategoryPoints {
  category: string
  pointsRaw: string
  dailyCapRaw: string | null
  enabled: boolean
}

export interface FixedPointsSummary {
  pointsTodayRaw: string
  cappedPointsTodayRaw: string
  dailyCapRaw: string
  averagePerHourRaw: number
  categories: PointCategoryPoints[]
}

export interface DynamicPointsSummary {
  pointsTodayRaw: string
  averagePerHourRaw: number
  seedUploadPointsTodayRaw: string
  downloadPointsTodayRaw: string
  hotPeerPointsTodayRaw: string
  seedUploadEnabled: boolean
  downloadEnabled: boolean
  hotPeerEnabled: boolean
}

export interface PointFeatureFlags {
  dynamicPoints: boolean
  swarmPeerInsights: boolean
  banPeerInsights: boolean
}

export interface PointsOverview {
  balanceRaw: string
  fixed: FixedPointsSummary
  dynamic: DynamicPointsSummary | null
  features: PointFeatureFlags
  generatedAt: string
}

export interface PointsBreakdownEntry {
  category: string
  // ONE_TIME is retained for ledgers created before its removal.
  component: 'FIXED' | 'ONE_TIME' | 'DYNAMIC'
  requestedPoints: number
  awardedPoints: number
  itemCount: number
  rawValue: number
}

export interface PointsLogDetails {
  breakdown?: PointsBreakdownEntry[]
  requestedTotal?: number
  reason?: string
  operatorId?: number
  deltaPoints?: number
  balanceBefore?: number
  balanceAfter?: number
}

export interface PointsLogEntry {
  id: number
  occurredAt: string
  eventType: string
  pointsRaw: string
  fixedPointsRaw: string
  dynamicPointsRaw: string
  manualPointsRaw: string
  details: PointsLogDetails
}

export interface PointsLogPage {
  records: PointsLogEntry[]
  current: number
  size: number
  total: number
  pages: number
}

export interface PeerVersionCount {
  name: string
  peers: number
}

export interface PeerTorrentSeries {
  torrentId: string
  values: number[]
}

export interface SwarmPeerInsightsResponse {
  enabled: boolean
  days: number
  ipVersions: PeerVersionCount[]
  categories: string[]
  series: PeerTorrentSeries[]
  generatedAt: string
}

export interface BanPeerInsightsResponse {
  enabled: boolean
  days: number
  ipVersions: PeerVersionCount[]
  generatedAt: string
}

export interface PublicUserProfileResponse {
  id: string
  nickname: string | null
  avatarUrl: string | null
  privacy: PrivacySettings
  scoreBytesDisplay: string | null
  scoreBytesRaw: string | null
  anonymous: boolean
  banned: boolean
  self: boolean
  canSendMessage: boolean
  canSendEmail: boolean
}

export interface ContactEmailRequest {
  subject: string
  content: string
}

export interface ContactEmailResult {
  sent: boolean
  remainingToday: number
}

export interface PrivacyResponse {
  current: PrivacySettings
}

export type NotificationCategory =
  | 'LEGAL_COMPLIANCE'
  | 'ACCOUNT_SECURITY'
  | 'CLIENT_SECURITY'
  | 'MAJOR_ANNOUNCEMENT'
  | 'DAILY_ANNOUNCEMENT'
  | 'CHANGELOG'
  | 'DIRECT_MESSAGE'
  | 'SOCIAL_ALERT'
  | 'GENERAL_NOTICE'

export interface NotificationPreference {
  category: NotificationCategory
  inboxEnabled: boolean
  emailEnabled: boolean
  locked: boolean
}

export interface NotificationPreferencesResponse {
  preferences: NotificationPreference[]
}

export interface InboxMessage {
  id: number
  senderDisplayName: string
  senderKind: 'USER' | 'SYSTEM' | string
  category: NotificationCategory
  subject: string
  content: string
  createdAt: string
  readAt: string | null
  read: boolean
  broadcast: boolean
  emailRequested: boolean
  emailForced: boolean
  emailStatus: 'NONE' | 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED' | string
}

export interface InboxPage extends PageResponse<InboxMessage> {}

export interface SendMessageResult {
  sent: boolean
  remainingToday: number
}

export interface UnreadMessageCount {
  count: number
}

export type CommunityGroupType = 'GITHUB' | 'LINK'

export interface CommunityGroupTeam {
  slug: string
  name: string
}

export interface CommunityGroup {
  id: number
  slug: string
  type: CommunityGroupType
  titleZh: string
  titleEn: string
  descriptionZh: string
  descriptionEn: string
  url: string | null
  githubOrganization: string | null
  teams: CommunityGroupTeam[]
  githubLinked: boolean
  githubLogin: string | null
  githubConfigured: boolean
}

export type GithubMembershipStatus =
  | 'NOT_LINKED'
  | 'NOT_MEMBER'
  | 'PENDING'
  | 'PARTIAL'
  | 'ACTIVE'
  | 'UNAVAILABLE'
  | 'ERROR'

export interface GithubTeamStatus extends CommunityGroupTeam {
  state: 'ACTIVE' | 'PENDING' | 'NONE' | string
}

export interface GithubCommunityStatus {
  groupId: number
  status: GithubMembershipStatus
  organizationState: string
  teams: GithubTeamStatus[]
  message: string | null
  checkedAt: string
}

export interface GithubCommunityJoinResult {
  status: string
  message: string
}

export interface AfdianDonation {
  outTradeNo: string
  planId: string | null
  planTitle: string | null
  type: 'PRODUCT' | 'DONATION' | string
  amount: string
  status: string
  remotePresent: boolean
  remark: string | null
  createdAt: string | null
}

export interface PbhLicense {
  licenseId: string
  sourceType: 'PRODUCT' | 'DONATION' | string
  sourceOrderNo: string | null
  planId: string | null
  consumedAmount: string
  licenseKey: string
  issuedAt: string
  startAt: string
  expireAt: string
  revokedAt: string | null
  revokedReason: string | null
}

export interface PbhLicenseAvailability {
  configured: boolean
  availableProductLicenses: number
  availableDonationLicenses: number
  totalAvailable: number
  donationLicenseAmount: string
  baseCurrency: string
  validYears: number
  licenses: PbhLicense[]
}

export type MemberPlatformCode =
  | 'AFDIAN'
  | 'PATREON'
  | 'KOFI'
  | 'MIANBAODUO'
  | 'LIANDONG'

export interface MemberTransaction {
  transactionId: string
  type: 'PRODUCT' | 'DONATION' | string
  sku: string | null
  productName: string | null
  amount: string
  currency: string
  amountCny: string
  amountBase: string
  baseCurrency: string
  status: string
  remotePresent: boolean
  occurredAt: string | null
}

export interface MemberProviderBinding {
  configured: boolean
  bound: boolean
  provider: MemberPlatformCode
  externalUserId: string | null
  externalUserName: string | null
  externalUserAvatar: string | null
  proof: string | null
  boundAt: string | null
  lastSyncedAt: string | null
  lastError: string | null
  totalCny: string
  totalBase: string
  baseCurrency: string
  donationCount: number
  productCount: number
  transactions: MemberTransaction[]
}

export interface AfdianBinding {
  configured: boolean
  bound: boolean
  afdianUserId: string | null
  afdianName: string | null
  afdianAvatar: string | null
  boundAt: string | null
  lastSyncedAt: string | null
  totalAmount: string
  transactionCount: number
  donations: AfdianDonation[]
}

export interface FeatureAvailability {
  afdianConfigured: boolean
  communityGroupsAvailable: boolean
  memberPlatformsConfigured: boolean
}

export interface AccountDeletionStatus {
  state: 'NONE' | 'PENDING' | 'CANCELLED' | 'COMPLETED'
  requestedAt: string | null
  coolingEndsAt: string | null
  completedAt: string | null
  cancellationReason: string | null
}

export interface AccountDeletionReauthenticationResponse {
  redirectUrl: string
}

export interface PendingGithubRegistration {
  githubLogin: string
  nickname: string
  avatarUrl: string
  email: string
}

export interface GithubRegistrationResult {
  registered: boolean
  userId: string
}

export interface UserApplication {
  id: number
  appId: string
  comment: string
  createdAt: string | null
  lastSeenAt: string | null
  bannedAt: string | null
  bannedReason: string | null
  btnBypass: boolean | null
  exemptIps: string[]
}

export interface ApplicationCredentials {
  application: UserApplication
  appId: string
  appSecret: string
}

export interface PageResponse<T> {
  records: T[]
  current: number
  size: number
  total: number
  pages: number
}

export interface BanHistoryRecord {
  id: number
  insertTime: string
  userappId: number | null
  torrentId: number | null
  peerIp: string | null
  peerPort: number | null
  peerId: string | null
  peerClientName: string | null
  peerProgress: number | null
  peerFlags: string | null
  peerGeoip: {
    city?: {
      name?: string
      cnProvince?: string
      cnCity?: string
    }
    country?: {
      name?: string
      iso?: string
    }
    network?: {
      isp?: string
    }
  } | null
  reporterProgress: number | null
  toPeerTraffic: number | null
  fromPeerTraffic: number | null
  moduleName: string | null
  rule: string | null
  description: string | null
  structuredData: Record<string, unknown> | null
}

export interface BanHistoryQuery {
  insertTimeStart?: string
  insertTimeEnd?: string
  torrentId?: number
  peerIp?: string
  peerPort?: number
  peerId?: string
  peerClientName?: string
  moduleName?: string
  rule?: string
  description?: string
  page: number
  size: number
  sortBy: string
  sortOrder: 'asc' | 'desc'
}

export interface FilterOptions {
  moduleNames: string[]
}

export interface ClientDiscoveryRecord {
  hash: number
  peerId: string | null
  peerClientName: string | null
  foundAt: string
  foundUserappsId: number | null
  clientType: string | null
  clientSemver: string | null
}

export interface ClientDiscoveryQuery {
  peerId?: string
  peerClientName?: string
  clientType?: string
  clientSemver?: string
  page: number
  size: number
  sortBy: string
  sortOrder: 'asc' | 'desc'
}

export interface ClientDiscoveryOptions {
  clientTypes: string[]
}

export interface SwarmTrackerRecord {
  id: number
  torrentId: number
  peerIp: string | null
  peerPort: number | null
  peerId: string | null
  peerClientName: string | null
  peerProgress: number | null
  fromPeerTraffic: number | null
  toPeerTraffic: number | null
  flags: string | null
  firstTimeSeen: string
  lastTimeSeen: string
  userProgress: number | null
}

export interface SwarmTrackerQuery {
  infoHash?: string
  peerIp?: string
  peerPort?: number
  peerId?: string
  peerClientName?: string
  peerProgress?: number
  fromPeerTraffic?: number
  toPeerTraffic?: number
  flags?: string
  userProgress?: number
  page: number
  size: number
  sortBy: string
  sortOrder: 'asc' | 'desc'
}

export interface RankingEntry {
  rank: number
  userId: number
  displayName: string | null
  anonymous: boolean
  avatarUrl: string | null
  avatarAvailable: boolean
  banned: boolean
  sentTrafficSelfReport: number
  receivedTrafficSelfReport: number
  sentTrafficOtherAck: number
  receivedTrafficOtherAck: number
}

export interface RankingResponse {
  self: RankingEntry | null
  entries: RankingEntry[]
}

export interface PointsRankingEntry {
  rank: number
  userId: number
  displayName: string | null
  anonymous: boolean
  avatarUrl: string | null
  avatarAvailable: boolean
  banned: boolean
  scoreBytes: string
}

export interface PointsRankingResponse {
  self: PointsRankingEntry | null
  entries: PointsRankingEntry[]
}

export interface IpQueryRecord {
  id: number
  populate_time: number
  torrent: string
  peer_ip: string
  peer_port: number
  peer_id: string
  peer_client_name: string
  peer_progress: number | null
  peer_flags: string
  reporter_progress: number | null
  to_peer_traffic: number
  from_peer_traffic: number
  module_name: string
  rule: string
  description: string
  structured_data: Record<string, unknown> | null
}

export interface IpSwarmRecord {
  id: number
  torrent: string
  peer_ip: string
  peer_port: number
  peer_id: string
  peer_client_name: string
  peer_progress: number | null
  from_peer_traffic: number
  to_peer_traffic: number
  from_peer_traffic_offset: number
  to_peer_traffic_offset: number
  flags: string
  first_time_seen: number
  last_time_seen: number
  user_progress: number
}

export interface IpQueryResult {
  color: string
  labels: string[]
  bans: {
    duration: number
    total: number
    records: IpQueryRecord[]
  } | null
  swarms: {
    duration: number
    total: number
    records: IpSwarmRecord[]
    concurrent_download_torrents_count: number
    concurrent_seeding_torrents_count: number
  } | null
  traffic: {
    duration: number
    to_peer_traffic: number
    from_peer_traffic: number
    share_ratio: number
  } | null
  torrents: {
    duration: number
    count: number
  } | null
}

export interface IpGeoView {
  country: string | null
  countryIso: string | null
  province: string | null
  city: string | null
  district: string | null
  asn: string | null
  asnOrganization: string | null
  asnNetwork: string | null
  isp: string | null
  networkType: string | null
}

export interface IpCommentAccess {
  visible: boolean
  publishingEnabled: boolean
  moderationEnabled: boolean
  maxLength: number
  hourlyLimit: number
}

export interface IpQueryLookupResponse {
  input: string
  normalizedAddress: string
  canonicalCidr: string
  networkAddress: string
  rangeStart: string
  rangeEnd: string
  ipVersion: number
  prefixLength: number
  totalAddresses: string
  expandedAddress: string
  binaryAddress: string
  decimalAddress: string | null
  reverseDnsName: string
  flags: string[]
  globallyRoutable: boolean
  defaultCommentScope: string
  defaultCommentPrefix: number
  commentScopes: IpCommentScopeOption[]
  geoStart: IpGeoView | null
  geoEnd: IpGeoView | null
  activity: IpQueryResult
  commentAccess: IpCommentAccess
  generatedAt: string
}

export interface IpCommentScopeOption {
  prefix: number
  scope: string
  selected: boolean
}

export interface IpCommentRevision {
  revision: number
  content: string
  moderationStatus: ModerationStatus
  moderationReason: string | null
  editorIp: string | null
  editorUserAgent: string | null
  createdAt: string
}

export interface IpComment {
  id: number
  parentId: number | null
  network: string
  authorId: string | null
  authorNickname: string | null
  authorAvatarUrl: string | null
  authorAnonymous: boolean
  authorModerationHidden: boolean
  content: string
  revision: number
  edited: boolean
  moderationStatus: ModerationStatus
  moderationReason: string | null
  authorIp: string | null
  authorUserAgent: string | null
  createdAt: string
  updatedAt: string
  self: boolean
  canEdit: boolean
  canDelete: boolean
  history: IpCommentRevision[]
}

export interface IpCommentPage {
  records: IpComment[]
  current: number
  size: number
  total: number
  pages: number
  scope: string
  access: IpCommentAccess
}

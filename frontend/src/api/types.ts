export interface PrivacyLevel {
  name: 'DISABLED' | 'LOW' | 'MEDIUM' | 'HIGH'
  level: number
  allowBlurredUsername: boolean
  allowFullUsername: boolean
  allowBlurredAvatar: boolean
  allowOriginalAvatar: boolean
}

export interface SessionUser {
  id: string
  nickname: string
  email: string
  avatarUrl: string
  registerAt: string | null
  bannedAt: string | null
  bannedReason: string | null
  admin: boolean
  privacyLevel: PrivacyLevel
}

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
  scoreBytesRaw: number
}

export interface PublicUserProfileResponse {
  id: string
  nickname: string | null
  avatarUrl: string | null
  privacyLevel: PrivacyLevel['name']
  anonymous: boolean
  banned: boolean
  self: boolean
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
  current: PrivacyLevel
  levels: PrivacyLevel[]
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
  statusText: string | null
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

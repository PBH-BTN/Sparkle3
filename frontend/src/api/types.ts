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

export interface Announcement {
  id: number
  level: 'GREEN' | 'YELLOW' | 'BLUE' | 'RED'
  titleZh: string
  titleEn: string
  contentZh: string
  contentEn: string
  dismissible: boolean
  startsAt: string | null
  endsAt: string | null
  updatedAt: string
}

export interface HomeStats {
  banHistory: Record<string, number>
  userApps: Record<string, number>
  swarmTracker: Record<string, number>
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
    countryIso?: string
    cityCnProvince?: string
    cityCnCity?: string
    isp?: string
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

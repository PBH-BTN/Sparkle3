import { request } from './http'
import type {
  Announcement,
  ApplicationCredentials,
  BanHistoryQuery,
  BanHistoryRecord,
  ClientDiscoveryOptions,
  ClientDiscoveryQuery,
  ClientDiscoveryRecord,
  FilterOptions,
  HomeStats,
  IpQueryResult,
  PageResponse,
  PrivacyLevel,
  PrivacyResponse,
  RankingResponse,
  SessionResponse,
  SwarmTrackerQuery,
  SwarmTrackerRecord,
  UserApplication,
  UserProfileResponse,
} from './types'

function postQuery<T>(url: string, body: unknown) {
  return request<PageResponse<T>>(url, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export const uiApi = {
  session: () => request<SessionResponse>('/api/ui/session'),
  home: () => request<HomeStats>('/api/ui/home'),
  profile: () => request<UserProfileResponse>('/api/ui/user/profile'),
  privacy: () => request<PrivacyResponse>('/api/ui/user/privacy'),
  updatePrivacy: (level: PrivacyLevel['name']) =>
    request<PrivacyResponse>('/api/ui/user/privacy', {
      method: 'PUT',
      body: JSON.stringify({ level }),
    }),
  userApps: () => request<UserApplication[]>('/api/ui/userapps'),
  createUserApp: (comment: string) =>
    request<ApplicationCredentials>('/api/ui/userapps', {
      method: 'POST',
      body: JSON.stringify({ comment }),
    }),
  resetUserAppSecret: (appId: string) =>
    request<ApplicationCredentials>(`/api/ui/userapps/${encodeURIComponent(appId)}/reset-secret`, {
      method: 'POST',
    }),
  deleteUserApp: (appId: string) =>
    request<void>(`/api/ui/userapps/${encodeURIComponent(appId)}`, {
      method: 'DELETE',
    }),
  ranking: () => request<RankingResponse>('/api/ui/ranking'),
  announcements: () => request<Announcement[]>('/api/ui/announcements'),
  ipQueryWidget: (search: string) => request<IpQueryResult>(`/ping/queryIp/widget/data${search}`),
}

export const historyApi = {
  queryBanHistory: (body: BanHistoryQuery) =>
    postQuery<BanHistoryRecord>('/api/banhistory/query', body),
  banHistoryOptions: () => request<FilterOptions>('/api/banhistory/options'),
  queryClientDiscovery: (body: ClientDiscoveryQuery) =>
    postQuery<ClientDiscoveryRecord>('/api/clientdiscovery/query', body),
  clientDiscoveryOptions: () => request<ClientDiscoveryOptions>('/api/clientdiscovery/options'),
  querySwarmTracker: (body: SwarmTrackerQuery) =>
    postQuery<SwarmTrackerRecord>('/api/swarmtracker/query', body),
}

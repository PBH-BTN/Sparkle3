import { request } from './http'
import type {
  AccountDeletionReauthenticationResponse,
  AccountDeletionStatus,
  Announcement,
  ApplicationCredentials,
  BanHistoryQuery,
  BanHistoryRecord,
  ClientDiscoveryOptions,
  ClientDiscoveryQuery,
  ClientDiscoveryRecord,
  ContactEmailRequest,
  ContactEmailResult,
  FilterOptions,
  GithubRegistrationResult,
  HomeBittorrentTrafficResponse,
  HomeCompanionStatisticsResponse,
  HomeNetworkStatisticsResponse,
  HomeStats,
  InboxPage,
  IpQueryResult,
  NotificationPreferencesResponse,
  NotificationPreference,
  PageResponse,
  PointsLogPage,
  PointsOverview,
  PointsRankingResponse,
  PrivacySettings,
  PrivacyResponse,
  PendingGithubRegistration,
  PublicUserProfileResponse,
  RankingResponse,
  RulePreviewResponse,
  SessionResponse,
  SendMessageResult,
  SwarmTrackerQuery,
  SwarmTrackerRecord,
  SwarmPeerInsightsResponse,
  BanPeerInsightsResponse,
  StatisticsSource,
  UserApplication,
  UserProfileResponse,
  UnreadMessageCount,
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
  homeNetworkStatistics: (days: number, source: StatisticsSource) =>
    request<HomeNetworkStatisticsResponse>(
      `/api/ui/statistics/home?days=${days}&source=${encodeURIComponent(source)}`,
    ),
  homeCompanionStatistics: () =>
    request<HomeCompanionStatisticsResponse>('/api/ui/statistics/companion'),
  homeBittorrentTrafficStatistics: (days: number) =>
    request<HomeBittorrentTrafficResponse>(
      `/api/ui/statistics/bittorrent-traffic?days=${days}`,
    ),
  rulePreview: () => request<RulePreviewResponse>('/api/ui/rules/preview'),
  profile: () => request<UserProfileResponse>('/api/ui/user/profile'),
  pointsOverview: () => request<PointsOverview>('/api/ui/points/overview'),
  pointsLogs: (page: number, size: number) =>
    request<PointsLogPage>(`/api/ui/points/logs?page=${page}&size=${size}`),
  publicProfile: (uid: string) =>
    request<PublicUserProfileResponse>(`/api/ui/user/profile/${encodeURIComponent(uid)}`),
  sendContactEmail: (uid: string, body: ContactEmailRequest) =>
    request<ContactEmailResult>(
      `/api/ui/user/profile/${encodeURIComponent(uid)}/email`,
      {
        method: 'POST',
        body: JSON.stringify(body),
      },
    ),
  sendMessage: (uid: string, subject: string, content: string) =>
    request<SendMessageResult>(
      `/api/ui/user/profile/${encodeURIComponent(uid)}/message`,
      {
        method: 'POST',
        body: JSON.stringify({ subject, content }),
      },
    ),
  privacy: () => request<PrivacyResponse>('/api/ui/user/privacy'),
  updatePrivacy: (body: PrivacySettings) =>
    request<PrivacyResponse>('/api/ui/user/privacy', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  notifications: () =>
    request<NotificationPreferencesResponse>('/api/ui/notifications/preferences'),
  updateNotifications: (
    preferences: Array<
      Pick<NotificationPreference, 'category' | 'inboxEnabled' | 'emailEnabled'>
    >,
  ) =>
    request<NotificationPreferencesResponse>('/api/ui/notifications/preferences', {
      method: 'PUT',
      body: JSON.stringify({ preferences }),
    }),
  inbox: (page: number, size: number, unreadOnly = false) =>
    request<InboxPage>(
      `/api/ui/messages?page=${page}&size=${size}&unreadOnly=${unreadOnly}`,
    ),
  unreadMessageCount: () =>
    request<UnreadMessageCount>('/api/ui/messages/unread-count'),
  markMessageRead: (id: number) =>
    request<void>(`/api/ui/messages/${id}/read`, { method: 'POST' }),
  markAllMessagesRead: () =>
    request<void>('/api/ui/messages/read-all', { method: 'POST' }),
  deleteMessage: (id: number) =>
    request<void>(`/api/ui/messages/${id}`, { method: 'DELETE' }),
  accountDeletionStatus: () =>
    request<AccountDeletionStatus>('/api/ui/account-deletion'),
  beginAccountDeletionReauthentication: () =>
    request<AccountDeletionReauthenticationResponse>('/api/ui/account-deletion/reauthorize', {
      method: 'POST',
    }),
  pendingGithubRegistration: () =>
    request<PendingGithubRegistration>('/api/ui/registration'),
  completeGithubRegistration: () =>
    request<GithubRegistrationResult>('/api/ui/registration', {
      method: 'POST',
    }),
  logout: () =>
    request<void>('/api/ui/logout', {
      method: 'POST',
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
  pointsRanking: () => request<PointsRankingResponse>('/api/ui/ranking/points'),
  announcements: (placement: Announcement['placement'] = 'GLOBAL_TOP') =>
    request<Announcement[]>(`/api/ui/notices?placement=${encodeURIComponent(placement)}`),
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
  swarmPeerInsights: (days: number) =>
    request<SwarmPeerInsightsResponse>(`/api/ui/peer-insights/swarm?days=${days}`),
  banPeerInsights: (days: number) =>
    request<BanPeerInsightsResponse>(`/api/ui/peer-insights/ban?days=${days}`),
}

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
  GithubCommunityJoinResult,
  GithubCommunityStatus,
  CommunityGroup,
  FeatureAvailability,
  HomeBittorrentTrafficResponse,
  HomeCompanionStatisticsResponse,
  HomeNetworkStatisticsResponse,
  HomeStats,
  InboxPage,
  MemberProviderBinding,
  IpQueryResult,
  IpQueryLookupResponse,
  IpCommentPage,
  IpComment,
  MailUnsubscribeOption,
  NotificationPreferencesResponse,
  NotificationPreference,
  PageResponse,
  PbhLicense,
  PbhLicenseAvailability,
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
  SubmissionRecord,
  SubmissionType,
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
  submissions: (query: { type: SubmissionType; days: number; page: number; size: number }) =>
    request<PageResponse<SubmissionRecord>>('/api/ui/submissions?' + new URLSearchParams({
      type: query.type, days: String(query.days), page: String(query.page), size: String(query.size),
    })),
  session: () => request<SessionResponse>('/api/ui/session'),
  features: () => request<FeatureAvailability>('/api/ui/features'),
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
  messageConversation: (id: number, page = 1) =>
    request<InboxPage>(`/api/ui/messages/${id}/conversation?page=${page}&size=20`),
  unreadMessageCount: () =>
    request<UnreadMessageCount>('/api/ui/messages/unread-count'),
  markMessageRead: (id: number) =>
    request<void>(`/api/ui/messages/${id}/read`, { method: 'POST' }),
  markAllMessagesRead: () =>
    request<void>('/api/ui/messages/read-all', { method: 'POST' }),
  deleteMessage: (id: number) =>
    request<void>(`/api/ui/messages/${id}`, { method: 'DELETE' }),
  communityGroups: () => request<CommunityGroup[]>('/api/ui/community-groups'),
  communityGroupGithubStatus: (id: number) =>
    request<GithubCommunityStatus>(`/api/ui/community-groups/${id}/github-status`),
  joinCommunityGithubGroup: (id: number) =>
    request<GithubCommunityJoinResult>(`/api/ui/community-groups/${id}/join`, {
      method: 'POST',
    }),
  memberPlatforms: () =>
    request<MemberProviderBinding[]>('/api/ui/memberships'),
  bindMemberPlatform: (provider: string, proof: string) =>
    request<MemberProviderBinding>(
      `/api/ui/memberships/${encodeURIComponent(provider)}/bind`,
      {
        method: 'POST',
        body: JSON.stringify({ proof }),
      },
    ),
  unbindMemberPlatform: (provider: string) =>
    request<void>(`/api/ui/memberships/${encodeURIComponent(provider)}`, {
      method: 'DELETE',
    }),
  membershipLicenses: () =>
    request<PbhLicenseAvailability>('/api/ui/memberships/licenses'),
  generateMembershipLicense: () =>
    request<PbhLicense>('/api/ui/memberships/licenses', {
      method: 'POST',
    }),
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
  ipQueryLookup: (query: string) =>
    request<IpQueryLookupResponse>(
      `/api/ui/ip-query/lookup?query=${encodeURIComponent(query)}`,
    ),
  ipQueryComments: (scope: string, page: number, size: number) =>
    request<IpCommentPage>(
      `/api/ui/ip-query/comments?scope=${encodeURIComponent(scope)}&page=${page}&size=${size}`,
    ),
  createIpQueryComment: (
    scope: string,
    content: string,
    parentId: number | null = null,
  ) =>
    request<IpComment>('/api/ui/ip-query/comments', {
      method: 'POST',
      body: JSON.stringify({ scope, content, parentId }),
    }),
  updateIpQueryComment: (id: number, content: string, revision: number) =>
    request<IpComment>(`/api/ui/ip-query/comments/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ content, revision }),
    }),
  deleteIpQueryComment: (id: number) =>
    request<void>(`/api/ui/ip-query/comments/${id}`, {
      method: 'DELETE',
    }),
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

export function homeTrafficRankings(days: number) {
  return request<import('./types').TrafficRankingsResponse>(
    '/api/ui/statistics/bittorrent-traffic-rankings?days=' + days,
  )
}

export const mailUnsubscribeApi = {
  options: (token: string) => request<MailUnsubscribeOption[]>(
    '/api/ui/mail-unsubscribe', { headers: { 'X-Unsubscribe-Token': token } }),
  save: (token: string, categories: string[]) => request<void>('/api/ui/mail-unsubscribe', {
    method: 'POST', headers: { 'X-Unsubscribe-Token': token }, body: JSON.stringify({ categories }),
  }),
}

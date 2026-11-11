import { request } from './http'

export type FooterAudience = 'AUTHENTICATED' | 'ADMIN'

export interface FooterLink {
  labelZh: string
  labelEn: string
  href: string
}

export interface FooterColumn {
  titleZh: string
  titleEn: string
  links: FooterLink[]
}

export interface FooterMetricSetting {
  key: FooterMetricKey
  enabled: boolean
  audience: FooterAudience
}

export type FooterMetricKey =
  | 'loadAverage'
  | 'cpu'
  | 'memory'
  | 'systemUptime'
  | 'serverUptime'
  | 'diskIo'
  | 'network'
  | 'httpRps'
  | 'sqlQps'
  | 'redisQps'
  | 'httpApiAverage'
  | 'httpApiP99'
  | 'httpPingAverage'
  | 'httpPingP99'
  | 'httpOtherAverage'
  | 'httpOtherP99'

export interface FooterConfig {
  disclaimerZh: string
  disclaimerEn: string
  disclaimerAudience: FooterAudience
  icp: string
  icpAudience: FooterAudience
  columns: FooterColumn[]
  columnsAudience: FooterAudience
  timeAudience: FooterAudience
  loginAudience: FooterAudience
  performanceMetrics: FooterMetricSetting[]
}

export interface FooterHttpPerformance {
  apiAverageMillis: number | null
  apiP99Millis: number | null
  btnPingAverageMillis: number | null
  btnPingP99Millis: number | null
  otherAverageMillis: number | null
  otherP99Millis: number | null
}

export interface FooterPerformance {
  loadAverage: number | null
  cpuPercent: number | null
  memoryPercent: number | null
  systemUptimeSeconds: number | null
  serverUptimeSeconds: number | null
  diskIoBytesPerSecond: number | null
  networkBytesPerSecond: number | null
  httpRequestsPerSecond: number | null
  sqlQueriesPerSecond: number | null
  redisQueriesPerSecond: number | null
  httpResponses: FooterHttpPerformance | null
}

export interface FooterWorldTime {
  serverEpochMillis: number
  serverTimeZone: string
}

export interface FooterVisitor {
  ip: string
  geo: string | null
  system: string | null
  browser: string | null
  proxyProvider: 'CLOUDFLARE' | 'TENCENT_EDGEONE' | null
}

export interface FooterResponse {
  disclaimerZh: string | null
  disclaimerEn: string | null
  icp: string | null
  columns: FooterColumn[]
  worldTime: FooterWorldTime | null
  performance: FooterPerformance | null
  visitor: FooterVisitor | null
}

export const footerApi = {
  get: () => request<FooterResponse>('/api/ui/footer'),
  config: () => request<FooterConfig>('/api/admin/footer-config'),
  updateConfig: (body: FooterConfig) =>
    request<FooterConfig>('/api/admin/footer-config', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
}

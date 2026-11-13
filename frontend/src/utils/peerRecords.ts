import type { BanHistoryRecord } from '@/api/types'

export function shortModuleName(name: string | null) {
  if (!name) return 'N/A'
  const prefix = 'com.ghostchu.peerbanhelper.module.impl.rule.'
  return name.startsWith(prefix) ? name.slice(prefix.length) : name
}

export function geoIp(record: Pick<BanHistoryRecord, 'peerGeoip'>) {
  if (!record.peerGeoip) return 'N/A'
  const country = record.peerGeoip.country?.iso || record.peerGeoip.country?.name
  const province = record.peerGeoip.city?.cnProvince
  const city = record.peerGeoip.city?.cnCity || record.peerGeoip.city?.name
  const isp = record.peerGeoip.network?.isp?.replace(
    /^中国(?=电信|联通|移动|广电)/,
    '',
  )
  const parts = [country, province, city, isp]
    .filter((value): value is string => Boolean(value))
    .filter((value, index, values) => values.indexOf(value) === index)
  return parts.join(', ') || 'N/A'
}


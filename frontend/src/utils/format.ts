import { useI18n } from 'vue-i18n'

export function useFormatters() {
  const { locale, t } = useI18n()

  const formatNumber = (value: number | null | undefined) =>
    value == null ? 'N/A' : new Intl.NumberFormat(locale.value).format(value)

  const formatDate = (value: string | number | null | undefined) => {
    if (value == null) return 'N/A'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return 'N/A'
    return new Intl.DateTimeFormat(locale.value, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(date)
  }

  const formatBytes = (value: number | null | undefined) => {
    if (value == null || Number.isNaN(value)) return 'N/A'
    if (value === 0) return `0 ${t('units.bytes')}`
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB']
    const index = Math.min(Math.floor(Math.log(Math.abs(value)) / Math.log(1024)), units.length - 1)
    const result = value / 1024 ** index
    return `${new Intl.NumberFormat(locale.value, {
      maximumFractionDigits: index === 0 ? 0 : 2,
    }).format(result)} ${units[index]}`
  }

  const formatProgress = (value: number | null | undefined) =>
    value == null
      ? 'N/A'
      : new Intl.NumberFormat(locale.value, {
          style: 'percent',
          minimumFractionDigits: 0,
          maximumFractionDigits: 2,
        }).format(value)

  const formatDuration = (durationMs: number | null | undefined) => {
    if (durationMs == null) return 'N/A'
    let seconds = Math.max(0, Math.floor(durationMs / 1000))
    const days = Math.floor(seconds / 86400)
    seconds %= 86400
    const hours = Math.floor(seconds / 3600)
    seconds %= 3600
    const minutes = Math.floor(seconds / 60)
    const remainingSeconds = seconds % 60
    const parts: string[] = []
    if (days) parts.push(t('duration.days', { count: days }))
    if (hours) parts.push(t('duration.hours', { count: hours }))
    if (minutes) parts.push(t('duration.minutes', { count: minutes }))
    if (remainingSeconds || parts.length === 0) {
      parts.push(t('duration.seconds', { count: remainingSeconds }))
    }
    return parts.join(locale.value.startsWith('zh') ? '' : ' ')
  }

  const formatRatio = (value: number | null | undefined) =>
    value == null
      ? 'N/A'
      : new Intl.NumberFormat(locale.value, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        }).format(value)

  return {
    formatNumber,
    formatDate,
    formatBytes,
    formatProgress,
    formatDuration,
    formatRatio,
  }
}

export function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

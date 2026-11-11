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

  const formatBytes = (value: number | string | bigint | null | undefined) => {
    if (value == null) return 'N/A'
    let bytes: bigint
    try {
      if (typeof value === 'bigint') {
        bytes = value
      } else if (typeof value === 'string') {
        bytes = BigInt(value)
      } else {
        if (!Number.isFinite(value)) return 'N/A'
        bytes = BigInt(Math.trunc(value))
      }
    } catch {
      return 'N/A'
    }

    const negative = bytes < 0n
    const absolute = negative ? -bytes : bytes
    if (absolute === 0n) return `0 ${t('units.bytes')}`
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB']
    let index = 0
    let divisor = 1n
    while (index < units.length - 1 && absolute / divisor >= 1024n) {
      divisor *= 1024n
      index++
    }
    const whole = absolute / divisor
    const fraction = absolute % divisor
    const scaled = Number(whole) + Number(fraction) / Number(divisor)
    const formatted = new Intl.NumberFormat(locale.value, {
      maximumFractionDigits: index === 0 ? 0 : 2,
    }).format(negative ? -scaled : scaled)
    return `${formatted} ${index === 0 ? t('units.bytes') : units[index]}`
  }

  const formatPointBytes = (
    value: number | string | bigint | null | undefined,
  ) => {
    if (value == null) return 'N/A'
    let points: bigint
    try {
      if (typeof value === 'bigint') {
        points = value
      } else if (typeof value === 'string') {
        points = BigInt(value)
      } else {
        if (!Number.isFinite(value)) return 'N/A'
        points = BigInt(Math.trunc(value))
      }
    } catch {
      return 'N/A'
    }

    const negative = points < 0n
    const absolute = negative ? -points : points
    if (absolute === 0n) return '0 KiB'
    const units = ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB']
    let index = 0
    let divisor = 1024n
    while (index < units.length - 1 && absolute / divisor >= 1024n) {
      divisor *= 1024n
      index++
    }
    const whole = absolute / divisor
    const fraction = absolute % divisor
    const scaled = Number(whole) + Number(fraction) / Number(divisor)
    const formatted = new Intl.NumberFormat(locale.value, {
      minimumFractionDigits: index === 0 ? 2 : 0,
      maximumFractionDigits: 2,
    }).format(negative ? -scaled : scaled)
    return `${formatted} ${units[index]}`
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
    formatPointBytes,
    formatProgress,
    formatDuration,
    formatRatio,
  }
}

export function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

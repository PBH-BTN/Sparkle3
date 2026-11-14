interface TooltipItem {
  dataIndex?: number
  name?: string
  value?: unknown
  axisValueLabel?: string
  axisValue?: unknown
  seriesName?: string
  marker?: string
  encode?: { x?: number[]; y?: number[] }
}

export function tooltipValue(item: TooltipItem): number {
  const value = Array.isArray(item.value)
    ? item.value[item.encode?.y?.[0] ?? item.encode?.x?.[0] ?? 1]
    : item.value
  return value == null || value === '' ? NaN : Number(value)
}

function escapeHtml(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[character]!)
}

export function sortedTooltipItems(params: unknown): TooltipItem[] {
  const items = (Array.isArray(params) ? params : [params]) as TooltipItem[]
  return items.filter((item) => item && Number.isFinite(tooltipValue(item)) && tooltipValue(item) !== 0)
    .sort((left, right) => tooltipValue(right) - tooltipValue(left))
}

export function formatAxisTooltip(params: unknown, valueFormatter?: (value: number, dataIndex: number) => unknown): string {
  const items = sortedTooltipItems(params)
  if (!items.length) return ''
  const headerItem = items.find((item) => item.axisValueLabel != null || item.axisValue != null)
  const header = escapeHtml(headerItem?.axisValueLabel ?? headerItem?.axisValue)
  const rows = items.map((item) => {
    const value = tooltipValue(item)
    const formatted = valueFormatter ? valueFormatter(value, item.dataIndex ?? 0) : value.toLocaleString()
    return '<div style="display:flex;justify-content:space-between;gap:20px"><span>' + (item.marker ?? '') + escapeHtml(item.seriesName) + '</span><strong>' + escapeHtml(formatted) + '</strong></div>'
  })
  return (header ? header + '<br/>' : '') + rows.join('')
}

export function formatItemTooltip(params: unknown, valueFormatter?: (value: number, dataIndex: number) => unknown): string {
  const item = sortedTooltipItems(params)[0]
  if (!item) return ''
  const value = tooltipValue(item)
  const formatted = valueFormatter ? valueFormatter(value, item.dataIndex ?? 0) : value.toLocaleString()
  return [escapeHtml(item.seriesName), (item.marker ?? '') + escapeHtml(item.name) + ': <strong>' + escapeHtml(formatted) + '</strong>']
    .filter(Boolean).join('<br/>')
}

export function utcChartDays(from: string, through: string): string[] {
  const current = new Date(from)
  const end = new Date(through)
  const days: string[] = []
  while (current <= end) {
    days.push(current.toISOString().slice(0, 10))
    current.setUTCDate(current.getUTCDate() + 1)
  }
  return days
}

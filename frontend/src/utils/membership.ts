export function membershipTransactionStatus(status: string | null | undefined) {
  switch (status?.trim().toUpperCase()) {
    case '2':
    case 'PAID':
    case 'COMPLETED':
    case 'SUCCEEDED':
    case 'SUCCESS':
      return { key: 'completed', color: 'green' }
    case 'ACTIVE':
      return { key: 'active', color: 'green' }
    case 'FORMER':
      return { key: 'former', color: 'gray' }
    default:
      return { key: 'unknown', color: 'gray' }
  }
}

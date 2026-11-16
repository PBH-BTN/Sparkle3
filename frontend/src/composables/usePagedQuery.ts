import { ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { Message } from '@arco-design/web-vue'
import type { PageResponse } from '@/api/types'
import { errorMessage } from '@/utils/format'

interface PageParameters {
  page: number
  size: number
  sortBy: string
  sortOrder: 'asc' | 'desc'
}

interface PagedQueryOptions {
  pageSize?: number
  sortBy: string
  sortOrder?: 'asc' | 'desc'
}

export function usePagedQuery<T, Q extends Record<string, unknown>>(
  fetcher: (query: Q & PageParameters) => Promise<PageResponse<T, Q>>,
  buildQuery: () => Q,
  options: PagedQueryOptions,
) {
  const { t } = useI18n()
  const records = ref<T[]>([])
  const loading = ref(false)
  const page = ref(1)
  const pageSize = ref(options.pageSize ?? 20)
  const total = ref(0)
  const pages = ref(0)
  const sortBy = ref(options.sortBy)
  const sortOrder = ref<'asc' | 'desc'>(options.sortOrder ?? 'desc')

  const appliedQuery = shallowRef<(Q & Pick<PageParameters, 'sortBy' | 'sortOrder'>) | null>(null)

  const canExport = ref(false)
  let sequence = 0

  async function run(explicitSearch = false) {
    const request = ++sequence
    loading.value = true
    try {
      const query = { ...buildQuery(), sortBy: sortBy.value, sortOrder: sortOrder.value }
      const result = await fetcher({ ...query, page: page.value, size: pageSize.value })
      if (request !== sequence) return
      appliedQuery.value = { ...query, ...result.appliedFilters }
      if (explicitSearch) canExport.value = true
      records.value = result.records
      total.value = result.total
      pages.value = result.pages
      page.value = result.current || page.value
      if (result.size > 0) {
        pageSize.value = result.size
      }
    } catch (error) {
      if (request !== sequence) return
      canExport.value = false
      appliedQuery.value = null
      records.value = []
      total.value = 0
      pages.value = 0
      Message.error(errorMessage(error, t('common.requestFailed')))
    } finally {
      if (request === sequence) loading.value = false
    }
  }

  async function search() {
    canExport.value = false
    page.value = 1
    await run()
  }

  async function submitSearch() {
    canExport.value = false
    page.value = 1
    await run(true)
  }

  async function changePage(nextPage: number) {
    page.value = nextPage
    await run()
  }

  async function changePageSize(nextSize: number) {
    pageSize.value = nextSize
    page.value = 1
    await run()
  }

  async function changeSort(dataIndex: string, direction: string) {
    if (!direction) return
    sortBy.value = dataIndex
    sortOrder.value = direction === 'ascend' ? 'asc' : 'desc'
    page.value = 1
    await run()
  }

  return {
    records,
    appliedQuery,
    canExport,
    submitSearch,
    loading,
    page,
    pageSize,
    total,
    pages,
    sortBy,
    sortOrder,
    run,
    search,
    changePage,
    changePageSize,
    changeSort,
  }
}

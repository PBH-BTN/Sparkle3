import { ref } from 'vue'
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
  fetcher: (query: Q & PageParameters) => Promise<PageResponse<T>>,
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

  async function run() {
    loading.value = true
    try {
      const result = await fetcher({
        ...buildQuery(),
        page: page.value,
        size: pageSize.value,
        sortBy: sortBy.value,
        sortOrder: sortOrder.value,
      })
      records.value = result.records
      total.value = result.total
      pages.value = result.pages
      page.value = result.current || page.value
      if (result.size > 0) {
        pageSize.value = result.size
      }
    } catch (error) {
      records.value = []
      total.value = 0
      pages.value = 0
      Message.error(errorMessage(error, t('common.requestFailed')))
    } finally {
      loading.value = false
    }
  }

  async function search() {
    page.value = 1
    await run()
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

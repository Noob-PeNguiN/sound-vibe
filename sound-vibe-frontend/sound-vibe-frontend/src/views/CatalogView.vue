<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi, getAssetFileUrl } from '@/api/catalog'
import { searchApi } from '@/api/search'
import type { TrackSearchResult, FilterOptions } from '@/api/search'
import { downloadAssetFile } from '@/api/asset'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import type { CartItem } from '@/types/order'
import AudioPreview from '@/components/AudioPreview.vue'
import MidiPlayer from '@/components/MidiPlayer.vue'
import type { Track, TrackQuery, FileType, TrackType } from '@/types/catalog'

// ========== 路由 & Store ==========
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()
const isAddingToCart = ref(false)

const handleLogout = async () => {
  if (confirm('确定要退出登录吗？')) {
    await userStore.logout()
    router.push('/login')
  }
}

// ========== 状态 ==========
const tracks = ref<Track[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const totalTracks = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const totalPages = ref(0)
const selectedTrack = ref<Track | null>(null)
const isDetailLoading = ref(false)
const detailErrorMessage = ref('')
/** 选中的作品是否为付费 */
const isSelectedTrackPaid = computed(() => {
  return selectedTrack.value ? (selectedTrack.value.price !== null && selectedTrack.value.price > 0) : false
})

/** 选中的作品是否已售罄（stock 为 null 表示不限库存） */
const isOutOfStock = computed(() => {
  if (!selectedTrack.value) return false
  return selectedTrack.value.stock !== null && selectedTrack.value.stock !== undefined && selectedTrack.value.stock <= 0
})

// ========== 搜索 / 过滤 ==========
const keyword = ref('')
const selectedFileType = ref<FileType | ''>('')
const selectedTrackType = ref<TrackType | ''>('')
const selectedTag = ref('')

// ========== AI 语义搜索 ==========
/** 是否处于 AI Vibe Search 模式 */
const isAiSearchMode = ref(false)
/** AI 搜索输入框内容 */
const aiQuery = ref('')
/** AI 搜索加载状态 */
const isAiSearching = ref(false)
/** 当前列表是否为 AI 搜索结果 */
const isAiResult = ref(false)

// ========== 高级筛选（ES 搜索引擎） ==========
const showAdvancedFilters = ref(false)
const minBpm = ref<number | null>(null)
const maxBpm = ref<number | null>(null)
const selectedMusicalKey = ref('')
const selectedGenre = ref('')

/** 是否正在使用高级筛选（BPM/Key/Genre），走 ES 搜索 */
const isUsingAdvancedSearch = computed(() => {
  return !!(minBpm.value || maxBpm.value || selectedMusicalKey.value || selectedGenre.value)
})

/** 动态筛选选项（从 ES 聚合获取） */
const availableKeys = ref<string[]>([])
const availableGenres = ref<string[]>([])
const isLoadingFilters = ref(false)

/** 加载动态筛选选项（级联：选了调式 → 只返回含该调式的风格，反之亦然） */
const loadFilterOptions = async () => {
  isLoadingFilters.value = true
  try {
    const filters = await searchApi.getFilters({
      musicalKey: selectedMusicalKey.value || undefined,
      genre: selectedGenre.value || undefined,
    })
    availableKeys.value = filters.musicalKeys || []
    availableGenres.value = filters.genres || []
  } catch (err) {
    console.warn('[CatalogView] 加载筛选选项失败:', err)
  } finally {
    isLoadingFilters.value = false
  }
}

/** 文件类型筛选选项 */
const fileTypeOptions: { value: FileType | ''; label: string; icon: string }[] = [
  { value: '', label: '全部', icon: '🎯' },
  { value: 'AUDIO', label: '音频', icon: '🎵' },
  { value: 'MIDI', label: 'MIDI', icon: '🎹' }
]

/** 作品类型筛选选项 */
const trackTypeOptions: { value: TrackType | ''; label: string; icon: string }[] = [
  { value: '', label: '全部', icon: '🎯' },
  { value: 'SINGLE', label: '单文件', icon: '🎵' },
  { value: 'PACK', label: '合集', icon: '📦' }
]

/** 常见标签快捷按钮 */
const popularTags = [
  'trap', 'lo-fi', 'drill', 'hip-hop', 'r&b', 'pop', 'edm',
  'sample-pack', 'drum loop', 'melody loop', 'one-shot', 'vocal',
  '808', 'piano', 'guitar', 'drum-kit'
]

// ========== 登录状态 ==========
const isLoggedIn = (): boolean => {
  return !!localStorage.getItem('soundvibe-token')
}

// ========== 方法 ==========

/**
 * 将 ES 搜索结果转为与 Track 兼容的格式（用于卡片展示）
 * v2: TrackDoc 使用数组字段 bpmValues/musicalKeys/durations
 */
const esResultToTrack = (doc: TrackSearchResult): Track => ({
  id: doc.id,
  title: doc.title,
  description: null,
  trackType: (doc.trackType as TrackType) || 'SINGLE',
  producerId: doc.producerId,
  producerName: doc.producerName,
  coverId: doc.coverId,
  fileId: null,
  fileType: 'AUDIO',
  fileCount: 1,
  files: null,
  price: doc.price,
  status: doc.status as 0 | 1,
  visibility: 2,
  tags: doc.tags,
  autoTags: doc.autoTags?.join(',') || null,
  genre: doc.genres?.join(', ') || null,
  allowPreview: true,
  previewDuration: 30,
  soldCount: 0,
  stock: null,
  createTime: '',
  updateTime: ''
})

/**
 * 加载作品列表
 * 当使用高级筛选（BPM/Key/Genre）时走 ES 搜索引擎，否则走 Catalog MySQL
 */
const loadTracks = async () => {
  isLoading.value = true
  errorMessage.value = ''

  try {
    if (isUsingAdvancedSearch.value) {
      // ===== ES 搜索模式 =====
      const result = await searchApi.searchTracks({
        keyword: keyword.value || undefined,
        genre: selectedGenre.value || undefined,
        minBpm: minBpm.value || undefined,
        maxBpm: maxBpm.value || undefined,
        musicalKey: selectedMusicalKey.value || undefined,
        page: currentPage.value - 1, // ES 页码从 0 开始
        size: pageSize.value
      })
      tracks.value = result.content.map(esResultToTrack)
      totalTracks.value = result.totalElements
      totalPages.value = result.totalPages
    } else {
      // ===== Catalog MySQL 模式（原始逻辑） =====
      const query: TrackQuery = {
        keyword: keyword.value || undefined,
        tag: selectedTag.value || undefined,
        fileType: selectedFileType.value || undefined,
        trackType: selectedTrackType.value || undefined,
        status: 1,
        visibility: 2,
        current: currentPage.value,
        size: pageSize.value
      }

      const page = await catalogApi.listTracks(query)
      tracks.value = page.records
      totalTracks.value = page.total
      totalPages.value = page.pages
      currentPage.value = page.current
    }
  } catch (err: any) {
    errorMessage.value = err.message || '加载失败，请稍后重试'
    console.error('[CatalogView] 加载失败:', err)
  } finally {
    isLoading.value = false
  }
}

const handleSearch = () => {
  if (isAiSearchMode.value) {
    handleAiSearch()
  } else {
    isAiResult.value = false
    currentPage.value = 1
    loadTracks()
  }
}

/** AI 语义搜索 */
const handleAiSearch = async () => {
  const q = aiQuery.value.trim()
  if (!q) return

  isAiSearching.value = true
  isLoading.value = true
  errorMessage.value = ''

  try {
    const results = await searchApi.semanticSearch(q, 20)
    tracks.value = results.map(esResultToTrack)
    totalTracks.value = results.length
    totalPages.value = 1
    currentPage.value = 1
    isAiResult.value = true
  } catch (err: any) {
    errorMessage.value = err.message || 'AI 搜索失败，请稍后重试'
    console.error('[CatalogView] AI 语义搜索失败:', err)
  } finally {
    isAiSearching.value = false
    isLoading.value = false
  }
}

/** 切换 AI/标准搜索模式 */
const toggleAiSearchMode = () => {
  isAiSearchMode.value = !isAiSearchMode.value
  if (!isAiSearchMode.value && isAiResult.value) {
    isAiResult.value = false
    loadTracks()
  }
}

const toggleTag = (tag: string) => {
  selectedTag.value = selectedTag.value === tag ? '' : tag
  currentPage.value = 1
  loadTracks()
}

const setFileType = (type: FileType | '') => {
  selectedFileType.value = type
  currentPage.value = 1
  loadTracks()
}

const setTrackType = (type: TrackType | '') => {
  selectedTrackType.value = type
  currentPage.value = 1
  loadTracks()
}

const toggleAdvancedFilters = () => {
  showAdvancedFilters.value = !showAdvancedFilters.value
  if (showAdvancedFilters.value) {
    loadFilterOptions()
  }
}

// 级联过滤：调式或风格变化时重新加载对方的可用选项
watch(selectedMusicalKey, () => {
  loadFilterOptions()
})
watch(selectedGenre, () => {
  loadFilterOptions()
})

const clearFilters = () => {
  keyword.value = ''
  selectedFileType.value = ''
  selectedTrackType.value = ''
  selectedTag.value = ''
  minBpm.value = null
  maxBpm.value = null
  selectedMusicalKey.value = ''
  selectedGenre.value = ''
  aiQuery.value = ''
  isAiResult.value = false
  currentPage.value = 1
  loadTracks()
}

const goToPage = (page: number) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    loadTracks()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

const formatPrice = (price: number | null): string => {
  if (price === null || price === 0) return '免费'
  return `¥${price.toFixed(2)}`
}

const getCoverGradient = (track: Track) => {
  const colors = [
    'from-blue-600 to-purple-600',
    'from-pink-600 to-red-600',
    'from-green-600 to-teal-600',
    'from-orange-600 to-yellow-600',
    'from-indigo-600 to-blue-600',
    'from-purple-600 to-pink-600'
  ]
  return colors[track.id % colors.length]
}

const parseTags = (tags: string | null): string[] => {
  if (!tags) return []
  return tags.split(',').map(t => t.trim()).filter(Boolean).slice(0, 5)
}

const parseAutoTags = (tags: string | null): string[] => {
  if (!tags) return []
  return tags.split(',').map(t => t.trim()).filter(Boolean).slice(0, 5)
}

const formatDateTime = (dateStr: string): string => {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

/** 获取作品的图标 */
const getTrackIcon = (track: Track) => {
  if (track.trackType === 'PACK') return '📦'
  return track.fileType === 'MIDI' ? '🎹' : '🎵'
}

/** 获取作品类型标签文本 */
const getTrackTypeBadge = (track: Track) => {
  if (track.trackType === 'PACK') return `📦 合集 · ${track.fileCount} 个文件`
  return track.fileType === 'MIDI' ? '🎹 MIDI' : '🎵 Audio'
}

/** 获取类型标签颜色 */
const getTrackTypeBadgeClass = (track: Track) => {
  if (track.trackType === 'PACK') return 'bg-teal-500/80 text-white'
  return track.fileType === 'MIDI' ? 'bg-amber-500/80 text-white' : 'bg-purple-500/80 text-white'
}

/** 是否可以在卡片上显示播放按钮 */
const canPreviewOnCard = (track: Track) => {
  return track.trackType === 'SINGLE'
    && track.fileType !== 'MIDI'
    && track.fileId
    && isTrackPreviewable(track)
}

/** 作品在市场卡片上是否可预览（用于角标提示） */
const isTrackPreviewable = (track: Track) => {
  // 免费作品默认可完整预览
  if (track.price === null || track.price === 0) return true
  // 付费作品由发布者控制是否可试听
  return track.allowPreview !== false
}

/**
 * 打开作品详情弹层
 */
const openTrackDetail = async (trackId: number) => {
  isDetailLoading.value = true
  detailErrorMessage.value = ''
  selectedTrack.value = null
  hasPurchased.value = false

  try {
    selectedTrack.value = await catalogApi.getTrackDetail(trackId)
    // 异步检查购买状态（不阻塞详情展示）
    checkPurchaseStatus(trackId)
  } catch (err: any) {
    detailErrorMessage.value = err.message || '作品详情加载失败，请稍后重试'
    console.error('[CatalogView] 详情加载失败:', err)
  } finally {
    isDetailLoading.value = false
  }
}

const closeTrackDetail = () => {
  selectedTrack.value = null
  isDetailLoading.value = false
  detailErrorMessage.value = ''
  packPreviewingFileId.value = null
}

// ========== PACK 文件预览 ==========
/** 当前正在预览的 PACK 文件的 assetId */
const packPreviewingFileId = ref<number | null>(null)

/** 判断 PACK 文件是否可以预览（已购买全部可以；免费全部可以；付费需要 allowPreview） */
const canPreviewPackFile = (file: { fileType: string; allowPreview?: boolean; assetId: number }) => {
  if (!selectedTrack.value) return false
  const fileType = String(file.fileType || '').toUpperCase()
  // 已购买：所有音频文件都可以完整预览
  if (hasPurchased.value) return fileType === 'AUDIO'
  // 免费作品：所有音频文件都可以预览
  if (!isSelectedTrackPaid.value) return fileType === 'AUDIO'
  // 付费作品：作品级别允许预览 + 文件级别允许预览
  const fileAllowPreview = file.allowPreview !== false
  return selectedTrack.value.allowPreview && fileAllowPreview && fileType === 'AUDIO'
}

/** 切换 PACK 文件音频预览 */
const togglePackFileAudioPreview = (assetId: number) => {
  if (packPreviewingFileId.value === assetId) {
    packPreviewingFileId.value = null
  } else {
    packPreviewingFileId.value = assetId
  }
}

const hasActiveFilters = () => {
  return keyword.value || selectedFileType.value || selectedTrackType.value || selectedTag.value
    || minBpm.value || maxBpm.value || selectedMusicalKey.value || selectedGenre.value
    || isAiResult.value
}

// ========== 购买功能 ==========
const isPurchasing = ref(false)
/** 当前作品是否已购买 */
const hasPurchased = ref(false)
const isCheckingPurchase = ref(false)

/** 检查当前用户是否已购买选中作品 */
const checkPurchaseStatus = async (trackId: number) => {
  if (!isLoggedIn()) {
    hasPurchased.value = false
    return
  }
  isCheckingPurchase.value = true
  try {
    hasPurchased.value = await catalogApi.checkPurchased(trackId)
  } catch {
    hasPurchased.value = false
  } finally {
    isCheckingPurchase.value = false
  }
}

/** 免费获取（保留旧的直接购买流程） */
const handleFreePurchase = async () => {
  if (!selectedTrack.value) return
  if (!isLoggedIn()) {
    alert('请先登录后再获取')
    return
  }
  if (!confirm(`确认免费获取《${selectedTrack.value.title}》？`)) return

  isPurchasing.value = true
  try {
    await catalogApi.purchaseTrack(selectedTrack.value.id)
    hasPurchased.value = true

    const trackId = selectedTrack.value.id
    selectedTrack.value = {
      ...selectedTrack.value,
      soldCount: (selectedTrack.value.soldCount ?? 0) + 1,
      stock: selectedTrack.value.stock != null ? selectedTrack.value.stock - 1 : null
    }
    const idx = tracks.value.findIndex(t => t.id === trackId)
    if (idx !== -1) {
      tracks.value[idx] = { ...selectedTrack.value }
    }

    alert('获取成功！可在「工作台 → 已购作品」中查看')
  } catch (err: any) {
    console.error('[CatalogView] 获取失败:', err)
    alert(err.message || '获取失败，请稍后重试')
  } finally {
    isPurchasing.value = false
  }
}

/** 加入购物车（付费作品走订单流程） */
const handleAddToCart = async () => {
  if (!selectedTrack.value) return
  if (!isLoggedIn()) {
    alert('请先登录')
    return
  }

  const track = selectedTrack.value
  if (cartStore.isInCart(track.id)) {
    alert('该作品已在购物车中')
    return
  }

  isAddingToCart.value = true
  try {
    const item: CartItem = {
      trackId: track.id,
      title: track.title,
      price: track.price || 0,
      licenseType: 'LEASE',
      coverUrl: track.coverId ? getAssetFileUrl(track.coverId) : ''
    }
    await cartStore.addItem(item)
    alert('已加入购物车！')
  } catch (err: any) {
    console.error('[CatalogView] 加入购物车失败:', err)
    alert(err.message || '加入购物车失败')
  } finally {
    isAddingToCart.value = false
  }
}

/** 下载文件（Blob 方式，不跳转页面） */
const downloadFile = async (assetId: number, fileName?: string) => {
  try {
    await downloadAssetFile(assetId, fileName)
  } catch (err: any) {
    console.error('[CatalogView] 下载失败:', err)
    alert('下载失败，请稍后重试')
  }
}

/** 下载合集中的所有文件（逐个触发） */
const downloadAllPackFiles = async () => {
  if (!selectedTrack.value?.files) return
  for (let i = 0; i < selectedTrack.value.files.length; i++) {
    const file = selectedTrack.value.files[i]
    try {
      await downloadAssetFile(file.assetId, file.originalName || `file-${i + 1}`)
    } catch (err) {
      console.error(`[CatalogView] 下载第 ${i + 1} 个文件失败:`, err)
    }
    // 间隔 300ms 避免浏览器拦截
    if (i < selectedTrack.value.files.length - 1) {
      await new Promise(r => setTimeout(r, 300))
    }
  }
}

onMounted(() => {
  loadTracks()
  if (isLoggedIn()) {
    cartStore.fetchCart()
  }
})
</script>

<template>
  <div class="min-h-screen bg-slate-900">
    <!-- 顶部导航栏 -->
    <nav class="bg-slate-800/80 backdrop-blur-xl border-b border-slate-700/50 shadow-lg sticky top-0 z-40">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="flex items-center space-x-3 hover:opacity-80 transition">
            <div class="h-10 w-10 bg-gradient-to-tr from-blue-500 to-purple-500 rounded-lg flex items-center justify-center shadow-lg">
              <span class="text-2xl">🎵</span>
            </div>
            <div>
              <h1 class="text-xl font-bold text-white">SoundVibe</h1>
              <p class="text-xs text-slate-400">音乐资产交易平台</p>
            </div>
          </router-link>

          <div class="flex items-center gap-3">
            <!-- 购物车图标 -->
            <router-link
              v-if="isLoggedIn()"
              to="/cart"
              class="relative px-3 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-lg transition"
              title="购物车"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 100 4 2 2 0 000-4z" />
              </svg>
              <span
                v-if="cartStore.itemCount > 0"
                class="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white text-xs font-bold rounded-full flex items-center justify-center"
              >
                {{ cartStore.itemCount > 9 ? '9+' : cartStore.itemCount }}
              </span>
            </router-link>
            <div v-if="isLoggedIn()" class="relative group">
              <button
                class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition flex items-center gap-2"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                </svg>
                我的
                <svg class="w-3 h-3 transition-transform group-hover:rotate-180" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                </svg>
              </button>
              <div class="absolute right-0 top-full pt-2 w-48 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50">
                <div class="bg-slate-800 border border-slate-600 rounded-xl shadow-2xl py-1.5 backdrop-blur-xl">
                  <router-link to="/assets" class="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-300 hover:text-white hover:bg-slate-700/70 transition">
                    <span class="w-5 text-center">🎛️</span>
                    <span>工作台</span>
                  </router-link>
                  <router-link to="/cart" class="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-300 hover:text-white hover:bg-slate-700/70 transition">
                    <span class="w-5 text-center">🛒</span>
                    <span>购物车</span>
                  </router-link>
                  <router-link to="/orders" class="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-300 hover:text-white hover:bg-slate-700/70 transition">
                    <span class="w-5 text-center">📋</span>
                    <span>我的订单</span>
                  </router-link>
                  <div class="border-t border-slate-700 my-1.5"></div>
                  <button
                    class="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-400 hover:text-red-300 hover:bg-slate-700/70 transition"
                    @click="handleLogout"
                  >
                    <svg class="w-4 h-4 ml-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
                    </svg>
                    <span>退出登录</span>
                  </button>
                </div>
              </div>
            </div>
            <router-link
              v-if="isLoggedIn()"
              to="/generate"
              class="px-4 py-2 bg-gradient-to-r from-purple-600 to-fuchsia-600 hover:from-purple-500 hover:to-fuchsia-500 text-white text-sm font-medium rounded-lg transition shadow-lg flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
              </svg>
              AI 生成
            </router-link>
            <router-link
              v-if="isLoggedIn()"
              to="/publish"
              class="px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white text-sm font-medium rounded-lg transition shadow-lg flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
              </svg>
              发布作品
            </router-link>
            <router-link
              v-if="!isLoggedIn()"
              to="/login"
              class="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition"
            >
              登录 / 注册
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <!-- Hero 区域 -->
    <div class="border-b border-slate-700/30" :class="isAiSearchMode ? 'bg-gradient-to-b from-purple-950/80 via-slate-900 to-slate-900' : 'bg-gradient-to-b from-slate-800 to-slate-900'">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 text-center">
        <h2 v-if="!isAiSearchMode" class="text-4xl font-bold text-white mb-3">探索 · 发现 · 交易</h2>
        <h2 v-else class="text-4xl font-bold mb-3">
          <span class="bg-gradient-to-r from-purple-400 via-fuchsia-400 to-pink-400 bg-clip-text text-transparent">✨ AI Vibe Search</span>
        </h2>
        <p v-if="!isAiSearchMode" class="text-slate-400 text-lg max-w-2xl mx-auto mb-8">
          发现优质的采样、Loop、Beat、MIDI、采样包和更多音乐素材
        </p>
        <p v-else class="text-purple-300/70 text-lg max-w-2xl mx-auto mb-8">
          用自然语言描述你脑海中的声音，AI 帮你找到最匹配的音乐
        </p>

        <div class="max-w-2xl mx-auto">
          <!-- 搜索模式切换 -->
          <div class="flex items-center justify-center gap-3 mb-4">
            <button
              class="px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-300"
              :class="!isAiSearchMode
                ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20'
                : 'bg-slate-800 text-slate-400 hover:text-white border border-slate-700'"
              @click="isAiSearchMode && toggleAiSearchMode()"
            >🔍 标准搜索</button>
            <button
              class="px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-300"
              :class="isAiSearchMode
                ? 'bg-gradient-to-r from-purple-600 to-fuchsia-600 text-white shadow-lg shadow-purple-600/30'
                : 'bg-slate-800 text-slate-400 hover:text-white border border-slate-700'"
              @click="!isAiSearchMode && toggleAiSearchMode()"
            >✨ AI Vibe Search</button>
          </div>

          <!-- ===== 标准搜索栏 ===== -->
          <div v-if="!isAiSearchMode" class="flex gap-2">
            <div class="flex-1 relative">
              <svg class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
              </svg>
              <input
                v-model="keyword"
                type="text"
                placeholder="搜索标题、标签、描述..."
                class="w-full pl-11 pr-4 py-3.5 bg-slate-800 border border-slate-600 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition text-base"
                @keydown.enter="handleSearch"
              />
            </div>
            <button
              class="px-6 py-3.5 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-xl transition duration-200"
              @click="handleSearch"
            >搜索</button>
            <button
              class="px-4 py-3.5 rounded-xl transition duration-200 flex items-center gap-1.5 text-sm font-medium"
              :class="showAdvancedFilters || isUsingAdvancedSearch
                ? 'bg-purple-600 text-white'
                : 'bg-slate-700 text-slate-300 hover:bg-slate-600'"
              @click="toggleAdvancedFilters"
              title="高级筛选（BPM / 调式 / 风格）"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4"></path>
              </svg>
              高级
            </button>
          </div>

          <!-- ===== AI Vibe 搜索栏 ===== -->
          <div v-else class="flex gap-2">
            <div class="flex-1 relative group">
              <div class="absolute -inset-0.5 bg-gradient-to-r from-purple-600 via-fuchsia-500 to-pink-500 rounded-xl opacity-50 group-focus-within:opacity-100 blur transition duration-300"></div>
              <div class="relative flex items-center">
                <span class="absolute left-4 text-lg">✨</span>
                <input
                  v-model="aiQuery"
                  type="text"
                  placeholder="Describe the vibe... (e.g. Heavy distorted midwest emo guitar, dark electronic drums)"
                  class="w-full pl-11 pr-4 py-3.5 bg-slate-900 border border-purple-500/30 rounded-xl text-white placeholder-purple-300/40 focus:outline-none focus:border-purple-400 transition text-base"
                  @keydown.enter="handleSearch"
                />
              </div>
            </div>
            <button
              class="px-6 py-3.5 bg-gradient-to-r from-purple-600 to-fuchsia-600 hover:from-purple-500 hover:to-fuchsia-500 text-white font-medium rounded-xl transition duration-200 shadow-lg shadow-purple-600/20 flex items-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
              :disabled="isAiSearching || !aiQuery.trim()"
              @click="handleSearch"
            >
              <svg v-if="isAiSearching" class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
              </svg>
              {{ isAiSearching ? '搜索中' : '搜索' }}
            </button>
          </div>

          <!-- 高级筛选面板 -->
          <transition name="slide-down">
            <div v-if="showAdvancedFilters" class="mt-3 bg-slate-800/80 border border-slate-700 rounded-xl p-4 space-y-3">
              <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <!-- BPM 范围 -->
                <div>
                  <label class="block text-xs text-slate-400 mb-1.5 font-medium">BPM 范围</label>
                  <div class="flex items-center gap-2">
                    <input
                      v-model.number="minBpm"
                      type="number"
                      min="1"
                      max="999"
                      placeholder="最小"
                      class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-purple-500 transition"
                    />
                    <span class="text-slate-500 text-sm shrink-0">—</span>
                    <input
                      v-model.number="maxBpm"
                      type="number"
                      min="1"
                      max="999"
                      placeholder="最大"
                      class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-purple-500 transition"
                    />
                  </div>
                </div>

                <!-- 调式 -->
                <div>
                  <label class="block text-xs text-slate-400 mb-1.5 font-medium">调式 (Key)</label>
                  <select
                    v-model="selectedMusicalKey"
                    class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 transition appearance-none"
                    :disabled="isLoadingFilters"
                  >
                    <option value="">全部调式</option>
                    <option v-for="key in availableKeys" :key="key" :value="key">{{ key }}</option>
                  </select>
                  <p v-if="availableKeys.length === 0 && !isLoadingFilters" class="text-xs text-slate-500 mt-1">暂无可选调式</p>
                </div>

                <!-- 风格 -->
                <div>
                  <label class="block text-xs text-slate-400 mb-1.5 font-medium">风格 (Genre)</label>
                  <select
                    v-model="selectedGenre"
                    class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 transition appearance-none"
                    :disabled="isLoadingFilters"
                  >
                    <option value="">全部风格</option>
                    <option v-for="g in availableGenres" :key="g" :value="g">{{ g }}</option>
                  </select>
                  <p v-if="availableGenres.length === 0 && !isLoadingFilters" class="text-xs text-slate-500 mt-1">暂无可选风格</p>
                </div>

                <!-- 搜索按钮 -->
                <div class="flex items-end">
                  <button
                    class="w-full px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white font-medium rounded-lg transition text-sm"
                    @click="handleSearch"
                  >
                    应用筛选
                  </button>
                </div>
              </div>

              <!-- 当前高级筛选状态 -->
              <div v-if="isUsingAdvancedSearch" class="flex items-center gap-2 pt-1">
                <span class="text-xs text-purple-400 flex items-center gap-1">
                  <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 24 24"><circle cx="12" cy="12" r="6"/></svg>
                  ES 搜索引擎已启用
                </span>
                <span v-if="minBpm || maxBpm" class="px-2 py-0.5 bg-purple-500/20 text-purple-300 rounded-full text-xs">
                  BPM: {{ minBpm || '∞' }} - {{ maxBpm || '∞' }}
                </span>
                <span v-if="selectedMusicalKey" class="px-2 py-0.5 bg-purple-500/20 text-purple-300 rounded-full text-xs">
                  {{ selectedMusicalKey }}
                </span>
                <span v-if="selectedGenre" class="px-2 py-0.5 bg-purple-500/20 text-purple-300 rounded-full text-xs">
                  {{ selectedGenre }}
                </span>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <!-- 主内容区域 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">

      <!-- 过滤栏（AI 搜索模式下隐藏） -->
      <div v-if="!isAiSearchMode" class="mb-6 space-y-4">
        <!-- 作品类型筛选 -->
        <div class="flex items-center gap-3 flex-wrap">
          <span class="text-slate-400 text-sm font-medium shrink-0">作品:</span>
          <div class="flex gap-2 flex-wrap">
            <button
              v-for="opt in trackTypeOptions"
              :key="opt.value"
              class="px-3.5 py-1.5 rounded-lg text-sm font-medium transition-all duration-200"
              :class="{
                'bg-teal-600 text-white shadow-md': selectedTrackType === opt.value,
                'bg-slate-800 text-slate-300 hover:bg-slate-700 border border-slate-700': selectedTrackType !== opt.value
              }"
              @click="setTrackType(opt.value)"
            >
              <span class="mr-1">{{ opt.icon }}</span>
              {{ opt.label }}
            </button>
          </div>

          <span class="text-slate-600">|</span>

          <!-- 文件类型筛选 -->
          <span class="text-slate-400 text-sm font-medium shrink-0">文件:</span>
          <div class="flex gap-2 flex-wrap">
            <button
              v-for="opt in fileTypeOptions"
              :key="opt.value"
              class="px-3.5 py-1.5 rounded-lg text-sm font-medium transition-all duration-200"
              :class="{
                'bg-blue-600 text-white shadow-md': selectedFileType === opt.value,
                'bg-slate-800 text-slate-300 hover:bg-slate-700 border border-slate-700': selectedFileType !== opt.value
              }"
              @click="setFileType(opt.value)"
            >
              <span class="mr-1">{{ opt.icon }}</span>
              {{ opt.label }}
            </button>
          </div>

          <!-- 清除所有过滤 -->
          <button
            v-if="hasActiveFilters()"
            class="ml-auto text-xs text-slate-500 hover:text-white transition flex items-center gap-1"
            @click="clearFilters"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
            清除过滤
          </button>
        </div>

        <!-- 热门标签 -->
        <div class="flex items-center gap-3 flex-wrap">
          <span class="text-slate-400 text-sm font-medium shrink-0">热门:</span>
          <div class="flex gap-2 flex-wrap">
            <button
              v-for="tag in popularTags"
              :key="tag"
              class="px-3 py-1 rounded-full text-xs font-medium transition-all duration-200"
              :class="{
                'bg-purple-600 text-white': selectedTag === tag,
                'bg-slate-800/60 text-slate-400 hover:bg-slate-700 hover:text-slate-200 border border-slate-700/50': selectedTag !== tag
              }"
              @click="toggleTag(tag)"
            >
              {{ tag }}
            </button>
          </div>
        </div>
      </div>

      <!-- 统计 -->
      <div class="flex items-center justify-between mb-6">
        <p class="text-slate-400 text-sm">
          <template v-if="isAiResult">
            <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-purple-500/10 border border-purple-500/20 text-purple-300 text-xs font-medium mr-2">
              ✨ AI Vibe Search
            </span>
            找到 <span class="text-white font-semibold">{{ totalTracks }}</span> 个最匹配的结果
          </template>
          <template v-else>
            共 <span class="text-white font-semibold">{{ totalTracks }}</span> 个作品
            <span v-if="isUsingAdvancedSearch" class="text-purple-400 ml-2">
              ⚡ ES 搜索
            </span>
            <span v-if="selectedTrackType" class="text-teal-400 ml-2">
              类型: {{ selectedTrackType === 'PACK' ? '合集' : '单文件' }}
            </span>
            <span v-if="selectedTag" class="text-purple-400 ml-2">
              标签: {{ selectedTag }}
            </span>
          </template>
        </p>
      </div>

      <!-- 加载状态 -->
      <div v-if="isLoading" class="flex flex-col items-center justify-center py-20">
        <svg class="animate-spin h-12 w-12 mb-4" :class="isAiSearching ? 'text-purple-400' : 'text-blue-400'" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        <p v-if="isAiSearching" class="text-purple-300">✨ AI 正在分析你的描述并匹配音频特征...</p>
        <p v-else class="text-slate-400">加载中...</p>
      </div>

      <!-- 错误提示 -->
      <div v-else-if="errorMessage" class="flex flex-col items-center justify-center py-20">
        <div class="text-5xl mb-4">😵</div>
        <p class="text-red-400 mb-4">{{ errorMessage }}</p>
        <button class="px-6 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition" @click="loadTracks">
          重试
        </button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="tracks.length === 0" class="flex flex-col items-center justify-center py-20">
        <template v-if="isAiResult">
          <div class="text-6xl mb-4">🔮</div>
          <h3 class="text-xl font-semibold text-white mb-2">没有找到匹配的 Vibe</h3>
          <p class="text-slate-400 mb-6 max-w-md text-center">
            AI 暂时未找到与你描述匹配的音频，试试换一种方式描述，例如更具体的乐器、风格或情感氛围。
          </p>
          <div class="flex gap-3">
            <button
              class="px-5 py-2.5 bg-purple-600 hover:bg-purple-500 text-white font-medium rounded-lg transition"
              @click="aiQuery = ''; isAiResult = false"
            >重新描述</button>
            <button
              class="px-5 py-2.5 bg-slate-700 hover:bg-slate-600 text-white font-medium rounded-lg transition"
              @click="toggleAiSearchMode()"
            >切换到标准搜索</button>
          </div>
        </template>
        <template v-else>
          <div class="text-6xl mb-4">🎵</div>
          <h3 class="text-xl font-semibold text-white mb-2">还没有公开作品</h3>
          <p class="text-slate-400 mb-6">成为第一个发布作品的人吧！</p>
          <router-link
            v-if="isLoggedIn()"
            to="/publish"
            class="px-6 py-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition duration-200 shadow-lg"
          >
            发布第一个作品
          </router-link>
        </template>
      </div>

      <!-- 作品网格 -->
      <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        <div
          v-for="track in tracks"
          :key="track.id"
          class="rounded-xl overflow-hidden shadow-lg hover:shadow-2xl transition-all duration-300 group cursor-pointer"
          :class="isAiResult
            ? 'bg-slate-800 border border-purple-500/30 hover:border-purple-400/50 hover:shadow-purple-500/10'
            : 'bg-slate-800 border border-slate-700 hover:border-slate-600'"
          @click="openTrackDetail(track.id)"
        >
          <!-- 封面区域 -->
          <div class="relative aspect-square overflow-hidden">
            <img
              v-if="track.coverId"
              :src="getAssetFileUrl(track.coverId)"
              :alt="track.title"
              class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              loading="lazy"
            />
            <div
              v-else
              class="w-full h-full bg-gradient-to-br flex items-center justify-center"
              :class="getCoverGradient(track)"
            >
              <span class="text-5xl opacity-30 group-hover:opacity-50 transition-opacity duration-300">
                {{ getTrackIcon(track) }}
              </span>
            </div>

            <!-- 悬停播放按钮（仅 SINGLE 音频） -->
            <div
              v-if="canPreviewOnCard(track)"
              class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center"
            >
              <div class="h-14 w-14 bg-white/20 backdrop-blur-sm rounded-full flex items-center justify-center cursor-pointer hover:bg-white/30 transition">
                <svg class="w-7 h-7 text-white ml-1" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M8 5v14l11-7z"></path>
                </svg>
              </div>
            </div>

            <!-- 作品类型角标 -->
            <div class="absolute top-3 left-3 flex flex-col gap-1.5">
              <span
                class="px-2 py-0.5 rounded-full text-xs font-bold backdrop-blur-sm"
                :class="getTrackTypeBadgeClass(track)"
              >
                {{ getTrackTypeBadge(track) }}
              </span>
              <span
                v-if="isAiResult"
                class="px-2 py-0.5 rounded-full text-[11px] font-bold backdrop-blur-sm bg-gradient-to-r from-purple-600/90 to-fuchsia-600/90 text-white shadow-sm shadow-purple-500/20"
              >
                ✨ AI Match
              </span>
            </div>

            <!-- 价格标签 -->
            <div class="absolute top-3 right-3">
              <span
                class="px-2.5 py-1 rounded-full text-xs font-bold backdrop-blur-sm"
                :class="track.price ? 'bg-green-500/80 text-white' : 'bg-slate-900/60 text-slate-300'"
              >
                {{ formatPrice(track.price) }}
              </span>
            </div>

            <!-- 预览能力角标 -->
            <div class="absolute bottom-3 right-3">
              <span
                class="px-2 py-0.5 rounded-full text-[11px] font-semibold backdrop-blur-sm"
                :class="isTrackPreviewable(track) ? 'bg-blue-500/80 text-white' : 'bg-slate-900/70 text-slate-300'"
              >
                {{ isTrackPreviewable(track) ? '可预览' : '不可预览' }}
              </span>
            </div>
          </div>

          <!-- 信息区域 -->
          <div class="p-4">
            <h3 class="text-white font-semibold text-sm truncate mb-1 group-hover:text-blue-400 transition-colors">
              {{ track.title }}
            </h3>
            <p class="text-slate-400 text-xs mb-2">
              {{ track.producerName || `Producer #${track.producerId}` }}
            </p>

            <!-- 描述预览 -->
            <p v-if="track.description" class="text-slate-500 text-xs line-clamp-2 mb-2">
              {{ track.description }}
            </p>

            <!-- 已售 / 库存 -->
            <div class="flex items-center gap-3 text-xs mb-2">
              <span v-if="track.soldCount > 0" class="text-orange-400 flex items-center gap-1">
                <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                </svg>
                已售 {{ track.soldCount }}
              </span>
              <span
                v-if="track.stock !== null && track.stock !== undefined"
                class="flex items-center gap-1"
                :class="track.stock > 0 ? 'text-emerald-400' : 'text-red-400'"
              >
                <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
                {{ track.stock > 0 ? `库存 ${track.stock}` : '已售罄' }}
              </span>
            </div>

            <!-- 标签行 -->
            <div v-if="track.tags || track.autoTags" class="flex flex-wrap gap-1.5">
              <span
                v-for="tag in parseTags(track.tags)"
                :key="'u-' + tag"
                class="px-2 py-0.5 bg-slate-700/60 text-slate-300 rounded text-xs cursor-pointer hover:bg-blue-500/20 hover:text-blue-300 transition"
                @click.stop="toggleTag(tag)"
              >
                {{ tag }}
              </span>
              <span
                v-for="tag in parseAutoTags(track.autoTags)"
                :key="'ai-' + tag"
                class="px-2 py-0.5 bg-purple-500/15 text-purple-300 rounded text-xs border border-purple-500/30 flex items-center gap-1"
              >
                <svg class="w-3 h-3 shrink-0 text-purple-400" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L9.19 8.63L2 9.24l5.46 4.73L5.82 21L12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2z"/></svg>
                {{ tag }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页（AI 搜索结果不分页） -->
      <div v-if="totalPages > 1 && !isAiResult" class="mt-10 flex items-center justify-center gap-2">
        <button
          :disabled="currentPage <= 1"
          class="px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-300 hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
          @click="goToPage(currentPage - 1)"
        >上一页</button>

        <div class="flex items-center gap-1">
          <template v-for="page in totalPages" :key="page">
            <button
              v-if="page === 1 || page === totalPages || (page >= currentPage - 2 && page <= currentPage + 2)"
              class="w-10 h-10 rounded-lg text-sm font-medium transition"
              :class="{
                'bg-blue-600 text-white': page === currentPage,
                'bg-slate-800 border border-slate-700 text-slate-300 hover:bg-slate-700': page !== currentPage
              }"
              @click="goToPage(page)"
            >
              {{ page }}
            </button>
            <span
              v-else-if="page === currentPage - 3 || page === currentPage + 3"
              class="text-slate-500 px-1"
            >...</span>
          </template>
        </div>

        <button
          :disabled="currentPage >= totalPages"
          class="px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-300 hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
          @click="goToPage(currentPage + 1)"
        >下一页</button>
      </div>

      <!-- 页脚 -->
      <div class="mt-12 pb-8 text-center">
        <p class="text-slate-600 text-sm">🎵 SoundVibe — 音乐资产交易平台</p>
      </div>
    </main>

    <!-- ========== 作品详情弹层 ========== -->
    <div
      v-if="selectedTrack || isDetailLoading || detailErrorMessage"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      <div class="absolute inset-0 bg-black/70 backdrop-blur-sm" @click="closeTrackDetail"></div>
      <div class="relative w-full max-w-3xl bg-slate-800 border border-slate-700 rounded-2xl shadow-2xl overflow-hidden max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between px-6 py-4 border-b border-slate-700 sticky top-0 bg-slate-800/95 backdrop-blur">
          <h3 class="text-lg font-semibold text-white">作品详情</h3>
          <button
            class="h-8 w-8 rounded-lg text-slate-400 hover:text-white hover:bg-slate-700 transition"
            @click="closeTrackDetail"
          >
            <svg class="w-5 h-5 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>

        <div class="p-6">
          <!-- 加载中 -->
          <div v-if="isDetailLoading" class="flex flex-col items-center justify-center py-14">
            <svg class="animate-spin h-10 w-10 text-blue-400 mb-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            <p class="text-slate-400 text-sm">正在加载作品详情...</p>
          </div>

          <!-- 加载失败 -->
          <div v-else-if="detailErrorMessage" class="text-center py-12">
            <p class="text-red-400 mb-4">{{ detailErrorMessage }}</p>
            <button class="px-5 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition" @click="closeTrackDetail">
              关闭
            </button>
          </div>

          <!-- 详情内容 -->
          <div v-else-if="selectedTrack" class="space-y-6">
            <!-- 上半区：封面 + 元数据 -->
            <div class="grid grid-cols-1 md:grid-cols-[220px_1fr] gap-6">
              <div class="aspect-square rounded-xl overflow-hidden bg-slate-700">
                <img
                  v-if="selectedTrack.coverId"
                  :src="getAssetFileUrl(selectedTrack.coverId)"
                  :alt="selectedTrack.title"
                  class="w-full h-full object-cover"
                />
                <div
                  v-else
                  class="w-full h-full bg-gradient-to-br flex items-center justify-center"
                  :class="getCoverGradient(selectedTrack)"
                >
                  <span class="text-6xl opacity-40">{{ getTrackIcon(selectedTrack) }}</span>
                </div>
              </div>

              <div class="min-w-0">
                <div class="flex items-center gap-2 mb-2 flex-wrap">
                  <span
                    class="px-2 py-0.5 rounded-full text-xs font-bold"
                    :class="getTrackTypeBadgeClass(selectedTrack)"
                  >
                    {{ getTrackTypeBadge(selectedTrack) }}
                  </span>
                  <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-700 text-slate-200">
                    {{ formatPrice(selectedTrack.price) }}
                  </span>
                  <span v-if="selectedTrack.soldCount > 0" class="px-2 py-0.5 rounded-full text-xs font-semibold bg-orange-500/20 text-orange-300">
                    已售 {{ selectedTrack.soldCount }}
                  </span>
                  <span
                    v-if="selectedTrack.stock !== null && selectedTrack.stock !== undefined"
                    class="px-2 py-0.5 rounded-full text-xs font-semibold"
                    :class="selectedTrack.stock > 0 ? 'bg-emerald-500/20 text-emerald-300' : 'bg-red-500/20 text-red-300'"
                  >
                    {{ selectedTrack.stock > 0 ? `库存 ${selectedTrack.stock}` : '已售罄' }}
                  </span>
                </div>
                <h4 class="text-2xl font-bold text-white mb-2 break-words">
                  {{ selectedTrack.title }}
                </h4>
                <p class="text-slate-400 text-sm mb-4">
                  by {{ selectedTrack.producerName || `Producer #${selectedTrack.producerId}` }}
                </p>
                <p v-if="selectedTrack.description" class="text-slate-300 leading-relaxed mb-4 whitespace-pre-wrap">
                  {{ selectedTrack.description }}
                </p>
                <p v-else class="text-slate-500 text-sm mb-4">发布者暂无作品描述</p>
                <div v-if="selectedTrack.tags || selectedTrack.autoTags" class="flex flex-wrap gap-2">
                  <span
                    v-for="tag in parseTags(selectedTrack.tags)"
                    :key="'u-' + tag"
                    class="px-2.5 py-1 rounded-full text-xs bg-slate-700/80 text-slate-200"
                  >
                    {{ tag }}
                  </span>
                  <span
                    v-for="tag in parseAutoTags(selectedTrack.autoTags)"
                    :key="'ai-' + tag"
                    class="px-2.5 py-1 rounded-full text-xs bg-purple-500/15 text-purple-300 border border-purple-500/30 flex items-center gap-1"
                  >
                    <svg class="w-3 h-3 shrink-0 text-purple-400" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L9.19 8.63L2 9.24l5.46 4.73L5.82 21L12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2z"/></svg>
                    {{ tag }}
                  </span>
                </div>
              </div>
            </div>

            <!-- ========== SINGLE: 音频试听 ========== -->
            <div v-if="selectedTrack.trackType === 'SINGLE' && selectedTrack.fileId && selectedTrack.fileType !== 'MIDI'" class="rounded-xl border border-slate-700 bg-slate-900/40 p-4">
              <!-- 付费且不允许预览（未购买时） -->
              <div v-if="isSelectedTrackPaid && !selectedTrack.allowPreview && !hasPurchased" class="text-center py-6">
                <div class="text-4xl mb-3">🔒</div>
                <p class="text-slate-400 text-sm">发布者未开放预览，购买后即可收听完整内容</p>
              </div>
              <!-- 可预览 -->
              <template v-else>
              <h5 class="text-white font-semibold mb-3 flex items-center gap-2">
                🎵 音频预览
                  <span v-if="hasPurchased" class="text-xs font-normal text-green-400/80">(已购买 · 完整播放)</span>
                  <span v-else-if="isSelectedTrackPaid" class="text-xs font-normal text-amber-400/80">(试听前 {{ selectedTrack.previewDuration || 30 }} 秒)</span>
                <span v-else class="text-xs font-normal text-green-400/80">(完整播放)</span>
              </h5>
              <AudioPreview
                :src="getAssetFileUrl(selectedTrack.fileId)"
                  :is-paid="isSelectedTrackPaid && !hasPurchased"
                  :preview-limit="selectedTrack.previewDuration || 30"
              />
              </template>
            </div>

            <!-- ========== SINGLE: MIDI 试听 ========== -->
            <div v-if="selectedTrack.trackType === 'SINGLE' && selectedTrack.fileId && selectedTrack.fileType === 'MIDI'" class="rounded-xl border border-slate-700 bg-slate-900/40 p-4">
              <!-- 付费且不允许预览（未购买时） -->
              <div v-if="isSelectedTrackPaid && !selectedTrack.allowPreview && !hasPurchased" class="text-center py-6">
                <div class="text-4xl mb-3">🔒</div>
                <p class="text-slate-400 text-sm">发布者未开放预览，购买后即可收听完整内容</p>
              </div>
              <!-- 可预览 -->
              <template v-else>
              <h5 class="text-white font-semibold mb-3 flex items-center gap-2">
                🎹 MIDI 预览
                  <span v-if="hasPurchased" class="text-xs font-normal text-green-400/80">(已购买 · 完整播放)</span>
                  <span v-else-if="isSelectedTrackPaid" class="text-xs font-normal text-amber-400/80">(试听前 {{ selectedTrack.previewDuration || 30 }} 秒)</span>
                <span v-else class="text-xs font-normal text-green-400/80">(完整播放)</span>
              </h5>
              <MidiPlayer
                :src="getAssetFileUrl(selectedTrack.fileId)"
                  :is-paid="isSelectedTrackPaid && !hasPurchased"
                  :preview-limit="selectedTrack.previewDuration || 30"
              />
              </template>
            </div>

            <!-- ========== PACK: 合集文件列表（含预览） ========== -->
            <div v-if="selectedTrack.trackType === 'PACK'" class="rounded-xl border border-slate-700 bg-slate-900/40 p-4">
              <div class="flex items-center justify-between mb-3">
                <h5 class="text-white font-semibold flex items-center gap-2">
                📦 合集内容
                <span class="text-sm font-normal text-slate-400">({{ selectedTrack.fileCount }} 个文件)</span>
                  <span v-if="hasPurchased" class="text-xs font-normal text-green-400/80 ml-1">已购买 · 完整访问</span>
                  <span v-else-if="isSelectedTrackPaid && selectedTrack.allowPreview" class="text-xs font-normal text-amber-400/80 ml-1">
                    可预览文件试听前 {{ selectedTrack.previewDuration || 30 }} 秒
                  </span>
              </h5>
                <!-- 已购买时显示"下载全部"按钮 -->
                <button
                  v-if="hasPurchased && selectedTrack.files && selectedTrack.files.length > 0"
                  class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium rounded-lg transition flex items-center gap-1.5"
                  @click="downloadAllPackFiles"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                  </svg>
                  下载全部
                </button>
              </div>

              <!-- 有文件列表时展示 -->
              <div v-if="selectedTrack.files && selectedTrack.files.length > 0" class="space-y-2 max-h-[420px] overflow-y-auto pr-1">
                <div
                  v-for="(file, index) in selectedTrack.files"
                  :key="file.id"
                  class="bg-slate-800/60 rounded-lg overflow-hidden"
                >
                  <!-- 文件信息行 -->
                  <div class="flex items-center gap-3 p-3">
                  <span class="text-xs text-slate-500 w-6 text-center shrink-0">{{ index + 1 }}</span>
                  <div
                    class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
                      :class="String(file.fileType).toUpperCase() === 'MIDI' ? 'bg-amber-500/20' : 'bg-purple-500/20'"
                  >
                      <span class="text-sm">{{ String(file.fileType).toUpperCase() === 'MIDI' ? '🎹' : '🎵' }}</span>
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-sm text-white truncate">{{ file.originalName || `文件 #${file.id}` }}</p>
                      <p class="text-xs text-slate-500">
                        {{ file.fileType }}
                        <span v-if="hasPurchased" class="text-green-400 ml-1">· 已购买</span>
                        <span v-else-if="String(file.fileType).toUpperCase() === 'AUDIO' && canPreviewPackFile(file)" class="text-green-400 ml-1">· 可试听</span>
                        <span v-else-if="String(file.fileType).toUpperCase() === 'AUDIO' && file.allowPreview === false" class="text-slate-600 ml-1">· 不可预览</span>
                      </p>
                    </div>
                    <!-- 已购买时的下载按钮 -->
                    <button
                      v-if="hasPurchased"
                      class="w-8 h-8 rounded-full flex items-center justify-center bg-slate-700 text-slate-300 hover:bg-blue-600/60 hover:text-white transition shrink-0"
                      title="下载此文件"
                      @click.stop="downloadFile(file.assetId, file.originalName || undefined)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                      </svg>
                    </button>
                    <!-- 播放/暂停按钮（仅音频且允许预览） -->
                    <button
                      v-if="String(file.fileType).toUpperCase() === 'AUDIO' && canPreviewPackFile(file)"
                      class="w-8 h-8 rounded-full flex items-center justify-center transition shrink-0"
                      :class="packPreviewingFileId === file.assetId
                        ? 'bg-purple-600 text-white'
                        : 'bg-slate-700 text-slate-300 hover:bg-purple-600/60 hover:text-white'"
                      @click.stop="togglePackFileAudioPreview(file.assetId)"
                      :title="packPreviewingFileId === file.assetId ? '收起播放器' : '试听'"
                    >
                      <svg v-if="packPreviewingFileId !== file.assetId" class="w-4 h-4 ml-0.5" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M8 5v14l11-7z"></path>
                      </svg>
                      <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                      </svg>
                    </button>
                    <!-- 锁定图标（未购买 + 不可预览） -->
                    <div
                      v-else-if="!hasPurchased && String(file.fileType).toUpperCase() === 'AUDIO' && file.allowPreview === false"
                      class="w-8 h-8 rounded-full flex items-center justify-center bg-slate-700/50 shrink-0"
                      title="发布者未开放此文件预览"
                    >
                      <span class="text-xs">🔒</span>
                    </div>
                  </div>

                  <!-- 内嵌音频播放器 -->
                  <div v-if="packPreviewingFileId === file.assetId" class="px-3 pb-3">
                    <AudioPreview
                      :src="getAssetFileUrl(file.assetId)"
                      :is-paid="isSelectedTrackPaid && !hasPurchased"
                      :preview-limit="selectedTrack.previewDuration || 30"
                    />
                  </div>
                </div>
              </div>

              <!-- 没有文件列表时的占位 -->
              <div v-else class="text-center py-6">
                <p class="text-slate-500 text-sm">该合集包含 {{ selectedTrack.fileCount }} 个文件</p>
                <p class="text-slate-600 text-xs mt-1">购买后可查看完整文件列表并下载</p>
              </div>

              <p v-if="!hasPurchased" class="mt-4 text-xs text-slate-400">
                说明：合集为可交易商品，购买后可下载全部文件。
              </p>
            </div>

            <!-- 时间信息 -->
            <div class="flex flex-wrap items-center gap-3 text-sm text-slate-400">
              <span>创建时间：{{ formatDateTime(selectedTrack.createTime) }}</span>
              <span>更新时间：{{ formatDateTime(selectedTrack.updateTime) }}</span>
            </div>

            <!-- ========== 操作按钮区域 ========== -->
            <div class="flex items-center gap-3 pt-2">
              <!-- 已购买状态 -->
              <div
                v-if="hasPurchased"
                class="flex-1 px-5 py-3 bg-slate-700 text-green-400 font-semibold rounded-lg flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                已购买
              </div>

              <!-- 已售罄状态 -->
              <div
                v-else-if="isOutOfStock"
                class="flex-1 px-5 py-3 bg-slate-700 text-red-400 font-semibold rounded-lg flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
                </svg>
                已售罄
              </div>

              <!-- 付费作品：加入购物车 -->
              <template v-else-if="isSelectedTrackPaid">
                <!-- 已在购物车中 -->
                <router-link
                  v-if="cartStore.isInCart(selectedTrack.id)"
                  to="/cart"
                  class="flex-1 px-5 py-3 bg-slate-700 text-blue-400 font-semibold rounded-lg flex items-center justify-center gap-2 hover:bg-slate-600 transition"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                  </svg>
                  已在购物车 · 去结算
                </router-link>
                <!-- 加入购物车 -->
                <button
                  v-else
                  class="flex-1 px-5 py-3 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-500 hover:to-emerald-500 text-white font-semibold rounded-lg transition shadow-lg shadow-green-600/20 flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
                  :disabled="isAddingToCart || isCheckingPurchase || !isLoggedIn()"
                  @click="handleAddToCart"
                >
                  <svg v-if="isAddingToCart" class="animate-spin w-5 h-5" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                  </svg>
                  <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 100 4 2 2 0 000-4z" />
                  </svg>
                  {{ isAddingToCart ? '添加中...' : `加入购物车 ${formatPrice(selectedTrack.price)}` }}
                </button>
              </template>

              <!-- 免费作品：直接获取（保留旧流程） -->
              <button
                v-else
                class="flex-1 px-5 py-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-semibold rounded-lg transition shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
                :disabled="isPurchasing || isCheckingPurchase || !isLoggedIn()"
                @click="handleFreePurchase"
              >
                <svg v-if="isPurchasing" class="animate-spin w-5 h-5" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                </svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
                {{ isPurchasing ? '处理中...' : '免费获取' }}
              </button>

              <button
                class="px-5 py-3 bg-slate-700 hover:bg-slate-600 text-white rounded-lg transition"
                @click="closeTrackDetail"
              >关闭</button>
            </div>
            <p v-if="!isLoggedIn()" class="text-xs text-amber-400/80 text-center pt-1">
              请先 <router-link to="/login" class="underline hover:text-amber-300">登录</router-link> 后再购买
            </p>
            <p v-else-if="!hasPurchased && isSelectedTrackPaid" class="text-xs text-slate-500 text-center pt-1">
              💡 付费作品请加入购物车后统一结算，订单创建后 15 分钟内需完成支付
            </p>
            <p v-else-if="!hasPurchased" class="text-xs text-slate-500 text-center pt-1">
              💡 免费作品可直接获取（无需购物车）
            </p>

            <!-- ========== 已购买 — 下载区域 ========== -->
            <div v-if="hasPurchased" class="rounded-xl border border-green-700/40 bg-green-900/10 p-4 mt-2">
              <h5 class="text-white font-semibold mb-3 flex items-center gap-2">
                <svg class="w-5 h-5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                </svg>
                下载
              </h5>
              <!-- SINGLE: 单文件下载 -->
              <div v-if="selectedTrack.trackType === 'SINGLE' && selectedTrack.fileId">
                <button
                  class="w-full px-4 py-3 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-lg transition flex items-center justify-center gap-2"
                  @click="downloadFile(selectedTrack.fileId!, selectedTrack.title)"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                  </svg>
                  下载 {{ selectedTrack.fileType === 'MIDI' ? 'MIDI' : '音频' }} 文件
                </button>
              </div>
              <!-- PACK: 合集下载 -->
              <div v-if="selectedTrack.trackType === 'PACK' && selectedTrack.files && selectedTrack.files.length > 0">
                <button
                  class="w-full px-4 py-3 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-lg transition flex items-center justify-center gap-2 mb-3"
                  @click="downloadAllPackFiles"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                  </svg>
                  下载全部 ({{ selectedTrack.files.length }} 个文件)
                </button>
                <p class="text-xs text-slate-500 text-center">也可在上方文件列表中单独下载每个文件</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 高级筛选面板展开/收起动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-8px);
}
.slide-down-enter-to,
.slide-down-leave-from {
  opacity: 1;
  max-height: 300px;
  transform: translateY(0);
}
</style>

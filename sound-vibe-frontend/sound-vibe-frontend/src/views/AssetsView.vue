<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { assetApi, downloadAssetFile } from '@/api/asset'
import { catalogApi, getAssetFileUrl } from '@/api/catalog'
import type { Asset, AssetType } from '@/types/asset'
import type { PurchaseItem, Track } from '@/types/catalog'
import UploadZone from '@/components/UploadZone.vue'
import MyTracksPanel from '@/components/MyTracksPanel.vue'
import AudioPreview from '@/components/AudioPreview.vue'

// ========== 路由 & Store ==========
const router = useRouter()
const userStore = useUserStore()

// ========== Tab 管理 ==========
type TabKey = 'files' | 'tracks' | 'purchases'
const activeTab = ref<TabKey>('files')

const tabs: { key: TabKey; label: string; icon: string }[] = [
  { key: 'files', label: '我的文件', icon: '📁' },
  { key: 'tracks', label: '我的作品', icon: '🎵' },
  { key: 'purchases', label: '已购作品', icon: '🛒' }
]

// ========== 文件资产状态 ==========
const assets = ref<Asset[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const isLoading = ref(false)
const activeFilter = ref<AssetType | ''>('')
const searchKeyword = ref('')
const showUploadPanel = ref(false)

/** 正在编辑重命名的资产 */
const renamingAsset = ref<string | null>(null)
const renameInput = ref('')
const renameInputRef = ref<HTMLInputElement | null>(null)

/** 正在删除确认的资产 */
const deletingAsset = ref<string | null>(null)

/** 当前展开预览的资产 */
const previewingAsset = ref<string | null>(null)

// ========== 计算属性 ==========
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

// ========== 生命周期 ==========
onMounted(() => {
  fetchAssets()
})

// ========== 方法 ==========

const fetchAssets = async () => {
  isLoading.value = true
  try {
    const result = await assetApi.list({
      current: currentPage.value,
      size: pageSize.value,
      type: activeFilter.value || undefined,
      keyword: searchKeyword.value || undefined
    })
    assets.value = result.records
    total.value = result.total
  } catch (err: any) {
    console.error('[Assets] 获取列表失败:', err)
  } finally {
    isLoading.value = false
  }
}

const setFilter = (type: AssetType | '') => {
  activeFilter.value = type
  currentPage.value = 1
  previewingAsset.value = null
  fetchAssets()
}

const handleSearch = () => {
  currentPage.value = 1
  previewingAsset.value = null
  fetchAssets()
}

const goToPage = (page: number) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  previewingAsset.value = null
  fetchAssets()
}

const onAssetUploaded = (_asset: Asset) => {
  fetchAssets()
}

/** 切换预览面板 */
const togglePreview = (assetCode: string) => {
  previewingAsset.value = previewingAsset.value === assetCode ? null : assetCode
}

/** 判断是否为图片类型 */
const isImage = (asset: Asset): boolean => {
  return asset.type === 'IMAGE' || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'].includes(asset.extension.toLowerCase())
}

/** 判断是否为音频类型 */
const isAudio = (asset: Asset): boolean => {
  return asset.type === 'AUDIO' || ['mp3', 'wav', 'flac', 'ogg', 'aac', 'm4a', 'wma'].includes(asset.extension.toLowerCase())
}

/** 判断是否为 MIDI 类型 */
const isMidi = (asset: Asset): boolean => {
  return asset.type === 'MIDI' || ['mid', 'midi'].includes(asset.extension.toLowerCase())
}

/** 开始重命名 */
const startRename = (asset: Asset) => {
  renamingAsset.value = asset.assetCode
  renameInput.value = asset.originalName
  nextTick(() => {
    renameInputRef.value?.focus()
    renameInputRef.value?.select()
  })
}

const confirmRename = async (assetCode: string) => {
  if (!renameInput.value.trim()) return
  try {
    await assetApi.rename(assetCode, renameInput.value.trim())
    renamingAsset.value = null
    fetchAssets()
  } catch (err: any) {
    console.error('[Assets] 重命名失败:', err)
    alert('重命名失败: ' + err.message)
  }
}

const cancelRename = () => {
  renamingAsset.value = null
  renameInput.value = ''
}

const requestDelete = (assetCode: string) => {
  deletingAsset.value = assetCode
}

const confirmDelete = async (assetCode: string) => {
  try {
    await assetApi.delete(assetCode)
    deletingAsset.value = null
    // 如果正在预览被删除的资产，关闭预览
    if (previewingAsset.value === assetCode) {
      previewingAsset.value = null
    }
    fetchAssets()
  } catch (err: any) {
    console.error('[Assets] 删除失败:', err)
    alert('删除失败: ' + err.message)
  }
}

const cancelDelete = () => {
  deletingAsset.value = null
}

const formatSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + units[i]
}

const formatDate = (dateStr: string): string => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const handleLogout = async () => {
  if (confirm('确定要退出登录吗？')) {
    await userStore.logout()
    router.push('/login')
  }
}

const getTypeStyle = (type: string) => {
  switch (type) {
    case 'AUDIO':
      return { icon: '🎵', bgClass: 'bg-purple-500/20', textClass: 'text-purple-300' }
    case 'IMAGE':
      return { icon: '🖼️', bgClass: 'bg-blue-500/20', textClass: 'text-blue-300' }
    case 'MIDI':
      return { icon: '🎹', bgClass: 'bg-amber-500/20', textClass: 'text-amber-300' }
    default:
      return { icon: '📄', bgClass: 'bg-slate-500/20', textClass: 'text-slate-300' }
  }
}

// ========== 已购作品 ==========
const purchases = ref<PurchaseItem[]>([])
const purchaseTotal = ref(0)
const purchasePage = ref(1)
const purchasePageSize = ref(20)
const purchaseLoading = ref(false)
const purchaseTotalPages = computed(() => Math.ceil(purchaseTotal.value / purchasePageSize.value))

const fetchPurchases = async () => {
  purchaseLoading.value = true
  try {
    const result = await catalogApi.listMyPurchases(purchasePage.value, purchasePageSize.value)
    purchases.value = result.records
    purchaseTotal.value = result.total
  } catch (err: any) {
    console.error('[Purchases] 获取已购列表失败:', err)
  } finally {
    purchaseLoading.value = false
  }
}

const goToPurchasePage = (page: number) => {
  if (page < 1 || page > purchaseTotalPages.value) return
  purchasePage.value = page
  fetchPurchases()
}

const formatPurchasePrice = (price: number | null): string => {
  if (price === null || price === 0) return '免费获取'
  return `¥${Number(price).toFixed(2)}`
}

const getTrackIcon = (purchase: PurchaseItem) => {
  if (!purchase.track) return '❓'
  if (purchase.track.trackType === 'PACK') return '📦'
  return purchase.track.fileType === 'MIDI' ? '🎹' : '🎵'
}

const getCoverGradient = (id: number) => {
  const colors = [
    'from-blue-600 to-purple-600',
    'from-pink-600 to-red-600',
    'from-green-600 to-teal-600',
    'from-orange-600 to-yellow-600',
    'from-indigo-600 to-blue-600',
    'from-purple-600 to-pink-600'
  ]
  return colors[id % colors.length]
}

// 当切换到"已购作品" tab 时自动加载
watch(activeTab, (tab) => {
  if (tab === 'purchases' && purchases.value.length === 0) {
    fetchPurchases()
  }
})

// ========== 已购作品详情弹层 ==========
const selectedPurchase = ref<PurchaseItem | null>(null)
const purchaseDetailTrack = ref<Track | null>(null)
const isPurchaseDetailLoading = ref(false)
const purchaseDetailError = ref('')
/** 当前正在预览的 PACK 文件 assetId */
const purchasePackPreviewingId = ref<number | null>(null)

/** 打开已购作品详情 */
const openPurchaseDetail = async (purchase: PurchaseItem) => {
  if (!purchase.track) return
  selectedPurchase.value = purchase
  purchaseDetailTrack.value = null
  isPurchaseDetailLoading.value = true
  purchaseDetailError.value = ''
  purchasePackPreviewingId.value = null

  try {
    // 获取完整详情（含 PACK 文件列表）
    purchaseDetailTrack.value = await catalogApi.getTrackDetail(purchase.trackId)
  } catch (err: any) {
    purchaseDetailError.value = err.message || '加载作品详情失败'
    console.error('[Purchases] 加载详情失败:', err)
  } finally {
    isPurchaseDetailLoading.value = false
  }
}

const closePurchaseDetail = () => {
  selectedPurchase.value = null
  purchaseDetailTrack.value = null
  purchasePackPreviewingId.value = null
}

const togglePurchasePackPreview = (assetId: number) => {
  purchasePackPreviewingId.value = purchasePackPreviewingId.value === assetId ? null : assetId
}

/** 下载文件（Blob 方式，不跳转页面） */
const downloadPurchaseFile = async (assetId: number, fileName?: string) => {
  try {
    await downloadAssetFile(assetId, fileName)
  } catch (err: any) {
    console.error('[AssetsView] 下载失败:', err)
    alert('下载失败，请稍后重试')
  }
}

/** 下载 PACK 所有文件 */
const downloadAllPurchasePackFiles = async () => {
  if (!purchaseDetailTrack.value?.files) return
  for (let i = 0; i < purchaseDetailTrack.value.files.length; i++) {
    const file = purchaseDetailTrack.value.files[i]
    try {
      await downloadAssetFile(file.assetId, file.originalName || `file-${i + 1}`)
    } catch (err) {
      console.error(`[AssetsView] 下载第 ${i + 1} 个文件失败:`, err)
    }
    if (i < purchaseDetailTrack.value.files.length - 1) {
      await new Promise(r => setTimeout(r, 300))
    }
  }
}

/** 已购详情中的辅助函数 */
const parseTags = (tags: string | null): string[] => {
  if (!tags) return []
  return tags.split(',').map(t => t.trim()).filter(Boolean).slice(0, 5)
}

const formatPurchaseDetailPrice = (price: number | null): string => {
  if (price === null || price === 0) return '免费'
  return `¥${Number(price).toFixed(2)}`
}
</script>

<template>
  <div class="min-h-screen bg-slate-900">
    <!-- 顶部导航栏 -->
    <nav class="bg-slate-800 border-b border-slate-700 shadow-lg">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-3">
            <router-link to="/" class="flex items-center space-x-3 hover:opacity-80 transition">
              <div class="h-10 w-10 bg-gradient-to-tr from-blue-500 to-purple-500 rounded-lg flex items-center justify-center shadow-lg">
                <span class="text-2xl">🎵</span>
              </div>
              <div>
                <h1 class="text-xl font-bold text-white">SoundVibe</h1>
                <p class="text-xs text-slate-400">工作台</p>
              </div>
            </router-link>
          </div>
          <div class="flex items-center space-x-4">
            <div class="flex items-center space-x-3">
              <div class="h-9 w-9 bg-gradient-to-br from-purple-400 to-pink-400 rounded-full flex items-center justify-center text-white font-bold shadow-md">
                {{ userStore.userInfo?.username?.charAt(0).toUpperCase() || '?' }}
              </div>
              <div class="hidden md:block">
                <p class="text-sm font-medium text-white">{{ userStore.userInfo?.username || '用户' }}</p>
              </div>
            </div>
            <button
              class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition duration-200"
              @click="handleLogout"
            >退出</button>
          </div>
        </div>
      </div>
    </nav>

    <!-- Tab 切换栏 -->
    <div class="bg-slate-800 border-b border-slate-700">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex gap-1">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="relative px-5 py-3.5 text-sm font-medium transition-all duration-200"
            :class="activeTab === tab.key
              ? 'text-white'
              : 'text-slate-400 hover:text-slate-200'"
            @click="activeTab = tab.key"
          >
            <span class="flex items-center gap-2">
              <span>{{ tab.icon }}</span>
              <span>{{ tab.label }}</span>
            </span>
            <!-- 活跃指示条 -->
            <div
              v-if="activeTab === tab.key"
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-500 rounded-t-full"
            ></div>
          </button>
        </div>
      </div>
    </div>

    <!-- 主内容 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- ===================== Tab: 文件资产 ===================== -->
      <div v-if="activeTab === 'files'">

        <!-- 标题栏 + 操作按钮 -->
        <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-6 gap-4">
          <div>
            <h2 class="text-2xl font-bold text-white">我的文件</h2>
            <p class="text-slate-400 text-sm mt-1">共 {{ total }} 个文件</p>
          </div>
          <button
            class="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-lg transition duration-200 flex items-center gap-2"
            @click="showUploadPanel = !showUploadPanel"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
            </svg>
            上传文件
          </button>
        </div>

        <!-- 上传面板 -->
        <Transition
          enter-active-class="transition duration-400 ease-out"
          enter-from-class="opacity-0 -translate-y-4"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition duration-300 ease-in"
          leave-from-class="opacity-100 translate-y-0"
          leave-to-class="opacity-0 -translate-y-4"
        >
          <div v-if="showUploadPanel" class="mb-6 bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg">
            <div class="flex items-center justify-between mb-4">
              <h3 class="text-lg font-semibold text-white">上传文件</h3>
              <button class="text-slate-400 hover:text-white" @click="showUploadPanel = false">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>
            <UploadZone @uploaded="onAssetUploaded" />
          </div>
        </Transition>

        <!-- 过滤 & 搜索栏 -->
        <div class="flex flex-col sm:flex-row gap-4 mb-6">
          <div class="flex gap-2">
            <button
              v-for="filter in [
                { label: '全部', value: '' },
                { label: '🎵 音频', value: 'AUDIO' },
                { label: '🖼️ 封面图', value: 'IMAGE' },
                { label: '🎹 MIDI', value: 'MIDI' }
              ]"
              :key="filter.value"
              class="px-4 py-2 rounded-lg text-sm font-medium transition duration-200"
              :class="activeFilter === filter.value
                ? 'bg-blue-600 text-white'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700 border border-slate-700'"
              @click="setFilter(filter.value as AssetType | '')"
            >
              {{ filter.label }}
            </button>
          </div>
          <div class="flex-1 flex gap-2">
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索文件名..."
              class="flex-1 px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-400 focus:outline-none focus:border-blue-500 text-sm"
              @keyup.enter="handleSearch"
            />
            <button
              class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-lg text-sm transition duration-200"
              @click="handleSearch"
            >搜索</button>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="isLoading" class="flex justify-center items-center py-20">
          <svg class="animate-spin h-8 w-8 text-blue-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        </div>

        <!-- 空状态 -->
        <div v-else-if="assets.length === 0" class="text-center py-20">
          <div class="text-6xl mb-4">📂</div>
          <p class="text-slate-400 text-lg">还没有上传任何文件</p>
          <p class="text-slate-500 text-sm mt-2">点击上方「上传文件」按钮开始</p>
        </div>

        <!-- 资产列表 -->
        <div v-else class="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden">
          <table class="w-full">
            <thead>
              <tr class="border-b border-slate-700">
                <th class="text-left px-6 py-4 text-sm font-medium text-slate-400">文件名</th>
                <th class="text-left px-6 py-4 text-sm font-medium text-slate-400 hidden md:table-cell">类型</th>
                <th class="text-left px-6 py-4 text-sm font-medium text-slate-400 hidden sm:table-cell">大小</th>
                <th class="text-left px-6 py-4 text-sm font-medium text-slate-400 hidden lg:table-cell">上传时间</th>
                <th class="text-right px-6 py-4 text-sm font-medium text-slate-400">操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="asset in assets" :key="asset.assetCode">
                <!-- 资产行 -->
                <tr
                  class="border-b border-slate-700/50 transition duration-150 cursor-pointer"
                  :class="previewingAsset === asset.assetCode ? 'bg-slate-750/80' : 'hover:bg-slate-750'"
                  @click="togglePreview(asset.assetCode)"
                >
                  <!-- 文件名 -->
                  <td class="px-6 py-4">
                    <div class="flex items-center gap-3">
                      <div
                        class="h-10 w-10 rounded-lg flex items-center justify-center shrink-0"
                        :class="getTypeStyle(asset.type).bgClass"
                      >
                        <span class="text-lg">{{ getTypeStyle(asset.type).icon }}</span>
                      </div>
                      <div class="min-w-0">
                        <!-- 重命名模式 -->
                        <div v-if="renamingAsset === asset.assetCode" class="flex items-center gap-2" @click.stop>
                          <input
                            ref="renameInputRef"
                            v-model="renameInput"
                            class="px-2 py-1 bg-slate-900 border border-blue-500 rounded text-white text-sm focus:outline-none w-48"
                            @keyup.enter="confirmRename(asset.assetCode)"
                            @keyup.escape="cancelRename"
                          />
                          <button
                            class="text-green-400 hover:text-green-300 text-sm font-bold"
                            @click.stop="confirmRename(asset.assetCode)"
                          >✓</button>
                          <button
                            class="text-red-400 hover:text-red-300 text-sm font-bold"
                            @click.stop="cancelRename"
                          >✕</button>
                        </div>
                        <!-- 正常显示 -->
                        <div v-else class="flex items-center gap-2">
                          <p class="text-white text-sm font-medium truncate max-w-[300px]" :title="asset.originalName">
                            {{ asset.originalName }}
                          </p>
                          <!-- 展开指示器 -->
                          <svg
                            class="w-3.5 h-3.5 text-slate-500 shrink-0 transition-transform duration-200"
                            :class="previewingAsset === asset.assetCode ? 'rotate-180' : ''"
                            fill="none" stroke="currentColor" viewBox="0 0 24 24"
                          >
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                          </svg>
                        </div>
                        <p class="text-slate-500 text-xs font-mono mt-0.5 md:hidden">
                          {{ asset.extension.toUpperCase() }} · {{ formatSize(asset.size) }}
                        </p>
                      </div>
                    </div>
                  </td>

                  <!-- 类型 -->
                  <td class="px-6 py-4 hidden md:table-cell">
                    <span
                      class="px-2.5 py-1 rounded text-xs font-medium"
                      :class="[getTypeStyle(asset.type).bgClass, getTypeStyle(asset.type).textClass]"
                    >
                      {{ asset.type }}
                    </span>
                  </td>

                  <!-- 大小 -->
                  <td class="px-6 py-4 hidden sm:table-cell">
                    <span class="text-slate-300 text-sm">{{ formatSize(asset.size) }}</span>
                  </td>

                  <!-- 时间 -->
                  <td class="px-6 py-4 hidden lg:table-cell">
                    <span class="text-slate-400 text-sm">{{ formatDate(asset.createTime) }}</span>
                  </td>

                  <!-- 操作 -->
                  <td class="px-6 py-4 text-right" @click.stop>
                    <!-- 删除确认状态 -->
                    <div v-if="deletingAsset === asset.assetCode" class="flex items-center justify-end gap-2">
                      <span class="text-red-400 text-xs">确认删除？</span>
                      <button
                        class="px-2 py-1 bg-red-600 hover:bg-red-500 text-white text-xs rounded transition"
                        @click="confirmDelete(asset.assetCode)"
                      >删除</button>
                      <button
                        class="px-2 py-1 bg-slate-700 hover:bg-slate-600 text-white text-xs rounded transition"
                        @click="cancelDelete"
                      >取消</button>
                    </div>
                    <!-- 正常操作按钮 -->
                    <div v-else class="flex items-center justify-end gap-1">
                      <!-- 预览按钮 -->
                      <button
                        class="p-2 transition"
                        :class="previewingAsset === asset.assetCode ? 'text-blue-400' : 'text-slate-400 hover:text-blue-400'"
                        title="预览"
                        @click="togglePreview(asset.assetCode)"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                        </svg>
                      </button>
                      <!-- 下载链接 -->
                      <button
                        class="p-2 text-slate-400 hover:text-green-400 transition"
                        title="下载"
                        @click.stop="downloadPurchaseFile(asset.id, asset.originalName)"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                        </svg>
                      </button>
                      <!-- 重命名 -->
                      <button
                        class="p-2 text-slate-400 hover:text-yellow-400 transition"
                        title="重命名"
                        @click="startRename(asset)"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                        </svg>
                      </button>
                      <!-- 删除 -->
                      <button
                        class="p-2 text-slate-400 hover:text-red-400 transition"
                        title="删除"
                        @click="requestDelete(asset.assetCode)"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>

                <!-- 内联预览面板 -->
                <tr v-if="previewingAsset === asset.assetCode">
                  <td colspan="5" class="px-0 py-0">
                    <div class="bg-slate-850 border-t border-b border-slate-600/30 px-6 py-5 animate-fade-in">
                      <div class="flex flex-col lg:flex-row gap-6">

                        <!-- 预览区域 -->
                        <div class="flex-1 min-w-0">
                          <!-- 图片预览 -->
                          <div v-if="isImage(asset)" class="flex justify-center">
                            <img
                              :src="asset.url"
                              :alt="asset.originalName"
                              class="max-h-80 max-w-full rounded-lg shadow-lg object-contain bg-slate-900/50"
                              loading="lazy"
                            />
                          </div>

                          <!-- 音频播放器 -->
                          <div v-else-if="isAudio(asset)" class="space-y-3">
                            <div class="flex items-center gap-3 mb-3">
                              <div class="h-14 w-14 bg-gradient-to-br from-purple-500/30 to-pink-500/30 rounded-xl flex items-center justify-center">
                                <span class="text-2xl">🎶</span>
                              </div>
                              <div class="min-w-0 flex-1">
                                <p class="text-white font-medium truncate">{{ asset.originalName }}</p>
                                <p class="text-slate-400 text-sm">{{ asset.extension.toUpperCase() }} · {{ formatSize(asset.size) }}</p>
                              </div>
                            </div>
                            <audio
                              :src="asset.url"
                              controls
                              preload="metadata"
                              class="w-full rounded-lg"
                              style="filter: invert(0.85) hue-rotate(180deg);"
                            >
                              你的浏览器不支持音频播放
                            </audio>
                          </div>

                          <!-- MIDI 文件信息 -->
                          <div v-else-if="isMidi(asset)" class="flex flex-col items-center justify-center py-8 text-slate-400">
                            <div class="h-16 w-16 bg-amber-500/20 rounded-xl flex items-center justify-center mb-3">
                              <span class="text-3xl">🎹</span>
                            </div>
                            <p class="text-sm font-medium text-white mb-1">{{ asset.originalName }}</p>
                            <p class="text-xs text-slate-500 mb-3">MIDI 文件无法在浏览器中预览</p>
                            <button
                              class="text-blue-400 hover:text-blue-300 text-sm underline"
                              @click="downloadPurchaseFile(asset.id, asset.originalName)"
                            >点击下载</button>
                          </div>

                          <!-- 不可预览 -->
                          <div v-else class="flex flex-col items-center justify-center py-8 text-slate-500">
                            <svg class="w-12 h-12 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
                            </svg>
                            <p class="text-sm">此文件类型不支持内联预览</p>
                            <button
                              class="mt-2 text-blue-400 hover:text-blue-300 text-sm underline"
                              @click="downloadPurchaseFile(asset.id, asset.originalName)"
                            >点击下载查看</button>
                          </div>
                        </div>

                        <!-- 详情信息 -->
                        <div class="lg:w-64 shrink-0 bg-slate-900/50 rounded-lg p-4 space-y-3">
                          <h4 class="text-white font-medium text-sm mb-3 flex items-center gap-2">
                            <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                            </svg>
                            文件详情
                          </h4>
                          <div class="space-y-2.5 text-sm">
                            <div class="flex justify-between">
                              <span class="text-slate-500">文件名</span>
                              <span class="text-slate-300 text-right truncate ml-2 max-w-[150px]" :title="asset.originalName">{{ asset.originalName }}</span>
                            </div>
                            <div class="flex justify-between">
                              <span class="text-slate-500">类型</span>
                              <span :class="getTypeStyle(asset.type).textClass">{{ asset.type }}</span>
                            </div>
                            <div class="flex justify-between">
                              <span class="text-slate-500">扩展名</span>
                              <span class="text-slate-300">.{{ asset.extension }}</span>
                            </div>
                            <div class="flex justify-between">
                              <span class="text-slate-500">大小</span>
                              <span class="text-slate-300">{{ formatSize(asset.size) }}</span>
                            </div>
                            <div class="flex justify-between">
                              <span class="text-slate-500">上传时间</span>
                              <span class="text-slate-300">{{ formatDate(asset.createTime) }}</span>
                            </div>
                            <div class="flex justify-between">
                              <span class="text-slate-500">资产编码</span>
                              <span class="text-slate-400 font-mono text-xs truncate ml-2 max-w-[130px]" :title="asset.assetCode">{{ asset.assetCode }}</span>
                            </div>
                          </div>
                          <div class="pt-3 border-t border-slate-700/50">
                            <button
                              class="flex items-center justify-center gap-2 w-full px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition"
                              @click="downloadPurchaseFile(asset.id, asset.originalName)"
                            >
                              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                              </svg>
                              下载文件
                            </button>
                          </div>
                        </div>

                      </div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>

          <!-- 分页 -->
          <div v-if="totalPages > 1" class="flex items-center justify-between px-6 py-4 border-t border-slate-700">
            <p class="text-sm text-slate-400">
              第 {{ currentPage }} / {{ totalPages }} 页，共 {{ total }} 条
            </p>
            <div class="flex gap-2">
              <button
                class="px-3 py-1.5 bg-slate-700 text-white text-sm rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-600"
                :disabled="currentPage <= 1"
                @click="goToPage(currentPage - 1)"
              >上一页</button>
              <button
                class="px-3 py-1.5 bg-slate-700 text-white text-sm rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-600"
                :disabled="currentPage >= totalPages"
                @click="goToPage(currentPage + 1)"
              >下一页</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ===================== Tab: 我的作品 ===================== -->
      <div v-else-if="activeTab === 'tracks'">
        <MyTracksPanel />
      </div>

      <!-- ===================== Tab: 已购作品 ===================== -->
      <div v-else-if="activeTab === 'purchases'">

        <!-- 标题栏 -->
        <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-6 gap-4">
          <div>
            <h2 class="text-2xl font-bold text-white">已购作品</h2>
            <p class="text-slate-400 text-sm mt-1">共 {{ purchaseTotal }} 个作品</p>
          </div>
          <router-link
            to="/"
            class="px-5 py-2.5 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition flex items-center gap-2 shadow-lg"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
            浏览市场
          </router-link>
        </div>

        <!-- 加载中 -->
        <div v-if="purchaseLoading" class="flex justify-center items-center py-20">
          <svg class="animate-spin h-8 w-8 text-blue-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        </div>

        <!-- 空状态 -->
        <div v-else-if="purchases.length === 0" class="text-center py-20">
          <div class="text-6xl mb-4">🛒</div>
          <h3 class="text-xl font-semibold text-white mb-2">暂无已购作品</h3>
          <p class="text-slate-400 mb-2">去市场发现你喜欢的音频和 MIDI 吧</p>
          <router-link
            to="/"
            class="inline-flex items-center gap-2 mt-4 px-6 py-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition shadow-lg"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
            浏览市场
          </router-link>
        </div>

        <!-- 已购作品列表 -->
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          <div
            v-for="purchase in purchases"
            :key="purchase.id"
            class="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden shadow-lg hover:shadow-2xl hover:border-slate-600 transition-all duration-300 group cursor-pointer"
            @click="openPurchaseDetail(purchase)"
          >
            <!-- 封面 -->
            <div class="relative aspect-square overflow-hidden">
              <img
                v-if="purchase.track?.coverId"
                :src="getAssetFileUrl(purchase.track.coverId)"
                :alt="purchase.track?.title"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                loading="lazy"
              />
              <div
                v-else
                class="w-full h-full bg-gradient-to-br flex items-center justify-center"
                :class="getCoverGradient(purchase.trackId)"
              >
                <span class="text-5xl opacity-30">{{ getTrackIcon(purchase) }}</span>
              </div>

              <!-- 已购标记 -->
              <div class="absolute top-3 left-3">
                <span class="px-2 py-0.5 rounded-full text-xs font-bold bg-green-500/80 text-white backdrop-blur-sm">
                  已购买
                </span>
              </div>

              <!-- 价格 -->
              <div class="absolute top-3 right-3">
                <span
                  class="px-2 py-0.5 rounded-full text-xs font-bold backdrop-blur-sm"
                  :class="purchase.pricePaid > 0 ? 'bg-green-500/80 text-white' : 'bg-slate-900/60 text-slate-300'"
                >
                  {{ formatPurchasePrice(purchase.pricePaid) }}
                </span>
              </div>

              <!-- 作品类型角标 -->
              <div v-if="purchase.track" class="absolute bottom-3 left-3">
                <span
                  class="px-2 py-0.5 rounded-full text-xs font-bold backdrop-blur-sm"
                  :class="purchase.track.trackType === 'PACK' ? 'bg-teal-500/80 text-white' : 'bg-purple-500/80 text-white'"
                >
                  {{ purchase.track.trackType === 'PACK' ? `📦 ${purchase.track.fileCount} 文件` : purchase.track.fileType === 'MIDI' ? '🎹 MIDI' : '🎵 Audio' }}
                </span>
              </div>
            </div>

            <!-- 信息 -->
            <div class="p-4">
              <h3 v-if="purchase.track" class="text-white font-semibold text-sm truncate mb-1">
                {{ purchase.track.title }}
              </h3>
              <h3 v-else class="text-slate-500 text-sm mb-1">
                作品已删除 (ID: {{ purchase.trackId }})
              </h3>
              <p v-if="purchase.track" class="text-slate-400 text-xs mb-2">
                {{ purchase.track.producerName || `Producer #${purchase.track.producerId}` }}
              </p>
              <p class="text-slate-500 text-xs">
                购买于 {{ formatDate(purchase.createTime) }}
              </p>

              <!-- 标签 -->
              <div v-if="purchase.track?.tags" class="flex flex-wrap gap-1 mt-2">
                <span
                  v-for="tag in purchase.track.tags.split(',').slice(0, 3)"
                  :key="tag"
                  class="px-2 py-0.5 bg-slate-700/60 text-slate-300 rounded text-xs"
                >
                  {{ tag.trim() }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="purchaseTotalPages > 1" class="mt-8 flex items-center justify-between">
          <p class="text-sm text-slate-400">
            第 {{ purchasePage }} / {{ purchaseTotalPages }} 页，共 {{ purchaseTotal }} 条
          </p>
          <div class="flex gap-2">
            <button
              class="px-3 py-1.5 bg-slate-700 text-white text-sm rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-600"
              :disabled="purchasePage <= 1"
              @click="goToPurchasePage(purchasePage - 1)"
            >上一页</button>
            <button
              class="px-3 py-1.5 bg-slate-700 text-white text-sm rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-600"
              :disabled="purchasePage >= purchaseTotalPages"
              @click="goToPurchasePage(purchasePage + 1)"
            >下一页</button>
          </div>
        </div>
      </div>

    </main>

    <!-- ========== 已购作品详情弹层 ========== -->
    <div
      v-if="selectedPurchase || isPurchaseDetailLoading"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      <div class="absolute inset-0 bg-black/70 backdrop-blur-sm" @click="closePurchaseDetail"></div>
      <div class="relative w-full max-w-3xl bg-slate-800 border border-slate-700 rounded-2xl shadow-2xl overflow-hidden max-h-[90vh] overflow-y-auto">
        <!-- 标题栏 -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-slate-700 sticky top-0 bg-slate-800/95 backdrop-blur z-10">
          <h3 class="text-lg font-semibold text-white flex items-center gap-2">
            已购作品详情
            <span class="px-2 py-0.5 bg-green-500/20 text-green-400 text-xs rounded-full font-medium">已购买</span>
          </h3>
          <button
            class="h-8 w-8 rounded-lg text-slate-400 hover:text-white hover:bg-slate-700 transition"
            @click="closePurchaseDetail"
          >
            <svg class="w-5 h-5 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>

        <div class="p-6">
          <!-- 加载中 -->
          <div v-if="isPurchaseDetailLoading" class="flex flex-col items-center justify-center py-14">
            <svg class="animate-spin h-10 w-10 text-blue-400 mb-3" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            <p class="text-slate-400 text-sm">正在加载作品详情...</p>
          </div>

          <!-- 加载失败 -->
          <div v-else-if="purchaseDetailError" class="text-center py-12">
            <p class="text-red-400 mb-4">{{ purchaseDetailError }}</p>
            <button class="px-5 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition" @click="closePurchaseDetail">
              关闭
            </button>
          </div>

          <!-- 详情内容 -->
          <div v-else-if="purchaseDetailTrack" class="space-y-6">
            <!-- 上半区：封面 + 元数据 -->
            <div class="grid grid-cols-1 md:grid-cols-[220px_1fr] gap-6">
              <div class="aspect-square rounded-xl overflow-hidden bg-slate-700">
                <img
                  v-if="purchaseDetailTrack.coverId"
                  :src="getAssetFileUrl(purchaseDetailTrack.coverId)"
                  :alt="purchaseDetailTrack.title"
                  class="w-full h-full object-cover"
                />
                <div
                  v-else
                  class="w-full h-full bg-gradient-to-br flex items-center justify-center"
                  :class="getCoverGradient(purchaseDetailTrack.id)"
                >
                  <span class="text-6xl opacity-40">{{ purchaseDetailTrack.trackType === 'PACK' ? '📦' : purchaseDetailTrack.fileType === 'MIDI' ? '🎹' : '🎵' }}</span>
                </div>
              </div>

              <div class="min-w-0">
                <div class="flex items-center gap-2 mb-2 flex-wrap">
                  <span
                    class="px-2 py-0.5 rounded-full text-xs font-bold"
                    :class="purchaseDetailTrack.trackType === 'PACK' ? 'bg-teal-500/80 text-white' : 'bg-purple-500/80 text-white'"
                  >
                    {{ purchaseDetailTrack.trackType === 'PACK' ? `📦 合集 · ${purchaseDetailTrack.fileCount} 个文件` : purchaseDetailTrack.fileType === 'MIDI' ? '🎹 MIDI' : '🎵 Audio' }}
                  </span>
                  <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-green-500/20 text-green-400">已购买</span>
                  <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-700 text-slate-200">
                    {{ formatPurchaseDetailPrice(purchaseDetailTrack.price) }}
                  </span>
                </div>
                <h4 class="text-2xl font-bold text-white mb-2 break-words">
                  {{ purchaseDetailTrack.title }}
                </h4>
                <p class="text-slate-400 text-sm mb-3">
                  by {{ purchaseDetailTrack.producerName || `Producer #${purchaseDetailTrack.producerId}` }}
                </p>
                <p v-if="purchaseDetailTrack.description" class="text-slate-300 leading-relaxed mb-3 whitespace-pre-wrap">
                  {{ purchaseDetailTrack.description }}
                </p>
                <div v-if="purchaseDetailTrack.tags" class="flex flex-wrap gap-2 mb-3">
                  <span
                    v-for="tag in parseTags(purchaseDetailTrack.tags)"
                    :key="tag"
                    class="px-2.5 py-1 rounded-full text-xs bg-slate-700/80 text-slate-200"
                  >
                    {{ tag }}
                  </span>
                </div>
                <p v-if="selectedPurchase" class="text-slate-500 text-xs">
                  购买于 {{ formatDate(selectedPurchase.createTime) }}
                </p>
              </div>
            </div>

            <!-- ========== SINGLE 音频: 完整预览 ========== -->
            <div
              v-if="purchaseDetailTrack.trackType === 'SINGLE' && purchaseDetailTrack.fileId && purchaseDetailTrack.fileType !== 'MIDI'"
              class="rounded-xl border border-slate-700 bg-slate-900/40 p-4"
            >
              <h5 class="text-white font-semibold mb-3 flex items-center gap-2">
                🎵 音频播放
                <span class="text-xs font-normal text-green-400/80">(已购买 · 完整播放)</span>
              </h5>
              <AudioPreview
                :src="getAssetFileUrl(purchaseDetailTrack.fileId)"
                :is-paid="false"
                :preview-limit="9999"
              />
            </div>

            <!-- ========== SINGLE MIDI: 提示下载 ========== -->
            <div
              v-if="purchaseDetailTrack.trackType === 'SINGLE' && purchaseDetailTrack.fileId && purchaseDetailTrack.fileType === 'MIDI'"
              class="rounded-xl border border-slate-700 bg-slate-900/40 p-4"
            >
              <div class="flex flex-col items-center py-4">
                <div class="h-14 w-14 bg-amber-500/20 rounded-xl flex items-center justify-center mb-2">
                  <span class="text-3xl">🎹</span>
                </div>
                <p class="text-white font-medium text-sm mb-1">MIDI 文件</p>
                <p class="text-slate-500 text-xs">已购买，可直接下载</p>
              </div>
            </div>

            <!-- ========== PACK: 合集文件列表 (完整预览 + 下载) ========== -->
            <div v-if="purchaseDetailTrack.trackType === 'PACK'" class="rounded-xl border border-slate-700 bg-slate-900/40 p-4">
              <div class="flex items-center justify-between mb-3">
                <h5 class="text-white font-semibold flex items-center gap-2">
                  📦 合集内容
                  <span class="text-sm font-normal text-slate-400">({{ purchaseDetailTrack.fileCount }} 个文件)</span>
                  <span class="text-xs font-normal text-green-400/80 ml-1">已购买 · 完整访问</span>
                </h5>
                <button
                  v-if="purchaseDetailTrack.files && purchaseDetailTrack.files.length > 0"
                  class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium rounded-lg transition flex items-center gap-1.5"
                  @click="downloadAllPurchasePackFiles"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                  </svg>
                  下载全部
                </button>
              </div>

              <div v-if="purchaseDetailTrack.files && purchaseDetailTrack.files.length > 0" class="space-y-2 max-h-[420px] overflow-y-auto pr-1">
                <div
                  v-for="(file, index) in purchaseDetailTrack.files"
                  :key="file.id"
                  class="bg-slate-800/60 rounded-lg overflow-hidden"
                >
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
                      <p class="text-xs text-slate-500">{{ file.fileType }}</p>
                    </div>
                    <!-- 下载按钮 -->
                    <button
                      class="w-8 h-8 rounded-full flex items-center justify-center bg-slate-700 text-slate-300 hover:bg-blue-600/60 hover:text-white transition shrink-0"
                      title="下载此文件"
                      @click.stop="downloadPurchaseFile(file.assetId, file.originalName || undefined)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                      </svg>
                    </button>
                    <!-- 播放按钮（仅音频） -->
                    <button
                      v-if="String(file.fileType).toUpperCase() === 'AUDIO'"
                      class="w-8 h-8 rounded-full flex items-center justify-center transition shrink-0"
                      :class="purchasePackPreviewingId === file.assetId
                        ? 'bg-purple-600 text-white'
                        : 'bg-slate-700 text-slate-300 hover:bg-purple-600/60 hover:text-white'"
                      @click.stop="togglePurchasePackPreview(file.assetId)"
                      :title="purchasePackPreviewingId === file.assetId ? '收起播放器' : '播放'"
                    >
                      <svg v-if="purchasePackPreviewingId !== file.assetId" class="w-4 h-4 ml-0.5" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M8 5v14l11-7z"></path>
                      </svg>
                      <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                      </svg>
                    </button>
                  </div>
                  <!-- 内嵌音频播放器（完整播放） -->
                  <div v-if="purchasePackPreviewingId === file.assetId" class="px-3 pb-3">
                    <AudioPreview
                      :src="getAssetFileUrl(file.assetId)"
                      :is-paid="false"
                      :preview-limit="9999"
                    />
                  </div>
                </div>
              </div>

              <div v-else class="text-center py-6">
                <p class="text-slate-500 text-sm">合集包含 {{ purchaseDetailTrack.fileCount }} 个文件</p>
              </div>
            </div>

            <!-- ========== 下载区域 ========== -->
            <div class="rounded-xl border border-green-700/40 bg-green-900/10 p-4">
              <h5 class="text-white font-semibold mb-3 flex items-center gap-2">
                <svg class="w-5 h-5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                </svg>
                下载
              </h5>
              <!-- SINGLE -->
              <div v-if="purchaseDetailTrack.trackType === 'SINGLE' && purchaseDetailTrack.fileId">
                <button
                  class="w-full px-4 py-3 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-lg transition flex items-center justify-center gap-2"
                  @click="downloadPurchaseFile(purchaseDetailTrack.fileId!, purchaseDetailTrack.title)"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                  </svg>
                  下载 {{ purchaseDetailTrack.fileType === 'MIDI' ? 'MIDI' : '音频' }} 文件
                </button>
              </div>
              <!-- PACK -->
              <div v-if="purchaseDetailTrack.trackType === 'PACK' && purchaseDetailTrack.files && purchaseDetailTrack.files.length > 0">
                <button
                  class="w-full px-4 py-3 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-lg transition flex items-center justify-center gap-2 mb-3"
                  @click="downloadAllPurchasePackFiles"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                  </svg>
                  下载全部 ({{ purchaseDetailTrack.files.length }} 个文件)
                </button>
                <p class="text-xs text-slate-500 text-center">也可在上方文件列表中单独下载每个文件</p>
              </div>
            </div>

            <!-- 关闭按钮 -->
            <div class="flex justify-end pt-2">
              <button
                class="px-5 py-3 bg-slate-700 hover:bg-slate-600 text-white rounded-lg transition"
                @click="closePurchaseDetail"
              >关闭</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 表格行半透明背景 */
.bg-slate-750 {
  background-color: rgb(40 44 54);
}
.bg-slate-750\/80 {
  background-color: rgb(40 44 54 / 0.8);
}
.bg-slate-850 {
  background-color: rgb(22 26 35);
}

/* 预览面板展开动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.animate-fade-in {
  animation: fadeIn 0.25s ease-out;
}
</style>

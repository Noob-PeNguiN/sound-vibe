<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { catalogApi, getAssetFileUrl } from '@/api/catalog'
import AssetPickerModal from '@/components/AssetPickerModal.vue'
import type { Track, TrackQuery, TrackVisibility, TrackUpdateForm, FileType, TrackType } from '@/types/catalog'
import type { Asset, AssetType } from '@/types/asset'

// ========== 路由 & Store ==========
const router = useRouter()
const userStore = useUserStore()

// ========== 状态 ==========
const tracks = ref<Track[]>([])
const isLoading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

// ========== 筛选 ==========
const filterStatus = ref<number | undefined>(undefined)
const filterVisibility = ref<number | undefined>(undefined)
const filterTrackType = ref<TrackType | ''>('')
const keyword = ref('')

// ========== 编辑相关 ==========
const editingTrack = ref<Track | null>(null)
const editForm = reactive<TrackUpdateForm>({})
const isSaving = ref(false)
const saveMessage = ref('')
const saveError = ref('')

// ========== 状态切换 ==========
const togglingStatusId = ref<number | null>(null)

// ========== 资产选择弹窗 ==========
const showAssetPicker = ref(false)
const assetPickerType = ref<AssetType>('IMAGE')
const assetPickerTarget = ref<'cover' | 'file'>('cover')

// ========== 方法 ==========

const getUserId = (): number | null => {
  try {
    const info = localStorage.getItem('soundvibe-user')
    if (info) return JSON.parse(info).userId || null
  } catch { /* ignore */ }
  return null
}

const fetchTracks = async () => {
  isLoading.value = true
  try {
    const query: TrackQuery = {
      producerId: getUserId() ?? undefined,
      keyword: keyword.value || undefined,
      trackType: filterTrackType.value || undefined,
      status: filterStatus.value,
      visibility: filterVisibility.value,
      current: currentPage.value,
      size: pageSize.value
    }
    const page = await catalogApi.listTracks(query)
    tracks.value = page.records
    total.value = page.total
  } catch (err: any) {
    console.error('[MyTracks] 加载失败:', err)
  } finally {
    isLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchTracks()
}

const goToPage = (page: number) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  fetchTracks()
}

// ========== 编辑功能 ==========

const startEdit = (track: Track) => {
  editingTrack.value = { ...track }
  Object.assign(editForm, {
    title: track.title,
    description: track.description || '',
    tags: track.tags || '',
    price: track.price,
    visibility: track.visibility,
    coverId: track.coverId,
    fileId: track.fileId,
    fileType: track.fileType,
    allowPreview: track.allowPreview ?? true,
    previewDuration: track.previewDuration ?? 30,
    stock: track.stock
  })
  saveMessage.value = ''
  saveError.value = ''
}

const cancelEdit = () => {
  editingTrack.value = null
  saveMessage.value = ''
  saveError.value = ''
}

const saveEdit = async () => {
  if (!editingTrack.value) return
  isSaving.value = true
  saveError.value = ''
  saveMessage.value = ''

  try {
    const updated = await catalogApi.updateTrack(editingTrack.value.id, editForm)
    const idx = tracks.value.findIndex(t => t.id === updated.id)
    if (idx !== -1) {
      tracks.value[idx] = updated
    }
    saveMessage.value = '保存成功'
    setTimeout(() => {
      editingTrack.value = null
      saveMessage.value = ''
    }, 800)
  } catch (err: any) {
    saveError.value = err.message || '保存失败'
    console.error('[MyTracks] 保存失败:', err)
  } finally {
    isSaving.value = false
  }
}

// ========== 状态切换 ==========

const toggleStatus = async (track: Track) => {
  togglingStatusId.value = track.id
  try {
    const updated = await catalogApi.toggleTrackStatus(track.id)
    const idx = tracks.value.findIndex(t => t.id === updated.id)
    if (idx !== -1) {
      tracks.value[idx] = updated
    }
  } catch (err: any) {
    console.error('[MyTracks] 切换状态失败:', err)
  } finally {
    togglingStatusId.value = null
  }
}

// ========== 删除作品 ==========
const deletingId = ref<number | null>(null)
const confirmDeleteId = ref<number | null>(null)

const requestDelete = (trackId: number) => {
  confirmDeleteId.value = trackId
}

const cancelDelete = () => {
  confirmDeleteId.value = null
}

const confirmDelete = async () => {
  if (!confirmDeleteId.value) return
  const id = confirmDeleteId.value
  deletingId.value = id
  confirmDeleteId.value = null

  try {
    await catalogApi.deleteTrack(id)
    tracks.value = tracks.value.filter(t => t.id !== id)
    total.value = Math.max(0, total.value - 1)
  } catch (err: any) {
    console.error('[MyTracks] 删除失败:', err)
    alert('删除失败: ' + (err.message || '未知错误'))
  } finally {
    deletingId.value = null
  }
}

// ========== 资产选择 ==========

const openCoverPicker = () => {
  assetPickerType.value = 'IMAGE'
  assetPickerTarget.value = 'cover'
  showAssetPicker.value = true
}

const openFilePicker = () => {
  const ft = editForm.fileType || 'AUDIO'
  assetPickerType.value = ft === 'MIDI' ? 'MIDI' : 'AUDIO'
  assetPickerTarget.value = 'file'
  showAssetPicker.value = true
}

const onAssetPicked = (asset: Asset) => {
  if (assetPickerTarget.value === 'cover') {
    editForm.coverId = asset.id
    if (editingTrack.value) {
      editingTrack.value.coverId = asset.id
    }
  } else {
    editForm.fileId = asset.id
    if (editingTrack.value) {
      editingTrack.value.fileId = asset.id
    }
  }
}

// ========== 格式化 ==========

const formatPrice = (price: number | null): string => {
  if (price === null || price === 0) return '免费'
  return `¥${price.toFixed(2)}`
}

const statusLabel = (s: number) => s === 1 ? '公开' : '私密'
const statusClass = (s: number) => s === 1
  ? 'bg-green-500/20 text-green-300'
  : 'bg-slate-600/30 text-slate-400'

const visibilityLabel = (v: number) => {
  switch (v) {
    case 0: return '🔒 私有'
    case 1: return '👥 共享'
    case 2: return '🌍 公开'
    default: return '未知'
  }
}

/** 获取作品图标 */
const getTrackIcon = (track: Track) => {
  if (track.trackType === 'PACK') return '📦'
  return track.fileType === 'MIDI' ? '🎹' : '🎵'
}

/** 获取作品类型标签 */
const getTrackTypeLabel = (track: Track) => {
  if (track.trackType === 'PACK') return `📦 合集 (${track.fileCount})`
  return track.fileType === 'MIDI' ? '🎹 MIDI' : '🎵 音频'
}

/** 获取类型标签样式 */
const getTrackTypeBadgeClass = (track: Track) => {
  if (track.trackType === 'PACK') return 'bg-teal-500/20 text-teal-300'
  return track.fileType === 'MIDI' ? 'bg-amber-500/20 text-amber-300' : 'bg-purple-500/20 text-purple-300'
}

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
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
  return tags.split(',').map(t => t.trim()).filter(Boolean)
}

// ========== 生命周期 ==========
onMounted(() => {
  fetchTracks()
})
</script>

<template>
  <div>
    <!-- 标题栏 + 发布按钮 -->
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-6 gap-4">
      <div>
        <h2 class="text-2xl font-bold text-white">我的作品</h2>
        <p class="text-slate-400 text-sm mt-1">共 {{ total }} 个作品</p>
      </div>
      <router-link
        to="/publish"
        class="px-5 py-2.5 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition shadow-lg flex items-center gap-2"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
        </svg>
        发布新作品
      </router-link>
    </div>

    <!-- 筛选栏 -->
    <div class="bg-slate-800 border border-slate-700 rounded-xl p-5 mb-6">
      <div class="flex flex-col sm:flex-row gap-3">
        <!-- 搜索 -->
        <div class="flex-1 relative">
          <svg class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
          </svg>
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索作品标题..."
            class="w-full pl-10 pr-4 py-2.5 bg-slate-900 border border-slate-600 rounded-lg text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
            @keydown.enter="handleSearch"
          />
        </div>

        <!-- 状态筛选 -->
        <select
          v-model="filterStatus"
          class="px-4 py-2.5 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition appearance-none min-w-[130px]"
          @change="handleSearch"
        >
          <option :value="undefined">全部状态</option>
          <option :value="0">私密</option>
          <option :value="1">公开</option>
        </select>

        <!-- 作品类型筛选 -->
        <select
          v-model="filterTrackType"
          class="px-4 py-2.5 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition appearance-none min-w-[130px]"
          @change="handleSearch"
        >
          <option value="">全部类型</option>
          <option value="SINGLE">单文件</option>
          <option value="PACK">合集</option>
        </select>

        <button
          class="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition"
          @click="handleSearch"
        >搜索</button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="isLoading" class="flex flex-col items-center justify-center py-20">
      <svg class="animate-spin h-12 w-12 text-blue-400 mb-4" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
      <p class="text-slate-400">加载中...</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="tracks.length === 0" class="text-center py-20">
      <div class="text-6xl mb-4">🎵</div>
      <h3 class="text-xl font-semibold text-white mb-2">还没有作品</h3>
      <p class="text-slate-400 mb-6">发布你的第一个作品吧！</p>
      <router-link
        to="/publish"
        class="inline-flex items-center gap-2 px-6 py-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition shadow-lg"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
        </svg>
        发布作品
      </router-link>
    </div>

    <!-- 作品列表 -->
    <div v-else class="space-y-4">
      <div
        v-for="track in tracks"
        :key="track.id"
        class="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden hover:border-slate-600 transition-all"
      >
        <!-- 作品卡片 -->
        <div class="flex items-center gap-4 p-4">
          <!-- 封面缩略图 -->
          <div class="w-16 h-16 rounded-lg overflow-hidden shrink-0">
            <img
              v-if="track.coverId"
              :src="getAssetFileUrl(track.coverId)"
              :alt="track.title"
              class="w-full h-full object-cover"
              loading="lazy"
            />
            <div
              v-else
              class="w-full h-full bg-gradient-to-br flex items-center justify-center"
              :class="getCoverGradient(track)"
            >
              <span class="text-xl opacity-50">{{ getTrackIcon(track) }}</span>
            </div>
          </div>

          <!-- 信息 -->
          <div class="flex-1 min-w-0">
            <h3 class="text-white font-semibold truncate">{{ track.title }}</h3>
            <div class="flex flex-wrap items-center gap-2 mt-1.5">
              <!-- 作品类型 -->
              <span
                class="px-2 py-0.5 rounded text-xs font-medium"
                :class="getTrackTypeBadgeClass(track)"
              >
                {{ getTrackTypeLabel(track) }}
              </span>
              <!-- 状态 -->
              <span class="px-2 py-0.5 rounded text-xs font-medium" :class="statusClass(track.status)">
                {{ statusLabel(track.status) }}
              </span>
              <!-- 可见性 -->
              <span class="text-xs text-slate-400">{{ visibilityLabel(track.visibility) }}</span>
              <!-- 用户标签 -->
              <span
                v-for="tag in parseTags(track.tags).slice(0, 3)"
                :key="'u-' + tag"
                class="px-2 py-0.5 bg-slate-700/60 text-slate-300 rounded text-xs"
              >
                {{ tag }}
              </span>
              <!-- AI 自动标签 -->
              <span
                v-for="tag in parseTags(track.autoTags).slice(0, 3)"
                :key="'ai-' + tag"
                class="px-2 py-0.5 bg-purple-500/15 text-purple-300 rounded text-xs border border-purple-500/30 inline-flex items-center gap-0.5"
              >
                <svg class="w-2.5 h-2.5 shrink-0 text-purple-400" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L9.19 8.63L2 9.24l5.46 4.73L5.82 21L12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2z"/></svg>
                {{ tag }}
              </span>
              <!-- Price -->
              <span class="text-xs text-slate-400">{{ formatPrice(track.price) }}</span>
              <!-- 已售数量 -->
              <span v-if="track.soldCount > 0" class="text-xs text-orange-400">
                已售 {{ track.soldCount }}
              </span>
              <!-- 库存 -->
              <span
                v-if="track.stock !== null && track.stock !== undefined"
                class="text-xs"
                :class="track.stock > 0 ? 'text-emerald-400' : 'text-red-400'"
              >
                {{ track.stock > 0 ? `库存 ${track.stock}` : '已售罄' }}
              </span>
            </div>
            <p v-if="track.description" class="text-xs text-slate-500 mt-1 truncate max-w-md">
              {{ track.description }}
            </p>
            <p class="text-xs text-slate-500 mt-0.5">{{ formatDate(track.createTime) }}</p>
          </div>

          <!-- 操作按钮 -->
          <div class="flex items-center gap-2 shrink-0">
            <button
              class="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
              :class="track.status === 1
                ? 'bg-slate-700 text-slate-300 hover:bg-slate-600'
                : 'bg-green-600 text-white hover:bg-green-500'"
              :disabled="togglingStatusId === track.id"
              @click="toggleStatus(track)"
            >
              <span v-if="togglingStatusId === track.id" class="inline-block animate-spin text-xs">⏳</span>
              <span v-else>{{ track.status === 1 ? '设为私密' : '设为公开' }}</span>
            </button>
            <button
              class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-medium transition"
              @click="startEdit(track)"
            >
              编辑
            </button>
            <button
              class="px-3 py-1.5 bg-red-600/80 hover:bg-red-500 text-white rounded-lg text-xs font-medium transition"
              :disabled="deletingId === track.id"
              @click="requestDelete(track.id)"
            >
              <span v-if="deletingId === track.id" class="inline-block animate-spin text-xs">⏳</span>
              <span v-else>删除</span>
            </button>
          </div>

          <!-- 删除确认弹出层 -->
          <Transition
            enter-active-class="transition duration-200 ease-out"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition duration-150 ease-in"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="confirmDeleteId === track.id"
              class="mt-2 p-3 bg-red-950/40 border border-red-800/50 rounded-lg"
            >
              <p class="text-sm text-red-300 mb-2">
                ⚠️ 确定要删除作品「{{ track.title }}」吗？此操作不可撤回。
              </p>
              <div class="flex items-center gap-2">
                <button
                  class="px-3 py-1 bg-red-600 hover:bg-red-500 text-white rounded text-xs font-medium transition"
                  @click="confirmDelete"
                >
                  确认删除
                </button>
                <button
                  class="px-3 py-1 bg-slate-700 hover:bg-slate-600 text-white rounded text-xs font-medium transition"
                  @click="cancelDelete"
                >
                  取消
                </button>
              </div>
            </div>
          </Transition>
        </div>

        <!-- 编辑面板（展开） -->
        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="opacity-0 max-h-0"
          enter-to-class="opacity-100 max-h-[1000px]"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="opacity-100 max-h-[1000px]"
          leave-to-class="opacity-0 max-h-0"
        >
          <div
            v-if="editingTrack?.id === track.id"
            class="border-t border-slate-700 bg-slate-850 px-6 py-5 overflow-hidden"
          >
            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">

              <!-- 左列：基础信息 -->
              <div class="space-y-4">
                <h4 class="text-white font-semibold text-sm flex items-center gap-2">
                  <svg class="w-4 h-4 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                  </svg>
                  编辑信息
                  <span class="text-xs text-slate-500 font-normal ml-2">
                    ({{ track.trackType === 'PACK' ? '📦 合集' : '🎵 单文件' }} · 类型不可修改)
                  </span>
                </h4>

                <!-- 标题 -->
                <div>
                  <label class="block text-xs font-medium text-slate-400 mb-1">标题</label>
                  <input
                    v-model="editForm.title"
                    type="text"
                    maxlength="200"
                    class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                  />
                </div>

                <!-- 描述 -->
                <div>
                  <label class="block text-xs font-medium text-slate-400 mb-1">描述</label>
                  <textarea
                    v-model="editForm.description"
                    maxlength="5000"
                    rows="3"
                    :placeholder="track.trackType === 'PACK'
                      ? '描述你的采样包...\n💡 建议包含: 包含内容、风格、BPM 范围等'
                      : '描述你的作品...\n💡 建议包含: BPM、调式、风格、适合用途等信息'"
                    class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition resize-none"
                  ></textarea>
                </div>

                <!-- 标签 -->
                <div>
                  <label class="block text-xs font-medium text-slate-400 mb-1">标签</label>
                  <input
                    v-model="editForm.tags"
                    type="text"
                    maxlength="500"
                    :placeholder="track.trackType === 'PACK'
                      ? '逗号分隔, 例如: sample-pack, trap, drum-kit'
                      : '逗号分隔, 例如: trap, dark, 140bpm, C minor'"
                    class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                  />
                  <p class="text-xs text-slate-600 mt-1">标签帮助买家找到你的作品</p>
                </div>
              </div>

              <!-- 右列：价格、可见性、文件 -->
              <div class="space-y-4">
                <!-- 价格 -->
                <div>
                  <label class="block text-xs font-medium text-slate-400 mb-1">价格 (¥)</label>
                  <input
                    v-model.number="editForm.price"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="0.00（免费）"
                    class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                  />
                </div>

                <!-- 库存 -->
                <div>
                  <label class="block text-xs font-medium text-slate-400 mb-1">库存数量</label>
                  <input
                    v-model.number="editForm.stock"
                    type="number"
                    min="0"
                    step="1"
                    placeholder="留空表示不限库存"
                    class="w-full px-3 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                  />
                  <p class="text-xs text-slate-600 mt-1">留空或清除表示不限库存</p>
                </div>

                <!-- 可见范围 -->
                <div>
                  <label class="block text-xs font-medium text-slate-400 mb-1">可见范围</label>
                  <div class="flex gap-2">
                    <button
                      type="button"
                      class="flex-1 px-3 py-2 rounded-lg border text-sm font-medium transition-all"
                      :class="editForm.visibility === 2
                        ? 'border-green-500 bg-green-500/10 text-green-300'
                        : 'border-slate-600 bg-slate-900 text-slate-400 hover:border-slate-500'"
                      @click="editForm.visibility = 2"
                    >🌍 公开</button>
                    <button
                      type="button"
                      class="flex-1 px-3 py-2 rounded-lg border text-sm font-medium transition-all"
                      :class="editForm.visibility === 0
                        ? 'border-orange-500 bg-orange-500/10 text-orange-300'
                        : 'border-slate-600 bg-slate-900 text-slate-400 hover:border-slate-500'"
                      @click="editForm.visibility = 0"
                    >🔒 私有</button>
                  </div>
                </div>

                <!-- 预览设置（仅付费作品显示） -->
                <div v-if="editForm.price && editForm.price > 0" class="bg-slate-900/50 border border-amber-500/20 rounded-lg p-3 space-y-3">
                  <h5 class="text-xs font-medium text-amber-300 flex items-center gap-1.5">
                    <span>🎧</span> 预览设置
                  </h5>

                  <!-- 是否允许预览 -->
                  <div class="flex items-center justify-between">
                    <div>
                      <p class="text-xs text-slate-300">允许买家试听</p>
                    </div>
                    <button
                      type="button"
                      class="relative w-10 h-5 rounded-full transition-all duration-200"
                      :class="editForm.allowPreview ? 'bg-green-600' : 'bg-slate-600'"
                      @click="editForm.allowPreview = !editForm.allowPreview"
                    >
                      <span
                        class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200"
                        :class="editForm.allowPreview ? 'translate-x-5' : 'translate-x-0'"
                      ></span>
                    </button>
                  </div>

                  <!-- 预览时长 -->
                  <div v-if="editForm.allowPreview">
                    <label class="block text-xs text-slate-500 mb-1">预览时长</label>
                    <div class="flex items-center gap-2">
                      <input
                        v-model.number="editForm.previewDuration"
                        type="range"
                        min="5"
                        max="120"
                        step="5"
                        class="flex-1 h-1.5 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-amber-500"
                      />
                      <span class="text-white text-xs font-semibold w-10 text-center">{{ editForm.previewDuration || 30 }}s</span>
                    </div>
                  </div>
                </div>

                <!-- 更换文件 — 仅 SINGLE 类型 -->
                <div v-if="track.trackType === 'SINGLE'" class="space-y-3">
                  <label class="block text-xs font-medium text-slate-400">更换文件</label>

                  <!-- 更换封面 -->
                  <div class="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg">
                    <div class="w-10 h-10 rounded-lg overflow-hidden shrink-0">
                      <img
                        v-if="editForm.coverId"
                        :src="getAssetFileUrl(editForm.coverId)"
                        class="w-full h-full object-cover"
                      />
                      <div v-else class="w-full h-full bg-slate-700 flex items-center justify-center">
                        <span class="text-xs text-slate-500">无</span>
                      </div>
                    </div>
                    <div class="flex-1 min-w-0">
                      <p class="text-xs text-slate-300">封面图片</p>
                      <p class="text-xs text-slate-500">{{ editForm.coverId ? `ID: ${editForm.coverId}` : '未设置' }}</p>
                    </div>
                    <button
                      class="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 text-white text-xs rounded-lg transition"
                      @click="openCoverPicker"
                    >选择图片</button>
                  </div>

                  <!-- 更换文件（音频/MIDI） -->
                  <div class="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg">
                    <div
                      class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0"
                      :class="editForm.fileType === 'MIDI' ? 'bg-amber-500/20' : 'bg-purple-500/20'"
                    >
                      <span class="text-lg">{{ editForm.fileType === 'MIDI' ? '🎹' : '🎵' }}</span>
                    </div>
                    <div class="flex-1 min-w-0">
                      <p class="text-xs text-slate-300">{{ editForm.fileType === 'MIDI' ? 'MIDI 文件' : '音频文件' }}</p>
                      <p class="text-xs text-slate-500">{{ editForm.fileId ? `ID: ${editForm.fileId}` : '未设置' }}</p>
                    </div>
                    <button
                      class="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 text-white text-xs rounded-lg transition"
                      @click="openFilePicker"
                    >选择文件</button>
                  </div>
                </div>

                <!-- PACK 类型 — 仅更换封面 -->
                <div v-else class="space-y-3">
                  <label class="block text-xs font-medium text-slate-400">更换封面</label>

                  <div class="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg">
                    <div class="w-10 h-10 rounded-lg overflow-hidden shrink-0">
                      <img
                        v-if="editForm.coverId"
                        :src="getAssetFileUrl(editForm.coverId)"
                        class="w-full h-full object-cover"
                      />
                      <div v-else class="w-full h-full bg-slate-700 flex items-center justify-center">
                        <span class="text-xs text-slate-500">无</span>
                      </div>
                    </div>
                    <div class="flex-1 min-w-0">
                      <p class="text-xs text-slate-300">封面图片</p>
                      <p class="text-xs text-slate-500">{{ editForm.coverId ? `ID: ${editForm.coverId}` : '未设置' }}</p>
                    </div>
                    <button
                      class="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 text-white text-xs rounded-lg transition"
                      @click="openCoverPicker"
                    >选择图片</button>
                  </div>

                  <!-- PACK 文件管理提示 -->
                  <div class="p-3 bg-teal-500/5 border border-teal-500/20 rounded-lg">
                    <p class="text-xs text-teal-300">
                      📦 合集包含 <span class="font-semibold">{{ track.fileCount }}</span> 个文件。
                      如需修改文件列表，请重新发布作品。
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 保存 / 取消 -->
            <div class="mt-5 pt-4 border-t border-slate-700 flex items-center justify-between">
              <div>
                <Transition
                  enter-active-class="transition duration-200"
                  enter-from-class="opacity-0"
                  enter-to-class="opacity-100"
                >
                  <span v-if="saveMessage" class="text-green-400 text-sm">{{ saveMessage }}</span>
                  <span v-else-if="saveError" class="text-red-400 text-sm">{{ saveError }}</span>
                </Transition>
              </div>
              <div class="flex gap-2">
                <button
                  class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm rounded-lg transition"
                  @click="cancelEdit"
                >取消</button>
                <button
                  class="px-5 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition flex items-center gap-1.5"
                  :disabled="isSaving"
                  @click="saveEdit"
                >
                  <svg v-if="isSaving" class="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                  </svg>
                  <span>{{ isSaving ? '保存中...' : '保存更改' }}</span>
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="mt-8 flex items-center justify-center gap-2">
      <button
        :disabled="currentPage <= 1"
        class="px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-300 hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
        @click="goToPage(currentPage - 1)"
      >上一页</button>
      <span class="text-sm text-slate-400">第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button
        :disabled="currentPage >= totalPages"
        class="px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-300 hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
        @click="goToPage(currentPage + 1)"
      >下一页</button>
    </div>

    <!-- 资产选择弹窗 -->
    <AssetPickerModal
      v-model:visible="showAssetPicker"
      :type="assetPickerType"
      :title="assetPickerTarget === 'cover' ? '选择封面图片' : (assetPickerType === 'MIDI' ? '选择 MIDI 文件' : '选择音频文件')"
      @select="onAssetPicked"
    />
  </div>
</template>

<style scoped>
.bg-slate-850 {
  background-color: rgb(22 26 35);
}
</style>

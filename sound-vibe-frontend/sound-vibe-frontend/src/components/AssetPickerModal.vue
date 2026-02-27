<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { assetApi } from '@/api/asset'
import type { Asset, AssetType } from '@/types/asset'

// ========== Props ==========
interface Props {
  /** 是否显示 */
  visible: boolean
  /** 过滤资产类型 */
  type: AssetType
  /** 弹窗标题 */
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '从资产库选择'
})

// ========== Events ==========
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'select', asset: Asset): void
}>()

// ========== 状态 ==========
const assets = ref<Asset[]>([])
const isLoading = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

// ========== 方法 ==========

const fetchAssets = async () => {
  isLoading.value = true
  try {
    const result = await assetApi.list({
      current: currentPage.value,
      size: pageSize.value,
      type: props.type,
      keyword: keyword.value || undefined
    })
    assets.value = result.records
    total.value = result.total
  } catch (err: any) {
    console.error('[AssetPicker] 加载失败:', err)
  } finally {
    isLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchAssets()
}

const goToPage = (page: number) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  fetchAssets()
}

const selectAsset = (asset: Asset) => {
  emit('select', asset)
  close()
}

const close = () => {
  emit('update:visible', false)
}

const formatSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + units[i]
}

// ========== 生命周期 ==========
watch(() => props.visible, (val) => {
  if (val) {
    keyword.value = ''
    currentPage.value = 1
    fetchAssets()
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
      >
        <!-- 遮罩 -->
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="close"></div>

        <!-- 弹窗 -->
        <div class="relative w-full max-w-3xl max-h-[80vh] bg-slate-800 border border-slate-700 rounded-2xl shadow-2xl flex flex-col overflow-hidden">

          <!-- 头部 -->
          <div class="flex items-center justify-between px-6 py-4 border-b border-slate-700 shrink-0">
              <div class="flex items-center gap-3">
              <div class="h-9 w-9 rounded-lg flex items-center justify-center"
                :class="{
                  'bg-blue-500/20': type === 'IMAGE',
                  'bg-purple-500/20': type === 'AUDIO',
                  'bg-amber-500/20': type === 'MIDI'
                }">
                <span class="text-lg">{{ type === 'IMAGE' ? '🖼️' : type === 'MIDI' ? '🎹' : '🎵' }}</span>
              </div>
              <div>
                <h3 class="text-white font-semibold">{{ title }}</h3>
                <p class="text-slate-400 text-xs">共 {{ total }} 个{{ type === 'IMAGE' ? '图片' : type === 'MIDI' ? 'MIDI' : '音频' }}资产</p>
              </div>
            </div>
            <button
              class="text-slate-400 hover:text-white p-1.5 rounded-lg hover:bg-slate-700 transition"
              @click="close"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>

          <!-- 搜索栏 -->
          <div class="px-6 py-3 border-b border-slate-700/50 shrink-0">
            <div class="flex gap-2">
              <input
                v-model="keyword"
                type="text"
                :placeholder="`搜索${type === 'IMAGE' ? '图片' : type === 'MIDI' ? 'MIDI' : '音频'}文件名...`"
                class="flex-1 px-4 py-2 bg-slate-900 border border-slate-600 rounded-lg text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                @keydown.enter="handleSearch"
              />
              <button
                class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm rounded-lg transition"
                @click="handleSearch"
              >搜索</button>
            </div>
          </div>

          <!-- 内容区 -->
          <div class="flex-1 overflow-y-auto px-6 py-4 min-h-0">
            <!-- 加载中 -->
            <div v-if="isLoading" class="flex items-center justify-center py-12">
              <svg class="animate-spin h-8 w-8 text-blue-400" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
              </svg>
            </div>

            <!-- 空状态 -->
            <div v-else-if="assets.length === 0" class="text-center py-12">
              <div class="text-5xl mb-3">{{ type === 'IMAGE' ? '🖼️' : type === 'MIDI' ? '🎹' : '🎵' }}</div>
              <p class="text-slate-400">没有找到{{ type === 'IMAGE' ? '图片' : type === 'MIDI' ? 'MIDI' : '音频' }}资产</p>
              <p class="text-slate-500 text-sm mt-1">请先在「我的资产」页面上传文件</p>
            </div>

            <!-- 图片网格选择 -->
            <div v-else-if="type === 'IMAGE'" class="grid grid-cols-3 sm:grid-cols-4 gap-3">
              <button
                v-for="asset in assets"
                :key="asset.assetCode"
                class="group relative aspect-square rounded-xl overflow-hidden border-2 transition-all duration-200 hover:scale-[1.03]"
                :class="'border-transparent hover:border-blue-500'"
                @click="selectAsset(asset)"
              >
                <img
                  :src="asset.url"
                  :alt="asset.originalName"
                  class="w-full h-full object-cover"
                  loading="lazy"
                />
                <!-- 悬停信息 -->
                <div class="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex flex-col items-center justify-center p-2">
                  <svg class="w-8 h-8 text-blue-400 mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                  </svg>
                  <p class="text-white text-xs text-center truncate w-full">{{ asset.originalName }}</p>
                  <p class="text-slate-300 text-xs">{{ formatSize(asset.size) }}</p>
                </div>
              </button>
            </div>

            <!-- 音频/MIDI 列表选择 -->
            <div v-else class="space-y-2">
              <button
                v-for="asset in assets"
                :key="asset.assetCode"
                class="w-full flex items-center gap-4 p-3 rounded-xl border border-slate-700 hover:border-blue-500 hover:bg-slate-700/50 transition-all duration-200 text-left"
                @click="selectAsset(asset)"
              >
                <div
                  class="h-12 w-12 rounded-lg flex items-center justify-center shrink-0"
                  :class="type === 'MIDI' ? 'bg-amber-500/20' : 'bg-purple-500/20'"
                >
                  <span v-if="type === 'MIDI'" class="text-2xl">🎹</span>
                  <svg v-else class="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3"></path>
                  </svg>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-white text-sm font-medium truncate">{{ asset.originalName }}</p>
                  <p class="text-slate-400 text-xs">{{ asset.extension.toUpperCase() }} · {{ formatSize(asset.size) }}</p>
                </div>
                <svg class="w-5 h-5 text-slate-500 group-hover:text-blue-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                </svg>
              </button>
            </div>
          </div>

          <!-- 底部分页 -->
          <div v-if="totalPages > 1" class="flex items-center justify-between px-6 py-3 border-t border-slate-700 shrink-0">
            <p class="text-sm text-slate-400">第 {{ currentPage }} / {{ totalPages }} 页</p>
            <div class="flex gap-2">
              <button
                :disabled="currentPage <= 1"
                class="px-3 py-1.5 bg-slate-700 text-white text-sm rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-600"
                @click="goToPage(currentPage - 1)"
              >上一页</button>
              <button
                :disabled="currentPage >= totalPages"
                class="px-3 py-1.5 bg-slate-700 text-white text-sm rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-600"
                @click="goToPage(currentPage + 1)"
              >下一页</button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

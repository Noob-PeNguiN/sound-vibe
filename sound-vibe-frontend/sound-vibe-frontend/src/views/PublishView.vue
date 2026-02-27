<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import UploadZone from '@/components/UploadZone.vue'
import AssetPickerModal from '@/components/AssetPickerModal.vue'
import { catalogApi } from '@/api/catalog'
import type { Asset } from '@/types/asset'
import type { TrackPublishForm, TrackVisibility, FileType, TrackType, TrackFileForm } from '@/types/catalog'

// ========== 路由 ==========
const router = useRouter()

// ========== 上传/选择状态 ==========

/** 封面资产 */
const coverAsset = ref<Asset | null>(null)
/** 文件资产（仅 SINGLE 模式） */
const fileAsset = ref<Asset | null>(null)

/** 封面选择模式 */
const coverMode = ref<'upload' | 'library'>('upload')
/** 文件选择模式（仅 SINGLE） */
const fileMode = ref<'upload' | 'library'>('upload')

/** 资产选择弹窗状态 */
const showCoverPicker = ref(false)
const showFilePicker = ref(false)

/** PACK 模式：文件添加弹窗 */
const showPackFilePicker = ref(false)
const packFilePickerType = ref<'AUDIO' | 'MIDI'>('AUDIO')

// ========== 表单数据 ==========

const form = ref<TrackPublishForm>({
  title: '',
  description: '',
  trackType: 'SINGLE',
  fileId: null,
  fileType: 'AUDIO',
  files: [],
  coverId: null,
  tags: '',
  price: null,
  visibility: 2,
  allowPreview: true,
  previewDuration: 30,
  stock: null
})

/** PACK 模式：已添加的文件列表（包含 Asset 对象用于展示） */
const packFiles = ref<(TrackFileForm & { asset?: Asset })[]>([])

// ========== 提交状态 ==========
const isSubmitting = ref(false)
const submitError = ref('')
const submitSuccess = ref(false)

// ========== 计算属性 ==========

const isPack = computed(() => form.value.trackType === 'PACK')
const isPaid = computed(() => form.value.price !== null && form.value.price > 0)

/** 是否可以提交 */
const canSubmit = computed(() => {
  if (form.value.title.trim() === '') return false
  if (isPack.value) {
    return packFiles.value.length > 0
  }
  return form.value.fileId !== null
})

/** 文件接受的类型 */
const fileAccept = computed(() => {
  if (form.value.fileType === 'MIDI') return '.mid,.midi'
  return 'audio/*,.wav,.mp3,.flac,.aac,.ogg'
})

/** 文件上传提示 */
const fileHint = computed(() => {
  if (form.value.fileType === 'MIDI') return '支持 MIDI (.mid, .midi) 格式'
  return '支持 MP3, WAV, FLAC, AAC, OGG 格式'
})

// ========== 选项 ==========

const visibilityOptions: { value: TrackVisibility; label: string; icon: string; desc: string }[] = [
  { value: 2, label: '公开', icon: '🌍', desc: '所有人可见，展示在市场' },
  { value: 0, label: '私有', icon: '🔒', desc: '仅自己可见' }
]

const fileTypeOptions: { value: FileType; label: string; icon: string }[] = [
  { value: 'AUDIO', label: '音频', icon: '🎵' },
  { value: 'MIDI', label: 'MIDI', icon: '🎹' }
]

const trackTypeOptions: { value: TrackType; label: string; icon: string; desc: string }[] = [
  { value: 'SINGLE', label: '单文件', icon: '🎵', desc: '包含 1 个音频或 MIDI 文件' },
  { value: 'PACK', label: '合集', icon: '📦', desc: '采样包，包含多个文件' }
]

// ========== 方法 ==========

/** 切换作品类型 */
const switchTrackType = (type: TrackType) => {
  if (form.value.trackType === type) return
  form.value.trackType = type
  // 切换模式时清空文件
  clearFile()
  packFiles.value = []
}

/** 处理封面上传成功 */
const onCoverUploaded = (asset: Asset) => {
  coverAsset.value = asset
  form.value.coverId = asset.id
}

/** 处理文件上传成功（SINGLE 模式） */
const onFileUploaded = (asset: Asset) => {
  fileAsset.value = asset
  form.value.fileId = asset.id
}

/** 从资产库选择封面 */
const onCoverPicked = (asset: Asset) => {
  coverAsset.value = asset
  form.value.coverId = asset.id
}

/** 从资产库选择文件（SINGLE 模式） */
const onFilePicked = (asset: Asset) => {
  fileAsset.value = asset
  form.value.fileId = asset.id
}

/** PACK 模式：打开添加文件弹窗 */
const openPackFilePicker = (type: 'AUDIO' | 'MIDI') => {
  packFilePickerType.value = type
  showPackFilePicker.value = true
}

/** PACK 模式：选择文件后添加到列表 */
const onPackFilePicked = (asset: Asset) => {
  // 防止重复添加
  if (packFiles.value.some(f => f.assetId === asset.id)) return
  packFiles.value.push({
    assetId: asset.id,
    fileType: packFilePickerType.value,
    originalName: asset.originalName,
    sortOrder: packFiles.value.length,
    allowPreview: true,
    asset: asset
  })
}

/** PACK 模式：上传文件后添加到列表 */
const onPackFileUploaded = (asset: Asset) => {
  if (packFiles.value.some(f => f.assetId === asset.id)) return
  // 根据扩展名推断类型
  const ext = asset.extension?.toLowerCase() || ''
  const fileType: FileType = ['mid', 'midi'].includes(ext) ? 'MIDI' : 'AUDIO'
  packFiles.value.push({
    assetId: asset.id,
    fileType,
    originalName: asset.originalName,
    sortOrder: packFiles.value.length,
    allowPreview: true,
    asset: asset
  })
}

/** PACK 模式：切换文件预览开关 */
const togglePackFilePreview = (index: number) => {
  packFiles.value[index].allowPreview = !packFiles.value[index].allowPreview
}

/** PACK 模式：移除文件 */
const removePackFile = (index: number) => {
  packFiles.value.splice(index, 1)
  // 重新计算 sortOrder
  packFiles.value.forEach((f, i) => { f.sortOrder = i })
}

/** PACK 模式：上移文件 */
const movePackFileUp = (index: number) => {
  if (index <= 0) return
  const temp = packFiles.value[index]
  packFiles.value[index] = packFiles.value[index - 1]
  packFiles.value[index - 1] = temp
  packFiles.value.forEach((f, i) => { f.sortOrder = i })
}

/** PACK 模式：下移文件 */
const movePackFileDown = (index: number) => {
  if (index >= packFiles.value.length - 1) return
  const temp = packFiles.value[index]
  packFiles.value[index] = packFiles.value[index + 1]
  packFiles.value[index + 1] = temp
  packFiles.value.forEach((f, i) => { f.sortOrder = i })
}

/** 清除封面 */
const clearCover = () => {
  coverAsset.value = null
  form.value.coverId = null
}

/** 清除文件（SINGLE 模式） */
const clearFile = () => {
  fileAsset.value = null
  form.value.fileId = null
}

/** 切换文件类型时清空已选文件（SINGLE 模式） */
const switchFileType = (type: FileType) => {
  if (form.value.fileType !== type) {
    form.value.fileType = type
    clearFile()
  }
}

/** 格式化文件大小 */
const formatSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + units[i]
}

/** 提交发布 */
const handleSubmit = async () => {
  if (!canSubmit.value || isSubmitting.value) return

  isSubmitting.value = true
  submitError.value = ''

  try {
    const payload: TrackPublishForm = {
      title: form.value.title.trim(),
      description: form.value.description || '',
      trackType: form.value.trackType,
      fileId: isPack.value ? null : form.value.fileId,
      fileType: isPack.value ? 'AUDIO' : form.value.fileType,
      files: isPack.value ? packFiles.value.map(f => ({
        assetId: f.assetId,
        fileType: f.fileType,
        originalName: f.originalName,
        sortOrder: f.sortOrder,
        allowPreview: f.allowPreview ?? true
      })) : undefined,
      coverId: form.value.coverId,
      tags: form.value.tags || '',
      price: form.value.price && form.value.price > 0 ? form.value.price : null,
      visibility: form.value.visibility ?? 2,
      allowPreview: form.value.allowPreview ?? true,
      previewDuration: form.value.previewDuration ?? 30,
      stock: form.value.stock != null && form.value.stock >= 0 ? form.value.stock : null
    }

    const track = await catalogApi.publishTrack(payload)
    console.log('[PublishView] 发布成功:', track)

    submitSuccess.value = true
    setTimeout(() => { router.push('/') }, 1500)
  } catch (err: any) {
    submitError.value = err.message || '发布失败，请稍后重试'
    console.error('[PublishView] 发布失败:', err)
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-900">
    <!-- 顶部导航栏 -->
    <nav class="bg-slate-800 border-b border-slate-700 shadow-lg">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-4">
            <router-link to="/" class="flex items-center space-x-2 text-slate-400 hover:text-white transition">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
              </svg>
              <span class="text-sm">返回市场</span>
            </router-link>
          </div>
          <div class="flex items-center space-x-3">
            <div class="h-10 w-10 bg-gradient-to-tr from-blue-500 to-purple-500 rounded-lg flex items-center justify-center shadow-lg">
              <span class="text-2xl">🎵</span>
            </div>
            <div>
              <h1 class="text-xl font-bold text-white">发布作品</h1>
              <p class="text-xs text-slate-400">Publish Work</p>
            </div>
          </div>
          <div class="w-24"></div>
        </div>
      </div>
    </nav>

    <!-- 主内容区域 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- 成功提示 -->
      <Transition enter-active-class="transition duration-500 ease-out" enter-from-class="opacity-0 scale-95" enter-to-class="opacity-100 scale-100">
        <div v-if="submitSuccess" class="mb-8 bg-green-500/10 border border-green-500/30 rounded-xl p-8 text-center">
          <div class="text-5xl mb-4">🎉</div>
          <h2 class="text-2xl font-bold text-green-400 mb-2">发布成功！</h2>
          <p class="text-slate-400">正在跳转到市场首页...</p>
        </div>
      </Transition>

      <!-- ===== 作品类型选择 ===== -->
      <div v-if="!submitSuccess" class="mb-8">
        <h2 class="text-lg font-bold text-white mb-4 flex items-center gap-2">
          <span class="text-xl">📋</span> 选择作品类型
        </h2>
        <div class="grid grid-cols-2 gap-4 max-w-xl">
          <button
            v-for="opt in trackTypeOptions"
            :key="opt.value"
            type="button"
            class="p-5 rounded-xl border-2 transition-all duration-200 text-left"
            :class="{
              'border-purple-500 bg-purple-500/10 shadow-lg shadow-purple-500/10': form.trackType === opt.value,
              'border-slate-700 bg-slate-800 hover:border-slate-600': form.trackType !== opt.value
            }"
            @click="switchTrackType(opt.value)"
          >
            <div class="flex items-center gap-3 mb-2">
              <span class="text-2xl">{{ opt.icon }}</span>
              <span class="text-lg font-bold text-white">{{ opt.label }}</span>
            </div>
            <p class="text-sm text-slate-400">{{ opt.desc }}</p>
          </button>
        </div>
      </div>

      <!-- 两栏布局 -->
      <div v-if="!submitSuccess" class="grid grid-cols-1 lg:grid-cols-2 gap-8">

        <!-- ==================== 左栏：文件选择区域 ==================== -->
        <div class="space-y-6">
          <h2 class="text-xl font-bold text-white flex items-center gap-2">
            <svg class="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
            </svg>
            {{ isPack ? '合集文件' : '选择文件' }}
          </h2>

          <!-- ===== SINGLE 模式：文件类型选择 + 单文件上传 ===== -->
          <template v-if="!isPack">
            <!-- 文件类型选择 -->
            <div class="bg-slate-800 border border-slate-700 rounded-xl p-5">
              <h3 class="text-white font-semibold text-sm mb-3">文件类型</h3>
              <div class="flex gap-3">
                <button
                  v-for="opt in fileTypeOptions"
                  :key="opt.value"
                  type="button"
                  class="flex-1 p-3 rounded-lg border-2 transition-all duration-200 text-center"
                  :class="{
                    'border-purple-500 bg-purple-500/10': form.fileType === opt.value,
                    'border-slate-600 bg-slate-900 hover:border-slate-500': form.fileType !== opt.value
                  }"
                  @click="switchFileType(opt.value)"
                >
                  <span class="text-xl block mb-1">{{ opt.icon }}</span>
                  <span class="text-sm font-semibold text-white">{{ opt.label }}</span>
                </button>
              </div>
            </div>

            <!-- 主文件 -->
            <div class="bg-slate-800 border border-slate-700 rounded-xl p-5">
              <div class="flex items-center gap-2 mb-4">
                <div class="h-8 w-8 bg-purple-500/20 rounded-lg flex items-center justify-center">
                  <span class="text-sm">{{ form.fileType === 'MIDI' ? '🎹' : '🎵' }}</span>
                </div>
                <div class="flex-1">
                  <h3 class="text-white font-semibold text-sm">
                    {{ form.fileType === 'MIDI' ? 'MIDI 文件' : '音频文件' }}
                    <span class="text-red-400">*</span>
                  </h3>
                  <p class="text-slate-400 text-xs">必须选择文件</p>
                </div>
                <div v-if="fileAsset" class="flex items-center gap-1 text-green-400 text-xs">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                  </svg>
                  已选择
                </div>
              </div>

              <!-- 已选文件预览 -->
              <div v-if="fileAsset" class="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg mb-3">
                <div class="w-12 h-12 bg-purple-500/20 rounded-lg flex items-center justify-center shrink-0">
                  <span class="text-xl">{{ form.fileType === 'MIDI' ? '🎹' : '🎵' }}</span>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm text-white truncate">{{ fileAsset.originalName }}</p>
                  <p class="text-xs text-slate-400">{{ formatSize(fileAsset.size) }}</p>
                </div>
                <button class="text-slate-400 hover:text-red-400 p-1 transition" @click="clearFile" title="移除">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                  </svg>
                </button>
              </div>

              <!-- 模式切换 -->
              <div v-if="!fileAsset" class="flex gap-2 mb-3">
                <button
                  class="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
                  :class="fileMode === 'upload' ? 'bg-purple-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'"
                  @click="fileMode = 'upload'"
                >上传新文件</button>
                <button
                  class="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
                  :class="fileMode === 'library' ? 'bg-purple-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'"
                  @click="fileMode = 'library'"
                >从资产库选择</button>
              </div>

              <UploadZone
                v-if="!fileAsset && fileMode === 'upload'"
                :accept="fileAccept"
                :label="form.fileType === 'MIDI' ? '拖拽 MIDI 文件到此处，或' : '拖拽音频文件到此处，或'"
                :hint="fileHint"
                @uploaded="onFileUploaded"
              />
              <button
                v-if="!fileAsset && fileMode === 'library'"
                class="w-full py-10 border-2 border-dashed border-slate-600 rounded-xl text-center hover:border-purple-500 hover:bg-purple-500/5 transition-all duration-200"
                @click="showFilePicker = true"
              >
                <div class="flex flex-col items-center gap-2">
                  <div class="h-12 w-12 bg-purple-500/10 rounded-xl flex items-center justify-center">
                    <span class="text-2xl">{{ form.fileType === 'MIDI' ? '🎹' : '🎵' }}</span>
                  </div>
                  <p class="text-slate-300 text-sm">点击浏览已上传的{{ form.fileType === 'MIDI' ? 'MIDI' : '音频' }}文件</p>
                </div>
              </button>
            </div>
          </template>

          <!-- ===== PACK 模式：多文件管理 ===== -->
          <template v-else>
            <div class="bg-slate-800 border border-slate-700 rounded-xl p-5">
              <div class="flex items-center justify-between mb-4">
                <div>
                  <h3 class="text-white font-semibold text-sm">
                    📦 合集文件列表 <span class="text-red-400">*</span>
                  </h3>
                  <p class="text-slate-400 text-xs mt-1">添加音频和/或 MIDI 文件到采样包中</p>
                </div>
                <span class="text-sm text-slate-400">{{ packFiles.length }} 个文件</span>
              </div>

              <!-- 添加文件按钮 -->
              <div class="flex gap-2 mb-4">
                <button
                  class="flex-1 px-4 py-3 bg-purple-600/20 border border-purple-500/30 rounded-lg text-sm font-medium text-purple-300 hover:bg-purple-600/30 transition flex items-center justify-center gap-2"
                  @click="openPackFilePicker('AUDIO')"
                >
                  <span>🎵</span> 添加音频
                </button>
                <button
                  class="flex-1 px-4 py-3 bg-amber-600/20 border border-amber-500/30 rounded-lg text-sm font-medium text-amber-300 hover:bg-amber-600/30 transition flex items-center justify-center gap-2"
                  @click="openPackFilePicker('MIDI')"
                >
                  <span>🎹</span> 添加 MIDI
                </button>
              </div>

              <!-- 上传区域（快速添加） -->
              <div class="mb-4">
                <UploadZone
                  accept="audio/*,.wav,.mp3,.flac,.aac,.ogg,.mid,.midi"
                  label="拖拽文件到此处快速添加到合集，或"
                  hint="支持音频和 MIDI 文件混合添加"
                  @uploaded="onPackFileUploaded"
                />
              </div>

              <!-- 文件列表 -->
              <div v-if="packFiles.length === 0" class="text-center py-8 text-slate-500">
                <span class="text-3xl block mb-2">📦</span>
                <p class="text-sm">还没有添加文件，点击上方按钮添加</p>
              </div>

              <div v-else class="space-y-2">
                <div
                  v-for="(file, index) in packFiles"
                  :key="file.assetId"
                  class="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg group"
                >
                  <!-- 序号 -->
                  <span class="text-xs text-slate-500 w-6 text-center shrink-0">{{ index + 1 }}</span>

                  <!-- 类型图标 -->
                  <div
                    class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
                    :class="file.fileType === 'MIDI' ? 'bg-amber-500/20' : 'bg-purple-500/20'"
                  >
                    <span class="text-sm">{{ file.fileType === 'MIDI' ? '🎹' : '🎵' }}</span>
                  </div>

                  <!-- 文件信息 -->
                  <div class="flex-1 min-w-0">
                    <p class="text-sm text-white truncate">{{ file.originalName || `文件 #${file.assetId}` }}</p>
                    <p class="text-xs text-slate-500">
                      {{ file.fileType }}
                      <span v-if="file.asset"> · {{ formatSize(file.asset.size) }}</span>
                    </p>
                  </div>

                  <!-- 操作按钮 -->
                  <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition">
                    <!-- 预览开关 -->
                    <button
                      v-if="file.fileType === 'AUDIO'"
                      class="p-1 transition"
                      :class="file.allowPreview !== false ? 'text-green-400 hover:text-green-300' : 'text-slate-500 hover:text-slate-400'"
                      :title="file.allowPreview !== false ? '允许预览（点击关闭）' : '禁止预览（点击开启）'"
                      @click="togglePackFilePreview(index)"
                    >
                      <svg v-if="file.allowPreview !== false" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072M12 6a7 7 0 017 7M8.464 15.536a5 5 0 010-7.072M12 18a7 7 0 01-7-7"></path>
                      </svg>
                      <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z"></path>
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2"></path>
                      </svg>
                    </button>
                    <button
                      v-if="index > 0"
                      class="p-1 text-slate-400 hover:text-white transition"
                      title="上移"
                      @click="movePackFileUp(index)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"></path>
                      </svg>
                    </button>
                    <button
                      v-if="index < packFiles.length - 1"
                      class="p-1 text-slate-400 hover:text-white transition"
                      title="下移"
                      @click="movePackFileDown(index)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                      </svg>
                    </button>
                    <button
                      class="p-1 text-slate-400 hover:text-red-400 transition"
                      title="移除"
                      @click="removePackFile(index)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- ===== 封面图片（两种模式共用） ===== -->
          <div class="bg-slate-800 border border-slate-700 rounded-xl p-5">
            <div class="flex items-center gap-2 mb-4">
              <div class="h-8 w-8 bg-blue-500/20 rounded-lg flex items-center justify-center">
                <svg class="w-4 h-4 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                </svg>
              </div>
              <div class="flex-1">
                <h3 class="text-white font-semibold text-sm">封面图片</h3>
                <p class="text-slate-400 text-xs">推荐 1400x1400px，正方形</p>
              </div>
              <div v-if="coverAsset" class="flex items-center gap-1 text-green-400 text-xs">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                </svg>
                已选择
              </div>
            </div>

            <!-- 已选封面预览 -->
            <div v-if="coverAsset" class="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg mb-3">
              <img :src="coverAsset.url" :alt="coverAsset.originalName" class="w-16 h-16 rounded-lg object-cover border border-slate-600" />
              <div class="flex-1 min-w-0">
                <p class="text-sm text-white truncate">{{ coverAsset.originalName }}</p>
                <p class="text-xs text-slate-400">{{ formatSize(coverAsset.size) }}</p>
              </div>
              <button class="text-slate-400 hover:text-red-400 p-1 transition" @click="clearCover" title="移除">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>

            <!-- 模式切换 -->
            <div v-if="!coverAsset" class="flex gap-2 mb-3">
              <button
                class="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
                :class="coverMode === 'upload' ? 'bg-blue-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'"
                @click="coverMode = 'upload'"
              >上传新文件</button>
              <button
                class="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
                :class="coverMode === 'library' ? 'bg-blue-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'"
                @click="coverMode = 'library'"
              >从资产库选择</button>
            </div>

            <UploadZone
              v-if="!coverAsset && coverMode === 'upload'"
              accept="image/*,.png,.jpg,.jpeg,.gif,.webp"
              label="拖拽封面图片到此处，或"
              hint="支持 PNG, JPG, GIF, WebP 格式"
              @uploaded="onCoverUploaded"
            />
            <button
              v-if="!coverAsset && coverMode === 'library'"
              class="w-full py-10 border-2 border-dashed border-slate-600 rounded-xl text-center hover:border-blue-500 hover:bg-blue-500/5 transition-all duration-200"
              @click="showCoverPicker = true"
            >
              <div class="flex flex-col items-center gap-2">
                <div class="h-12 w-12 bg-blue-500/10 rounded-xl flex items-center justify-center">
                  <svg class="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                  </svg>
                </div>
                <p class="text-slate-300 text-sm">点击浏览已上传的图片</p>
              </div>
            </button>
          </div>
        </div>

        <!-- ==================== 右栏：元数据表单 ==================== -->
        <div class="space-y-6">
          <h2 class="text-xl font-bold text-white flex items-center gap-2">
            <svg class="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
            </svg>
            作品信息
          </h2>

          <div class="bg-slate-800 border border-slate-700 rounded-xl p-6 space-y-5">

            <!-- 标题 -->
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">
                作品标题 <span class="text-red-400">*</span>
              </label>
              <input
                v-model="form.title"
                type="text"
                maxlength="200"
                :placeholder="isPack ? '为你的采样包取个名字...' : '为你的作品取个名字...'"
                class="w-full px-4 py-3 bg-slate-900 border border-slate-600 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
              <p class="mt-1 text-xs text-slate-500">{{ form.title.length }} / 200</p>
            </div>

            <!-- 描述 -->
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">作品描述</label>
              <textarea
                v-model="form.description"
                maxlength="5000"
                rows="4"
                :placeholder="isPack
                  ? '描述你的采样包...\n\n💡 建议包含：\n  • 包含内容概述（如 50 个 Trap 采样）\n  • 风格 / 适合的音乐类型\n  • BPM 范围（如 130-150 BPM）\n  • 调式信息（如果适用）'
                  : '描述你的作品...\n\n💡 建议包含以下信息方便买家了解：\n  • BPM（如 140 BPM）\n  • 调式（如 C Minor）\n  • 风格（如 Trap, Lo-Fi）\n  • 适合的用途'"
                class="w-full px-4 py-3 bg-slate-900 border border-slate-600 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition resize-none"
              ></textarea>
              <p class="mt-1 text-xs text-slate-500">{{ form.description.length }} / 5000</p>
            </div>

            <!-- 标签 -->
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">标签</label>
              <input
                v-model="form.tags"
                type="text"
                maxlength="500"
                :placeholder="isPack
                  ? '用逗号分隔，例如: sample-pack, trap, drum-kit, one-shot, 808'
                  : '用逗号分隔，例如: trap, dark, 140bpm, C minor, one-shot'"
                class="w-full px-4 py-3 bg-slate-900 border border-slate-600 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
              <p class="mt-1 text-xs text-slate-500">标签帮助买家找到你的作品</p>
            </div>

            <!-- 价格 -->
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">价格 (¥)</label>
              <input
                v-model.number="form.price"
                type="number"
                min="0"
                step="0.01"
                placeholder="0.00（免费）"
                class="w-full px-4 py-3 bg-slate-900 border border-slate-600 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
            </div>

            <!-- 库存 -->
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">库存数量</label>
              <input
                v-model.number="form.stock"
                type="number"
                min="0"
                step="1"
                placeholder="留空表示不限库存"
                class="w-full px-4 py-3 bg-slate-900 border border-slate-600 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              />
              <p class="mt-1 text-xs text-slate-500">设置后每次购买自动扣减，留空或清除表示不限库存</p>
            </div>

            <!-- 可见范围 -->
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">可见范围</label>
              <div class="grid grid-cols-2 gap-3">
                <button
                  v-for="opt in visibilityOptions"
                  :key="opt.value"
                  type="button"
                  class="p-3 rounded-lg border-2 transition-all duration-200 text-left"
                  :class="{
                    'border-blue-500 bg-blue-500/10': form.visibility === opt.value,
                    'border-slate-600 bg-slate-900 hover:border-slate-500': form.visibility !== opt.value
                  }"
                  @click="form.visibility = opt.value"
                >
                  <div class="flex items-center gap-2 mb-1">
                    <span class="text-lg">{{ opt.icon }}</span>
                    <span class="text-sm font-semibold text-white">{{ opt.label }}</span>
                  </div>
                  <p class="text-xs text-slate-400">{{ opt.desc }}</p>
                </button>
              </div>
            </div>

            <!-- 预览设置（仅付费作品显示） -->
            <Transition
              enter-active-class="transition duration-300 ease-out"
              enter-from-class="opacity-0 -translate-y-2"
              enter-to-class="opacity-100 translate-y-0"
              leave-active-class="transition duration-200 ease-in"
              leave-from-class="opacity-100 translate-y-0"
              leave-to-class="opacity-0 -translate-y-2"
            >
              <div v-if="isPaid" class="bg-slate-900/50 border border-amber-500/20 rounded-lg p-4 space-y-4">
                <h4 class="text-sm font-medium text-amber-300 flex items-center gap-2">
                  <span>🎧</span> 预览设置
                  <span class="text-xs font-normal text-slate-500">（付费作品）</span>
                </h4>

                <!-- 是否允许预览 -->
                <div class="flex items-center justify-between">
                  <div>
                    <p class="text-sm text-slate-300">允许买家试听</p>
                    <p class="text-xs text-slate-500">关闭后买家无法在购买前预览</p>
                  </div>
                  <button
                    type="button"
                    class="relative w-12 h-6 rounded-full transition-all duration-200"
                    :class="form.allowPreview ? 'bg-green-600' : 'bg-slate-600'"
                    @click="form.allowPreview = !form.allowPreview"
                  >
                    <span
                      class="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform duration-200"
                      :class="form.allowPreview ? 'translate-x-6' : 'translate-x-0'"
                    ></span>
                  </button>
                </div>

                <!-- 预览时长 -->
                <div v-if="form.allowPreview">
                  <label class="block text-xs font-medium text-slate-400 mb-2">预览时长（秒）</label>
                  <div class="flex items-center gap-3">
                    <input
                      v-model.number="form.previewDuration"
                      type="range"
                      min="5"
                      max="120"
                      step="5"
                      class="flex-1 h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-amber-500"
                    />
                    <div class="w-16 text-center">
                      <span class="text-white font-semibold text-sm">{{ form.previewDuration }}s</span>
                    </div>
                  </div>
                  <div class="flex justify-between text-xs text-slate-600 mt-1">
                    <span>5s</span>
                    <span>30s</span>
                    <span>60s</span>
                    <span>120s</span>
                  </div>
                </div>
              </div>
            </Transition>

            <div class="border-t border-slate-700"></div>

            <!-- 文件状态摘要 -->
            <div class="bg-slate-900/50 rounded-lg p-4 space-y-2">
              <h4 class="text-sm font-medium text-slate-300 mb-3">文件状态</h4>
              <div class="flex items-center justify-between text-sm">
                <span class="text-slate-400">作品类型</span>
                <span class="text-white font-medium">{{ isPack ? '📦 合集' : '🎵 单文件' }}</span>
              </div>
              <div class="flex items-center justify-between text-sm">
                <span class="text-slate-400">封面图片</span>
                <span v-if="coverAsset" class="text-green-400 flex items-center gap-1">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                  </svg>
                  {{ coverAsset.originalName }}
                </span>
                <span v-else class="text-slate-500">未选择（可选）</span>
              </div>
              <div class="flex items-center justify-between text-sm">
                <span class="text-slate-400">{{ isPack ? '合集文件' : (form.fileType === 'MIDI' ? 'MIDI 文件' : '音频文件') }}</span>
                <span v-if="isPack && packFiles.length > 0" class="text-green-400">{{ packFiles.length }} 个文件</span>
                <span v-else-if="!isPack && fileAsset" class="text-green-400 flex items-center gap-1">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                  </svg>
                  {{ fileAsset.originalName }}
                </span>
                <span v-else class="text-red-400 flex items-center gap-1">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"></path>
                  </svg>
                  必须选择
                </span>
              </div>
            </div>

            <!-- 错误提示 -->
            <Transition enter-active-class="transition duration-300 ease-out" enter-from-class="opacity-0 -translate-y-2" enter-to-class="opacity-100 translate-y-0">
              <div v-if="submitError" class="flex items-center gap-3 p-4 bg-red-500/10 border border-red-500/30 rounded-lg">
                <svg class="w-5 h-5 text-red-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
                <span class="text-red-300 text-sm">{{ submitError }}</span>
                <button class="ml-auto text-red-400 hover:text-red-300 text-xs underline" @click="submitError = ''">关闭</button>
              </div>
            </Transition>

            <!-- 提交按钮 -->
            <button
              :disabled="!canSubmit || isSubmitting"
              class="w-full py-3.5 rounded-lg font-semibold text-white transition-all duration-300 flex items-center justify-center gap-2"
              :class="{
                'bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 shadow-lg hover:shadow-xl': canSubmit && !isSubmitting,
                'bg-slate-700 text-slate-400 cursor-not-allowed': !canSubmit || isSubmitting
              }"
              @click="handleSubmit"
            >
              <svg v-if="isSubmitting" class="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              <span v-if="isSubmitting">发布中...</span>
              <span v-else-if="!canSubmit">{{ isPack ? '请填写标题并添加文件' : '请填写标题并选择文件' }}</span>
              <span v-else>
                <svg class="w-5 h-5 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                </svg>
                发布{{ isPack ? '合集' : '作品' }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- 资产选择弹窗 -->
    <AssetPickerModal v-model:visible="showCoverPicker" type="IMAGE" title="选择封面图片" @select="onCoverPicked" />
    <AssetPickerModal
      v-model:visible="showFilePicker"
      :type="form.fileType === 'MIDI' ? 'MIDI' : 'AUDIO'"
      :title="form.fileType === 'MIDI' ? '选择 MIDI 文件' : '选择音频文件'"
      @select="onFilePicked"
    />
    <!-- PACK 模式文件选择弹窗 -->
    <AssetPickerModal
      v-model:visible="showPackFilePicker"
      :type="packFilePickerType"
      :title="packFilePickerType === 'MIDI' ? '添加 MIDI 文件到合集' : '添加音频文件到合集'"
      @select="onPackFilePicked"
    />
  </div>
</template>

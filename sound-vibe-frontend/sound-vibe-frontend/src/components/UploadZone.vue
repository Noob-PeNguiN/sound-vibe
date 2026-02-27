<script setup lang="ts">
import { ref, computed } from 'vue'
import { assetApi } from '@/api/asset'
import type { Asset, AssetType } from '@/types/asset'

// ========== Props ==========

interface Props {
  /** 文件类型过滤（传给 input accept 属性） */
  accept?: string
  /** 上传区域标签（显示在拖拽区顶部） */
  label?: string
  /** 提示文字（显示支持的格式） */
  hint?: string
}

const props = withDefaults(defineProps<Props>(), {
  accept: 'audio/*,image/*,.wav,.mp3,.flac,.aac,.ogg,.m4a,.wma,.png,.jpg,.jpeg,.gif,.webp,.mid,.midi',
  label: '拖拽文件到此处，或',
  hint: '支持音频 (MP3, WAV, FLAC, AAC, OGG)、图片 (PNG, JPG, WebP) 和 MIDI (.mid, .midi)'
})

// ========== 状态 ==========

/** 是否正在上传 */
const isLoading = ref(false)
/** 是否正在拖拽悬停 */
const isDragOver = ref(false)
/** 上传结果（成功后的资产信息） */
const uploadedAsset = ref<Asset | null>(null)
/** 错误信息 */
const errorMessage = ref('')
/** 成功提示信息 */
const successMessage = ref('')
/** 当前选择/拖入的文件（用于本地预览） */
const selectedFile = ref<File | null>(null)
/** 本地预览 URL（图片） */
const localPreviewUrl = ref('')

// ========== 事件 ==========

const emit = defineEmits<{
  /** 上传成功后向父组件传递资产信息 */
  (e: 'uploaded', asset: Asset): void
}>()

// ========== 计算属性 ==========

/** 判断上传的文件是否为图片 */
const isImage = computed(() => {
  if (uploadedAsset.value) {
    return uploadedAsset.value.type === 'IMAGE'
  }
  if (selectedFile.value) {
    return selectedFile.value.type.startsWith('image/')
  }
  return false
})

/** 判断上传的文件是否为音频 */
const isAudio = computed(() => {
  if (uploadedAsset.value) {
    return uploadedAsset.value.type === 'AUDIO'
  }
  if (selectedFile.value) {
    return selectedFile.value.type.startsWith('audio/')
  }
  return false
})

/** 判断上传的文件是否为 MIDI */
const isMidi = computed(() => {
  if (uploadedAsset.value) {
    return uploadedAsset.value.type === 'MIDI'
  }
  if (selectedFile.value) {
    const ext = selectedFile.value.name.split('.').pop()?.toLowerCase()
    return ext === 'mid' || ext === 'midi'
  }
  return false
})

/** 格式化文件大小 */
const formatSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + units[i]
}

// ========== 隐藏 input 引用 ==========

const fileInputRef = ref<HTMLInputElement | null>(null)

// ========== 方法 ==========

/**
 * 点击触发文件选择
 */
const triggerFileInput = () => {
  fileInputRef.value?.click()
}

/**
 * 处理文件选择（来自 input）
 */
const onFileSelected = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    handleFile(file)
  }
  // 重置 input，允许再次选择同一文件
  target.value = ''
}

/**
 * 处理拖拽进入
 */
const onDragOver = (event: DragEvent) => {
  event.preventDefault()
  isDragOver.value = true
}

/**
 * 处理拖拽离开
 */
const onDragLeave = () => {
  isDragOver.value = false
}

/**
 * 处理拖拽放置
 */
const onDrop = (event: DragEvent) => {
  event.preventDefault()
  isDragOver.value = false

  const file = event.dataTransfer?.files?.[0]
  if (file) {
    handleFile(file)
  }
}

/**
 * 统一处理文件（选择或拖拽后调用）
 * 先设置本地预览，再执行上传
 */
const handleFile = async (file: File) => {
  // 重置状态
  clearState()
  selectedFile.value = file

  // 如果是图片，生成本地预览
  if (file.type.startsWith('image/')) {
    localPreviewUrl.value = URL.createObjectURL(file)
  }

  // 执行上传
  await doUpload(file)
}

/**
 * 调用 API 上传文件
 */
const doUpload = async (file: File) => {
  isLoading.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const asset = await assetApi.upload({ file })
    uploadedAsset.value = asset
    successMessage.value = `上传成功！文件: ${asset.originalName}`

    // 释放本地预览 URL（改用服务端返回的 URL）
    if (localPreviewUrl.value) {
      URL.revokeObjectURL(localPreviewUrl.value)
      localPreviewUrl.value = ''
    }

    // 向父组件发送事件 & 打印调试信息
    emit('uploaded', asset)
    console.log('[UploadZone] 上传成功:', asset)
  } catch (err: any) {
    errorMessage.value = err.message || '上传失败，请稍后重试'
    console.error('[UploadZone] 上传失败:', err)
  } finally {
    isLoading.value = false
  }
}

/**
 * 清除所有状态，准备下一次上传
 */
const clearState = () => {
  uploadedAsset.value = null
  errorMessage.value = ''
  successMessage.value = ''
  selectedFile.value = null
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
    localPreviewUrl.value = ''
  }
}

/**
 * 重新上传（重置状态）
 */
const resetUpload = () => {
  clearState()
}
</script>

<template>
  <div class="w-full">
    <!-- ==================== 拖拽上传区域 ==================== -->
    <div
      class="relative border-2 border-dashed rounded-xl transition-all duration-300 cursor-pointer"
      :class="{
        'border-blue-400 bg-blue-500/10 scale-[1.02]': isDragOver,
        'border-slate-600 bg-slate-800/50 hover:border-slate-500 hover:bg-slate-800': !isDragOver && !isLoading,
        'border-slate-700 bg-slate-800/30 cursor-wait': isLoading
      }"
      @click="!isLoading && triggerFileInput()"
      @dragover.prevent="onDragOver"
      @dragleave="onDragLeave"
      @drop.prevent="onDrop"
    >
      <!-- 隐藏的 file input -->
      <input
        ref="fileInputRef"
        type="file"
        class="hidden"
        :accept="props.accept"
        @change="onFileSelected"
      />

      <div class="flex flex-col items-center justify-center py-12 px-6">
        <!-- 上传中状态 -->
        <template v-if="isLoading">
          <div class="relative">
            <!-- 旋转动画 -->
            <svg class="animate-spin h-14 w-14 text-blue-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
          <p class="mt-4 text-lg font-medium text-blue-400">正在上传...</p>
          <p class="mt-1 text-sm text-slate-400">{{ selectedFile?.name }}</p>
        </template>

        <!-- 默认状态（等待上传） -->
        <template v-else>
          <!-- 上传图标 -->
          <div class="h-16 w-16 bg-slate-700/50 rounded-2xl flex items-center justify-center mb-4">
            <svg class="w-8 h-8 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12">
              </path>
            </svg>
          </div>
          <p class="text-lg font-medium text-white mb-1">
            {{ props.label }}
            <span class="text-blue-400 underline underline-offset-2">点击选择文件</span>
          </p>
          <p class="text-sm text-slate-400">
            {{ props.hint }}
          </p>
        </template>
      </div>
    </div>

    <!-- ==================== 消息提示区域 ==================== -->

    <!-- 成功提示 -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 -translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-2"
    >
      <div
        v-if="successMessage"
        class="mt-4 flex items-center gap-3 p-4 bg-green-500/10 border border-green-500/30 rounded-lg"
      >
        <svg class="w-5 h-5 text-green-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
        </svg>
        <span class="text-green-300 text-sm">{{ successMessage }}</span>
      </div>
    </Transition>

    <!-- 错误提示 -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 -translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-2"
    >
      <div
        v-if="errorMessage"
        class="mt-4 flex items-center gap-3 p-4 bg-red-500/10 border border-red-500/30 rounded-lg"
      >
        <svg class="w-5 h-5 text-red-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
        </svg>
        <span class="text-red-300 text-sm">{{ errorMessage }}</span>
        <button
          class="ml-auto text-red-400 hover:text-red-300 text-xs underline"
          @click="errorMessage = ''"
        >
          关闭
        </button>
      </div>
    </Transition>

    <!-- ==================== 上传结果 / 预览区域 ==================== -->
    <Transition
      enter-active-class="transition duration-500 ease-out"
      enter-from-class="opacity-0 translate-y-4"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-300 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-4"
    >
      <div
        v-if="uploadedAsset"
        class="mt-6 bg-slate-800 border border-slate-700 rounded-xl overflow-hidden"
      >
        <!-- 预览区 -->
        <div class="p-5">
          <div class="flex items-center justify-between mb-4">
            <h4 class="text-white font-semibold text-sm flex items-center gap-2">
              <svg class="w-4 h-4 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              上传完成
            </h4>
            <button
              class="text-xs text-slate-400 hover:text-white px-3 py-1.5 bg-slate-700 hover:bg-slate-600 rounded-lg transition duration-200"
              @click="resetUpload"
            >
              重新上传
            </button>
          </div>

          <!-- 图片预览 -->
          <div v-if="isImage && uploadedAsset.url" class="mb-4">
            <img
              :src="uploadedAsset.url"
              :alt="uploadedAsset.originalName"
              class="max-h-48 rounded-lg object-contain bg-slate-900/50 border border-slate-700"
            />
          </div>

          <!-- 音频预览 -->
          <div v-if="isAudio && uploadedAsset.url" class="mb-4">
            <div class="bg-slate-900/50 border border-slate-700 rounded-lg p-4 flex items-center gap-4">
              <div class="h-12 w-12 bg-purple-500/20 rounded-lg flex items-center justify-center shrink-0">
                <svg class="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3">
                  </path>
                </svg>
              </div>
              <audio
                :src="uploadedAsset.url"
                controls
                class="flex-1 h-10"
                style="filter: invert(1) hue-rotate(180deg);"
              ></audio>
            </div>
          </div>

          <!-- MIDI 预览（无法播放，显示信息） -->
          <div v-if="isMidi" class="mb-4">
            <div class="bg-slate-900/50 border border-slate-700 rounded-lg p-4 flex items-center gap-4">
              <div class="h-12 w-12 bg-amber-500/20 rounded-lg flex items-center justify-center shrink-0">
                <span class="text-2xl">🎹</span>
              </div>
              <div class="flex-1">
                <p class="text-white text-sm font-medium">MIDI 文件已上传</p>
                <p class="text-slate-400 text-xs">MIDI 文件无法在浏览器中预览播放</p>
              </div>
            </div>
          </div>

          <!-- 文件信息 -->
          <div class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
            <div class="flex justify-between">
              <span class="text-slate-400">文件名</span>
              <span class="text-white font-medium truncate ml-2 max-w-[180px]" :title="uploadedAsset.originalName">
                {{ uploadedAsset.originalName }}
              </span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate-400">大小</span>
              <span class="text-white font-medium">{{ formatSize(uploadedAsset.size) }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate-400">类型</span>
              <span
                class="px-2 py-0.5 rounded text-xs font-medium"
                :class="{
                  'bg-purple-500/20 text-purple-300': uploadedAsset.type === 'AUDIO',
                  'bg-blue-500/20 text-blue-300': uploadedAsset.type === 'IMAGE',
                  'bg-amber-500/20 text-amber-300': uploadedAsset.type === 'MIDI'
                }"
              >
                {{ uploadedAsset.type }}
              </span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate-400">扩展名</span>
              <span class="text-white font-medium">.{{ uploadedAsset.extension }}</span>
            </div>
            <div class="col-span-2 flex justify-between pt-2 border-t border-slate-700/50">
              <span class="text-slate-400">资产编码</span>
              <span class="text-slate-300 font-mono text-xs">{{ uploadedAsset.assetCode }}</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 本地预览（上传前/上传中）—— 仅当还没拿到服务端结果时显示 -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isLoading && localPreviewUrl && !uploadedAsset"
        class="mt-4 bg-slate-800/50 border border-slate-700/50 rounded-xl p-4"
      >
        <p class="text-xs text-slate-400 mb-2">本地预览</p>
        <img
          :src="localPreviewUrl"
          class="max-h-36 rounded-lg object-contain opacity-60"
          alt="预览"
        />
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
/**
 * AudioPreview 音频预览组件
 *
 * 功能：
 * - 免费作品：可播放完整音频
 * - 付费作品：仅允许试听前 N 秒（默认 30s）
 * - 自定义进度条 + 播放/暂停控制
 * - 显示当前时间 / 总时长
 * - 付费作品不允许拖动超过限制时长
 */
import { ref, computed, watch, onBeforeUnmount } from 'vue'

const props = withDefaults(defineProps<{
  /** 音频文件 URL */
  src: string
  /** 是否为付费作品（true 时限制试听时长） */
  isPaid?: boolean
  /** 试听时长限制（秒），仅 isPaid=true 时生效 */
  previewLimit?: number
}>(), {
  isPaid: false,
  previewLimit: 30
})

// ========== 状态 ==========
const audioRef = ref<HTMLAudioElement | null>(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const isLoaded = ref(false)
const isBuffering = ref(false)

/** 实际可播放的最大时长 */
const maxPlayableTime = computed(() => {
  if (!props.isPaid || duration.value === 0) return duration.value
  return Math.min(props.previewLimit, duration.value)
})

/** 进度百分比 (0-100) */
const progressPercent = computed(() => {
  if (maxPlayableTime.value === 0) return 0
  return Math.min((currentTime.value / maxPlayableTime.value) * 100, 100)
})

/** 格式化时间 mm:ss */
const formatTime = (seconds: number): string => {
  if (isNaN(seconds) || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

const currentTimeDisplay = computed(() => formatTime(currentTime.value))
const durationDisplay = computed(() => {
  if (props.isPaid) {
    return formatTime(maxPlayableTime.value)
  }
  return formatTime(duration.value)
})

// ========== 播放控制 ==========

const togglePlay = () => {
  const audio = audioRef.value
  if (!audio) return

  if (isPlaying.value) {
    audio.pause()
  } else {
    // 如果在末尾（或超过限制），重置到开头
    if (props.isPaid && currentTime.value >= maxPlayableTime.value) {
      audio.currentTime = 0
    }
    audio.play().catch(err => {
      console.warn('[AudioPreview] 播放失败:', err)
    })
  }
}

const onPlay = () => { isPlaying.value = true }
const onPause = () => { isPlaying.value = false }

const onTimeUpdate = () => {
  const audio = audioRef.value
  if (!audio) return
  currentTime.value = audio.currentTime

  // 付费作品到达限制时长时暂停
  if (props.isPaid && audio.currentTime >= maxPlayableTime.value) {
    audio.pause()
    audio.currentTime = maxPlayableTime.value
    currentTime.value = maxPlayableTime.value
  }
}

const onLoadedMetadata = () => {
  const audio = audioRef.value
  if (!audio) return
  duration.value = audio.duration
  isLoaded.value = true
}

const onWaiting = () => { isBuffering.value = true }
const onCanPlay = () => { isBuffering.value = false }

const onEnded = () => {
  isPlaying.value = false
  currentTime.value = 0
  if (audioRef.value) {
    audioRef.value.currentTime = 0
  }
}

// ========== 进度条拖动 ==========

const progressBarRef = ref<HTMLDivElement | null>(null)

const seekTo = (event: MouseEvent) => {
  const bar = progressBarRef.value
  const audio = audioRef.value
  if (!bar || !audio) return

  const rect = bar.getBoundingClientRect()
  const x = event.clientX - rect.left
  const percent = Math.max(0, Math.min(x / rect.width, 1))
  const seekTime = percent * maxPlayableTime.value

  audio.currentTime = seekTime
  currentTime.value = seekTime
}

// ========== 清理 ==========

onBeforeUnmount(() => {
  const audio = audioRef.value
  if (audio) {
    audio.pause()
    audio.src = ''
  }
})

// ========== 监听 src 变化 ==========

watch(() => props.src, () => {
  isPlaying.value = false
  currentTime.value = 0
  duration.value = 0
  isLoaded.value = false
})
</script>

<template>
  <div class="audio-preview">
    <!-- 隐藏的原生 audio 元素 -->
    <audio
      ref="audioRef"
      :src="src"
      preload="metadata"
      @play="onPlay"
      @pause="onPause"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
      @ended="onEnded"
      @waiting="onWaiting"
      @canplay="onCanPlay"
    />

    <div class="flex items-center gap-3">
      <!-- 播放/暂停按钮 -->
      <button
        class="w-10 h-10 rounded-full flex items-center justify-center shrink-0 transition-all duration-200"
        :class="isPlaying
          ? 'bg-blue-500 hover:bg-blue-400 text-white shadow-lg shadow-blue-500/30'
          : 'bg-slate-700 hover:bg-slate-600 text-white'"
        @click="togglePlay"
      >
        <!-- 缓冲中 -->
        <svg v-if="isBuffering" class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        <!-- 暂停图标 -->
        <svg v-else-if="isPlaying" class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
          <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
        </svg>
        <!-- 播放图标 -->
        <svg v-else class="w-5 h-5 ml-0.5" fill="currentColor" viewBox="0 0 24 24">
          <path d="M8 5v14l11-7z" />
        </svg>
      </button>

      <!-- 进度条区域 -->
      <div class="flex-1 min-w-0">
        <div
          ref="progressBarRef"
          class="relative h-2 bg-slate-700 rounded-full cursor-pointer group"
          @click="seekTo"
        >
          <!-- 已播放进度 -->
          <div
            class="absolute top-0 left-0 h-full rounded-full transition-all duration-100"
            :class="isPaid ? 'bg-amber-500' : 'bg-blue-500'"
            :style="{ width: `${progressPercent}%` }"
          />
          <!-- 拖动把手 -->
          <div
            class="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full opacity-0 group-hover:opacity-100 transition-opacity shadow-md"
            :class="isPaid ? 'bg-amber-400' : 'bg-blue-400'"
            :style="{ left: `calc(${progressPercent}% - 6px)` }"
          />
        </div>

        <!-- 时间显示 -->
        <div class="flex items-center justify-between mt-1">
          <span class="text-xs text-slate-400">{{ currentTimeDisplay }}</span>
          <div class="flex items-center gap-1.5">
            <!-- 付费标识 -->
            <span v-if="isPaid" class="text-xs text-amber-400/80 flex items-center gap-0.5">
              <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
              试听 {{ previewLimit }}s
            </span>
            <span class="text-xs text-slate-400">{{ durationDisplay }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

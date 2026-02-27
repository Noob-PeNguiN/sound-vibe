<script setup lang="ts">
/**
 * MidiPlayer — MIDI 文件预览播放组件
 *
 * 功能：
 * - 通过 @tonejs/midi 解析 MIDI 文件
 * - 使用 Tone.js 内置合成器实时演奏
 * - 支持乐器音色选择（钢琴 / 合成器 / 电钢琴 / 弦乐 / Pad）
 * - 付费作品限制试听时长（默认 30s）
 * - 自定义进度条 + 播放 / 暂停 / 停止控制
 * - 显示 MIDI 轨道信息（音轨数、音符数、时长）
 */
import { ref, computed, watch, onBeforeUnmount, shallowRef } from 'vue'
import { Midi } from '@tonejs/midi'
import * as Tone from 'tone'

const props = withDefaults(defineProps<{
  /** MIDI 文件 URL */
  src: string
  /** 是否为付费作品 */
  isPaid?: boolean
  /** 试听时长限制（秒） */
  previewLimit?: number
}>(), {
  isPaid: false,
  previewLimit: 30
})

// ========== 乐器选项 ==========

interface InstrumentOption {
  id: string
  label: string
  icon: string
  create: () => Tone.PolySynth
}

const instrumentOptions: InstrumentOption[] = [
  {
    id: 'piano',
    label: '钢琴',
    icon: '🎹',
    create: () => new Tone.PolySynth(Tone.Synth, {
      oscillator: { type: 'triangle8' },
      envelope: { attack: 0.005, decay: 0.3, sustain: 0.2, release: 1.2 }
    })
  },
  {
    id: 'synth',
    label: '合成器',
    icon: '🎛️',
    create: () => new Tone.PolySynth(Tone.Synth, {
      oscillator: { type: 'sawtooth' },
      envelope: { attack: 0.01, decay: 0.2, sustain: 0.4, release: 0.8 }
    })
  },
  {
    id: 'epiano',
    label: '电钢琴',
    icon: '🎶',
    create: () => new Tone.PolySynth(Tone.FMSynth, {
      harmonicity: 3.01,
      modulationIndex: 14,
      oscillator: { type: 'triangle' },
      envelope: { attack: 0.002, decay: 0.5, sustain: 0.1, release: 1.2 },
      modulation: { type: 'square' },
      modulationEnvelope: { attack: 0.002, decay: 0.2, sustain: 0, release: 0.2 }
    })
  },
  {
    id: 'strings',
    label: '弦乐',
    icon: '🎻',
    create: () => new Tone.PolySynth(Tone.AMSynth, {
      harmonicity: 2,
      oscillator: { type: 'sine' },
      envelope: { attack: 0.3, decay: 0.1, sustain: 0.8, release: 1.5 },
      modulation: { type: 'triangle' },
      modulationEnvelope: { attack: 0.5, decay: 0, sustain: 1, release: 0.5 }
    })
  },
  {
    id: 'pad',
    label: 'Pad',
    icon: '🌊',
    create: () => new Tone.PolySynth(Tone.Synth, {
      oscillator: { type: 'sine4' },
      envelope: { attack: 0.8, decay: 0.3, sustain: 0.7, release: 2.0 }
    })
  }
]

// ========== 状态 ==========
const selectedInstrumentId = ref('piano')
const midiData = shallowRef<Midi | null>(null)
const isLoading = ref(false)
const loadError = ref('')
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const totalNotes = ref(0)
const trackCount = ref(0)

/** 活跃的 Tone.js 合成器实例 */
let activeSynth: Tone.PolySynth | null = null
/** 定时器：更新当前播放时间 */
let progressTimer: ReturnType<typeof setInterval> | null = null
/** 调度的 Tone.js 事件 ID 列表（用于停止时清理） */
let scheduledEvents: number[] = []
/** 播放开始的绝对时间戳 */
let playStartTransportTime = 0

const maxPlayableTime = computed(() => {
  if (!props.isPaid || duration.value === 0) return duration.value
  return Math.min(props.previewLimit, duration.value)
})

const progressPercent = computed(() => {
  if (maxPlayableTime.value === 0) return 0
  return Math.min((currentTime.value / maxPlayableTime.value) * 100, 100)
})

const formatTime = (seconds: number): string => {
  if (isNaN(seconds) || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

const currentTimeDisplay = computed(() => formatTime(currentTime.value))
const durationDisplay = computed(() => formatTime(props.isPaid ? maxPlayableTime.value : duration.value))

// ========== MIDI 信息 ==========
const midiInfo = computed(() => {
  if (!midiData.value) return null
  return {
    tracks: trackCount.value,
    notes: totalNotes.value,
    duration: formatTime(duration.value),
    bpm: Math.round(midiData.value.header.tempos?.[0]?.bpm ?? 120)
  }
})

// ========== 加载 MIDI ==========

const loadMidi = async () => {
  if (!props.src) return
  isLoading.value = true
  loadError.value = ''

  try {
    const response = await fetch(props.src)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const arrayBuffer = await response.arrayBuffer()
    const midi = new Midi(arrayBuffer)

    midiData.value = midi
    duration.value = midi.duration

    // 统计音符和轨道
    let notes = 0
    let tracks = 0
    midi.tracks.forEach(track => {
      if (track.notes.length > 0) {
        tracks++
        notes += track.notes.length
      }
    })
    totalNotes.value = notes
    trackCount.value = tracks
  } catch (err: any) {
    loadError.value = err.message || 'MIDI 文件加载失败'
    console.error('[MidiPlayer] 加载失败:', err)
  } finally {
    isLoading.value = false
  }
}

// ========== 播放控制 ==========

const play = async () => {
  if (!midiData.value || isPlaying.value) return

  // 确保 Tone.js AudioContext 已启动（需要用户交互）
  await Tone.start()

  // 创建合成器
  const instrumentOpt = instrumentOptions.find(o => o.id === selectedInstrumentId.value) || instrumentOptions[0]
  activeSynth = instrumentOpt.create()
  activeSynth.maxPolyphony = 32
  activeSynth.toDestination()

  // 清除之前的调度
  Tone.getTransport().cancel()
  scheduledEvents = []

  const now = Tone.getTransport().seconds
  playStartTransportTime = now

  // 遍历所有轨道的音符，调度到 Tone Transport
  const midi = midiData.value
  const maxTime = maxPlayableTime.value

  midi.tracks.forEach(track => {
    track.notes.forEach(note => {
      // 跳过超出试听范围的音符
      if (note.time > maxTime) return

      const noteEnd = note.time + note.duration
      const actualDuration = Math.min(note.duration, maxTime - note.time)

      const eventId = Tone.getTransport().schedule((time) => {
        if (activeSynth) {
          activeSynth.triggerAttackRelease(
            note.name,
            actualDuration,
            time,
            note.velocity
          )
        }
      }, now + note.time)
      scheduledEvents.push(eventId)
    })
  })

  // 调度停止事件
  const stopId = Tone.getTransport().schedule(() => {
    stop()
  }, now + maxTime + 0.5)
  scheduledEvents.push(stopId)

  // 启动传输
  Tone.getTransport().start()
  isPlaying.value = true

  // 启动进度更新定时器
  progressTimer = setInterval(() => {
    const elapsed = Tone.getTransport().seconds - playStartTransportTime
    currentTime.value = Math.min(elapsed, maxTime)

    if (elapsed >= maxTime) {
      stop()
    }
  }, 50)
}

const stop = () => {
  isPlaying.value = false
  currentTime.value = 0

  // 清理定时器
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }

  // 停止并清理 Transport
  Tone.getTransport().stop()
  Tone.getTransport().cancel()
  scheduledEvents = []

  // 释放合成器
  if (activeSynth) {
    activeSynth.releaseAll()
    activeSynth.dispose()
    activeSynth = null
  }
}

const togglePlay = () => {
  if (isPlaying.value) {
    stop()
  } else {
    play()
  }
}

// ========== 乐器切换 ==========

const switchInstrument = (id: string) => {
  const wasPlaying = isPlaying.value
  if (wasPlaying) stop()
  selectedInstrumentId.value = id
  // 如果之前在播放中，自动重新开始（切换音色）
  if (wasPlaying) {
    setTimeout(() => play(), 100)
  }
}

// ========== 进度条点击 ==========

const progressBarRef = ref<HTMLDivElement | null>(null)

const seekTo = (event: MouseEvent) => {
  // MIDI 播放不支持 seek，因为是基于 scheduled events 的
  // 点击时提示用户
}

// ========== 生命周期 ==========

watch(() => props.src, () => {
  stop()
  midiData.value = null
  currentTime.value = 0
  duration.value = 0
  loadError.value = ''
  loadMidi()
}, { immediate: true })

onBeforeUnmount(() => {
  stop()
})
</script>

<template>
  <div class="midi-player">
    <!-- 加载中 -->
    <div v-if="isLoading" class="flex items-center justify-center py-6 gap-2">
      <svg class="animate-spin h-5 w-5 text-amber-400" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span class="text-sm text-slate-400">正在加载 MIDI 文件...</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
      <span class="text-red-400 text-sm">{{ loadError }}</span>
      <button class="text-xs text-red-300 underline hover:text-red-200" @click="loadMidi">重试</button>
    </div>

    <!-- 已加载 -->
    <div v-else-if="midiData" class="space-y-3">
      <!-- MIDI 信息条 -->
      <div v-if="midiInfo" class="flex items-center gap-3 text-xs text-slate-400 flex-wrap">
        <span class="flex items-center gap-1">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
          </svg>
          {{ midiInfo.tracks }} 轨道
        </span>
        <span>{{ midiInfo.notes }} 音符</span>
        <span>{{ midiInfo.bpm }} BPM</span>
        <span>{{ midiInfo.duration }}</span>
      </div>

      <!-- 乐器选择 -->
      <div class="flex items-center gap-1.5 flex-wrap">
        <span class="text-xs text-slate-500 shrink-0 mr-1">音色:</span>
        <button
          v-for="opt in instrumentOptions"
          :key="opt.id"
          class="px-2.5 py-1 rounded-md text-xs font-medium transition-all duration-150"
          :class="{
            'bg-amber-500/20 text-amber-300 border border-amber-500/40': selectedInstrumentId === opt.id,
            'bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-slate-200 border border-slate-700': selectedInstrumentId !== opt.id
          }"
          @click="switchInstrument(opt.id)"
        >
          <span class="mr-0.5">{{ opt.icon }}</span>
          {{ opt.label }}
        </button>
      </div>

      <!-- 播放控制 -->
      <div class="flex items-center gap-3">
        <!-- 播放/停止按钮 -->
        <button
          class="w-10 h-10 rounded-full flex items-center justify-center shrink-0 transition-all duration-200"
          :class="isPlaying
            ? 'bg-amber-500 hover:bg-amber-400 text-white shadow-lg shadow-amber-500/30'
            : 'bg-slate-700 hover:bg-slate-600 text-white'"
          @click="togglePlay"
        >
          <!-- 停止图标（正方形） -->
          <svg v-if="isPlaying" class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
            <rect x="6" y="6" width="12" height="12" rx="1" />
          </svg>
          <!-- 播放图标 -->
          <svg v-else class="w-5 h-5 ml-0.5" fill="currentColor" viewBox="0 0 24 24">
            <path d="M8 5v14l11-7z" />
          </svg>
        </button>

        <!-- 进度条 -->
        <div class="flex-1 min-w-0">
          <div
            ref="progressBarRef"
            class="relative h-2 bg-slate-700 rounded-full group"
          >
            <div
              class="absolute top-0 left-0 h-full bg-amber-500 rounded-full transition-all duration-100"
              :style="{ width: `${progressPercent}%` }"
            />
            <div
              class="absolute top-1/2 -translate-y-1/2 w-3 h-3 bg-amber-400 rounded-full opacity-0 group-hover:opacity-100 transition-opacity shadow-md"
              :style="{ left: `calc(${progressPercent}% - 6px)` }"
            />
          </div>

          <div class="flex items-center justify-between mt-1">
            <span class="text-xs text-slate-400">{{ currentTimeDisplay }}</span>
            <div class="flex items-center gap-1.5">
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
  </div>
</template>

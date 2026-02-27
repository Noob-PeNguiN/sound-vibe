<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { generateApi } from '@/api/generate'
import type { MusicGenerateResult, GenerateHistoryItem } from '@/types/generate'

const router = useRouter()
const userStore = useUserStore()

// ========== 表单状态 ==========
const prompt = ref('')
const duration = ref(5)
const isGenerating = ref(false)
const errorMessage = ref('')

// ========== 生成结果 ==========
const currentResult = ref<MusicGenerateResult | null>(null)

// ========== 历史记录（本地存储） ==========
const HISTORY_KEY = 'soundvibe-generate-history'
const history = ref<GenerateHistoryItem[]>([])
const showHistory = ref(false)

// ========== 提示词预设 ==========
const presets = [
  { label: 'Lo-fi Hip Hop', prompt: 'lo-fi hip hop beat with warm vinyl crackle, mellow piano chords, and soft boom bap drums' },
  { label: 'Cinematic Epic', prompt: 'cinematic orchestral music with dramatic strings, deep brass, and thundering percussion' },
  { label: 'Trap Beat', prompt: 'dark trap beat with heavy 808 bass, hi-hat rolls, and atmospheric synth pads' },
  { label: 'Chill Acoustic', prompt: 'relaxing acoustic guitar melody with soft fingerpicking and gentle reverb' },
  { label: 'EDM Drop', prompt: 'energetic EDM build-up and drop with saw synths, punchy kick drums, and sidechain compression' },
  { label: 'Jazz Piano', prompt: 'smooth jazz piano trio with walking bassline, brushed drums, and rich chord voicings' },
  { label: 'Ambient Pad', prompt: 'ethereal ambient soundscape with evolving synthesizer pads, reverb, and slow atmospheric textures' },
  { label: 'Funk Groove', prompt: 'funky groove with slap bass, wah-wah guitar, tight drums, and brass stabs' },
]

const durationOptions = [3, 5, 8, 10, 15, 20, 30]

const charCount = computed(() => prompt.value.length)
const canGenerate = computed(() => prompt.value.trim().length > 0 && !isGenerating.value)

onMounted(() => {
  loadHistory()
})

const loadHistory = () => {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (raw) history.value = JSON.parse(raw)
  } catch {
    history.value = []
  }
}

const saveHistory = (item: GenerateHistoryItem) => {
  history.value.unshift(item)
  if (history.value.length > 20) history.value = history.value.slice(0, 20)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
}

const clearHistory = () => {
  history.value = []
  localStorage.removeItem(HISTORY_KEY)
}

const applyPreset = (presetPrompt: string) => {
  prompt.value = presetPrompt
}

const applyHistoryItem = (item: GenerateHistoryItem) => {
  prompt.value = item.prompt
  duration.value = item.duration
  currentResult.value = { url: item.url, prompt: item.prompt, duration: item.duration }
  showHistory.value = false
}

const handleGenerate = async () => {
  if (!canGenerate.value) return

  isGenerating.value = true
  errorMessage.value = ''
  currentResult.value = null

  try {
    const result = await generateApi.generateMusic({
      prompt: prompt.value.trim(),
      duration: duration.value
    })
    currentResult.value = result

    saveHistory({
      id: Date.now().toString(36),
      prompt: result.prompt,
      duration: result.duration,
      url: result.url,
      createdAt: new Date().toISOString()
    })
  } catch (err: any) {
    errorMessage.value = err.message || 'AI 音乐生成失败，请稍后重试'
    console.error('[Generate] 生成失败:', err)
  } finally {
    isGenerating.value = false
  }
}

const formatDate = (dateStr: string): string => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const handleLogout = async () => {
  if (confirm('确定要退出登录吗？')) {
    await userStore.logout()
    router.push('/login')
  }
}
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
              <p class="text-xs text-slate-400">AI 音乐工坊</p>
            </div>
          </router-link>
          <div class="flex items-center gap-3">
            <router-link
              to="/"
              class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
              </svg>
              市场
            </router-link>
            <router-link
              to="/assets"
              class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition flex items-center gap-2"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
              </svg>
              工作台
            </router-link>
            <button
              class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition"
              @click="handleLogout"
            >退出</button>
          </div>
        </div>
      </div>
    </nav>

    <!-- Hero 区域 -->
    <div class="bg-gradient-to-b from-purple-950/60 via-slate-900 to-slate-900 border-b border-slate-700/30">
      <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-12 text-center">
        <div class="inline-flex items-center gap-2 px-4 py-1.5 bg-purple-500/20 border border-purple-500/30 rounded-full text-purple-300 text-sm font-medium mb-6">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
          </svg>
          Powered by MusicGen
        </div>
        <h2 class="text-4xl font-bold mb-3">
          <span class="bg-gradient-to-r from-purple-400 via-fuchsia-400 to-pink-400 bg-clip-text text-transparent">AI 音乐生成</span>
        </h2>
        <p class="text-slate-400 text-lg">用文字描述你想要的音乐，AI 帮你一键生成</p>
      </div>
    </div>

    <!-- 主内容区 -->
    <main class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">

      <!-- ========== 生成表单 ========== -->
      <div class="bg-slate-800 border border-slate-700 rounded-2xl p-6 shadow-xl">
        <!-- 描述输入 -->
        <div class="mb-5">
          <div class="flex items-center justify-between mb-2">
            <label class="text-white font-semibold text-sm flex items-center gap-2">
              <svg class="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
              </svg>
              音乐描述
            </label>
            <span class="text-xs" :class="charCount > 480 ? 'text-amber-400' : 'text-slate-500'">{{ charCount }} / 512</span>
          </div>
          <textarea
            v-model="prompt"
            rows="4"
            maxlength="512"
            placeholder="描述你想要的音乐风格、乐器、情绪等...&#10;例如: A heavy electronic drum loop with dark bassline and atmospheric synth pads"
            class="w-full px-4 py-3 bg-slate-900 border border-slate-600 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500/50 text-sm leading-relaxed resize-none transition"
            :disabled="isGenerating"
            @keydown.ctrl.enter="handleGenerate"
          ></textarea>
        </div>

        <!-- 快捷预设 -->
        <div class="mb-5">
          <p class="text-slate-400 text-xs font-medium mb-2">快捷预设</p>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="preset in presets"
              :key="preset.label"
              class="px-3 py-1.5 bg-slate-700/60 hover:bg-purple-600/30 border border-slate-600 hover:border-purple-500/50 text-slate-300 hover:text-purple-200 rounded-lg text-xs font-medium transition-all duration-200"
              :disabled="isGenerating"
              @click="applyPreset(preset.prompt)"
            >
              {{ preset.label }}
            </button>
          </div>
        </div>

        <!-- 时长选择 + 生成按钮 -->
        <div class="flex flex-col sm:flex-row gap-4 items-start sm:items-end">
          <div class="flex-shrink-0">
            <label class="text-white font-semibold text-sm mb-2 block flex items-center gap-2">
              <svg class="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              生成时长
            </label>
            <div class="flex gap-1.5">
              <button
                v-for="opt in durationOptions"
                :key="opt"
                class="px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200"
                :class="duration === opt
                  ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/30'
                  : 'bg-slate-700 text-slate-300 hover:bg-slate-600 border border-slate-600'"
                :disabled="isGenerating"
                @click="duration = opt"
              >
                {{ opt }}s
              </button>
            </div>
          </div>

          <div class="flex-1 flex justify-end">
            <button
              class="px-8 py-3 rounded-xl text-base font-bold transition-all duration-300 flex items-center gap-3 shadow-xl"
              :class="canGenerate
                ? 'bg-gradient-to-r from-purple-600 to-fuchsia-600 hover:from-purple-500 hover:to-fuchsia-500 text-white shadow-purple-600/30 hover:shadow-purple-500/40 hover:scale-[1.02]'
                : 'bg-slate-700 text-slate-400 cursor-not-allowed'"
              :disabled="!canGenerate"
              @click="handleGenerate"
            >
              <template v-if="isGenerating">
                <svg class="animate-spin w-5 h-5" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                </svg>
                AI 生成中...
              </template>
              <template v-else>
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                </svg>
                生成音乐
              </template>
            </button>
          </div>
        </div>

        <!-- 生成进度提示 -->
        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="opacity-0 translate-y-2"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="opacity-100 translate-y-0"
          leave-to-class="opacity-0 translate-y-2"
        >
          <div v-if="isGenerating" class="mt-5 flex items-center gap-3 bg-purple-900/20 border border-purple-500/20 rounded-xl px-5 py-4">
            <div class="relative w-10 h-10 shrink-0">
              <div class="absolute inset-0 rounded-full bg-purple-500/20 animate-ping"></div>
              <div class="relative w-10 h-10 rounded-full bg-purple-600/30 flex items-center justify-center">
                <svg class="w-5 h-5 text-purple-300 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3"></path>
                </svg>
              </div>
            </div>
            <div>
              <p class="text-purple-200 text-sm font-medium">MusicGen 正在创作中...</p>
              <p class="text-purple-400/70 text-xs mt-0.5">生成 {{ duration }} 秒音频大约需要 {{ Math.max(10, duration * 3) }}~{{ duration * 6 }} 秒</p>
            </div>
          </div>
        </Transition>

        <!-- 错误提示 -->
        <div v-if="errorMessage" class="mt-5 flex items-start gap-3 bg-red-900/20 border border-red-500/30 rounded-xl px-5 py-4">
          <svg class="w-5 h-5 text-red-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.732-.833-2.5 0L4.268 16.5c-.77.833.192 2.5 1.732 2.5z"></path>
          </svg>
          <div>
            <p class="text-red-300 text-sm font-medium">生成失败</p>
            <p class="text-red-400/70 text-xs mt-0.5">{{ errorMessage }}</p>
          </div>
        </div>
      </div>

      <!-- ========== 生成结果 ========== -->
      <Transition
        enter-active-class="transition duration-500 ease-out"
        enter-from-class="opacity-0 translate-y-6"
        enter-to-class="opacity-100 translate-y-0"
      >
        <div v-if="currentResult" class="bg-slate-800 border border-slate-700 rounded-2xl overflow-hidden shadow-xl">
          <!-- 结果头部 -->
          <div class="bg-gradient-to-r from-purple-900/40 to-fuchsia-900/40 border-b border-slate-700/50 px-6 py-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="w-12 h-12 bg-gradient-to-br from-purple-500 to-fuchsia-500 rounded-xl flex items-center justify-center shadow-lg shadow-purple-600/30">
                  <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3"></path>
                  </svg>
                </div>
                <div>
                  <h3 class="text-white font-bold text-lg">生成完成</h3>
                  <p class="text-slate-400 text-xs">{{ currentResult.duration }} 秒 · WAV 格式</p>
                </div>
              </div>
              <a
                :href="currentResult.url"
                target="_blank"
                class="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition flex items-center gap-2"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                </svg>
                下载
              </a>
            </div>
          </div>

          <!-- 音频播放器 -->
          <div class="px-6 py-5">
            <audio
              :src="currentResult.url"
              controls
              preload="auto"
              class="w-full rounded-lg"
              style="filter: invert(0.85) hue-rotate(180deg);"
            >
              你的浏览器不支持音频播放
            </audio>
          </div>

          <!-- 生成参数 -->
          <div class="px-6 pb-5">
            <div class="bg-slate-900/50 rounded-xl p-4">
              <p class="text-slate-500 text-xs font-medium mb-1.5">Prompt</p>
              <p class="text-slate-300 text-sm leading-relaxed">{{ currentResult.prompt }}</p>
            </div>
          </div>
        </div>
      </Transition>

      <!-- ========== 历史记录 ========== -->
      <div class="bg-slate-800 border border-slate-700 rounded-2xl overflow-hidden shadow-xl">
        <button
          class="w-full flex items-center justify-between px-6 py-4 hover:bg-slate-750 transition"
          @click="showHistory = !showHistory"
        >
          <div class="flex items-center gap-2">
            <svg class="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>
            <span class="text-white font-semibold text-sm">生成历史</span>
            <span v-if="history.length > 0" class="px-2 py-0.5 bg-slate-700 text-slate-300 text-xs rounded-full">
              {{ history.length }}
            </span>
          </div>
          <svg
            class="w-4 h-4 text-slate-400 transition-transform duration-200"
            :class="showHistory ? 'rotate-180' : ''"
            fill="none" stroke="currentColor" viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
          </svg>
        </button>

        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="opacity-0"
          enter-to-class="opacity-100"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
        >
          <div v-if="showHistory" class="border-t border-slate-700">
            <!-- 空状态 -->
            <div v-if="history.length === 0" class="text-center py-10">
              <p class="text-slate-500 text-sm">暂无生成记录</p>
            </div>

            <!-- 历史列表 -->
            <div v-else>
              <div class="px-6 py-3 flex justify-end border-b border-slate-700/50">
                <button
                  class="text-xs text-slate-500 hover:text-red-400 transition"
                  @click="clearHistory"
                >清空历史</button>
              </div>
              <div class="max-h-[400px] overflow-y-auto divide-y divide-slate-700/50">
                <div
                  v-for="item in history"
                  :key="item.id"
                  class="flex items-center gap-4 px-6 py-3.5 hover:bg-slate-750 transition cursor-pointer group"
                  @click="applyHistoryItem(item)"
                >
                  <div class="w-10 h-10 rounded-lg bg-purple-500/15 flex items-center justify-center shrink-0 group-hover:bg-purple-500/25 transition">
                    <svg class="w-5 h-5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3"></path>
                    </svg>
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-white text-sm truncate">{{ item.prompt }}</p>
                    <p class="text-slate-500 text-xs mt-0.5">{{ item.duration }}s · {{ formatDate(item.createdAt) }}</p>
                  </div>
                  <a
                    :href="item.url"
                    target="_blank"
                    class="p-2 text-slate-500 hover:text-blue-400 transition shrink-0"
                    title="下载"
                    @click.stop
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                    </svg>
                  </a>
                </div>
              </div>
            </div>
          </div>
        </Transition>
      </div>

      <!-- 使用提示 -->
      <div class="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-6">
        <h4 class="text-white font-semibold text-sm mb-3 flex items-center gap-2">
          <svg class="w-4 h-4 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
          </svg>
          使用技巧
        </h4>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
          <div class="flex items-start gap-2">
            <span class="text-purple-400 mt-0.5">-</span>
            <p class="text-slate-400">用英文描述效果最好，支持风格、乐器、情绪等关键词</p>
          </div>
          <div class="flex items-start gap-2">
            <span class="text-purple-400 mt-0.5">-</span>
            <p class="text-slate-400">生成时长越长，等待时间也越长（约 3~6 秒/秒音频）</p>
          </div>
          <div class="flex items-start gap-2">
            <span class="text-purple-400 mt-0.5">-</span>
            <p class="text-slate-400">描述越详细具体，生成结果越贴合你的想象</p>
          </div>
          <div class="flex items-start gap-2">
            <span class="text-purple-400 mt-0.5">-</span>
            <p class="text-slate-400">生成的音频为 WAV 格式，可以直接导入 DAW 使用</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.bg-slate-750 {
  background-color: rgb(40 44 54);
}
</style>

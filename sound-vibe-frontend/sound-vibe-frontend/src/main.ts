import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import './style.css'
import App from './App.vue'

const app = createApp(App)

// 安装 Pinia 状态管理
app.use(createPinia())

// 安装 Vue Router
app.use(router)

// 挂载应用
app.mount('#app')

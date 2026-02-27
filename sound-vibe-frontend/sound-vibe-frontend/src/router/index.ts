import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import LoginView from '@/views/auth/LoginView.vue'
import RegisterView from '@/views/auth/RegisterView.vue'

/**
 * 路由配置
 *
 * 路由设计：
 * - / (主页) → 市场/作品目录（公开浏览，无需登录）
 * - /assets → 工作台/我的资产（需登录）
 * - /publish → 发布作品（需登录）
 * - /cart → 购物车（需登录）
 * - /orders → 我的订单（需登录）
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/CatalogView.vue'),
    meta: {
      title: 'SoundVibe - 音乐资产交易平台',
      requiresAuth: false  // 主页公开浏览，无需登录
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: {
      title: '登录 - SoundVibe',
      requiresAuth: false
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: RegisterView,
    meta: {
      title: '注册 - SoundVibe',
      requiresAuth: false
    }
  },
  {
    path: '/dashboard',
    redirect: '/'
  },
  {
    path: '/assets',
    name: 'Assets',
    component: () => import('@/views/AssetsView.vue'),
    meta: {
      title: '我的资产 - SoundVibe',
      requiresAuth: true
    }
  },
  {
    path: '/publish',
    name: 'Publish',
    component: () => import('@/views/PublishView.vue'),
    meta: {
      title: '发布作品 - SoundVibe',
      requiresAuth: true
    }
  },
  {
    path: '/generate',
    name: 'Generate',
    component: () => import('@/views/GenerateView.vue'),
    meta: {
      title: 'AI 音乐生成 - SoundVibe',
      requiresAuth: true
    }
  },
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('@/views/CartView.vue'),
    meta: {
      title: '购物车 - SoundVibe',
      requiresAuth: true
    }
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('@/views/OrdersView.vue'),
    meta: {
      title: '我的订单 - SoundVibe',
      requiresAuth: true
    }
  },
  // 保留 /catalog 路径的兼容性重定向
  {
    path: '/catalog',
    redirect: '/'
  }
]

/**
 * 创建路由实例
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

/**
 * 全局前置守卫
 * 处理权限验证、页面标题等
 */
router.beforeEach((to, _from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title as string
  }

  // 检查是否需要登录
  const requiresAuth = to.meta.requiresAuth as boolean
  const token = localStorage.getItem('soundvibe-token')

  if (requiresAuth && !token) {
    // 需要登录但未登录，跳转到登录页
    next('/login')
  } else if (!requiresAuth && token && (to.path === '/login' || to.path === '/register')) {
    // 已登录但访问登录/注册页，跳转到主页
    next('/')
  } else {
    // 其他情况正常放行
    next()
  }
})

export default router

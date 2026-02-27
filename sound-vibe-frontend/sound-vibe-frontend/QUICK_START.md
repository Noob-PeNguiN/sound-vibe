# 🚀 SoundVibe Frontend - Quick Start Guide

## 📦 安装依赖（如果还没有安装）

```bash
cd /Users/penguin/cursor_projects/SoundVibe-Project/sound-vibe-frontend/sound-vibe-frontend
npm install
```

## 🎯 Phase 1 已完成功能

✅ **完整的登录流程**

- API 层（Axios + 拦截器）
- Pinia Store（用户状态管理）
- 登录页面（带表单验证、加载状态、错误提示）
- 路由配置（Vue Router）
- TypeScript 类型安全

## 🏃 启动开发服务器

```bash
npm run dev
```

浏览器会自动打开 `http://localhost:5173/`（或显示的端口）

## 🔍 测试登录功能

### 前提条件

**后端必须已启动**：`http://localhost:8080`

### 测试步骤

1. 打开浏览器开发者工具（F12）
2. 进入 Console 标签页
3. 在登录页面输入：
   - **用户名**：（后端测试账号）
   - **密码**：（后端测试密码）
4. 点击"登录"按钮

### 预期结果

#### ✅ 登录成功

- 按钮显示"登录中..."并禁用
- Console 输出：`Login Success: { userId, username, email }`
- localStorage 中保存 `soundvibe-token`
- 可以在 Application → Local Storage 中查看

#### ❌ 登录失败（密码错误）

- 页面顶部显示红色错误提示框
- 错误信息：后端返回的具体错误

#### ⚠️ 网络错误（后端未启动）

- 页面顶部显示红色错误提示框
- 错误信息：`网络错误`

## 🧪 调试技巧

### 1. 查看 API 请求

打开开发者工具 → Network 标签页，筛选 XHR：

- **请求 URL**：`http://localhost:8080/api/auth/login`
- **请求方法**：POST
- **请求体**：`{ "username": "...", "password": "..." }`
- **响应**：查看后端返回的数据结构

### 2. 查看 Pinia State

安装 Vue DevTools 浏览器插件：

- 打开 DevTools → Vue 标签页
- 点击左侧 "Pinia" 图标
- 查看 `user` store 的状态

### 3. 手动测试 Token 持久化

```javascript
// 在浏览器 Console 中执行

// 查看当前 token
localStorage.getItem('soundvibe-token')

// 清除 token（模拟登出）
localStorage.removeItem('soundvibe-token')

// 刷新页面后，token 应该被恢复（如果之前登录过）
```

## 📂 项目结构说明

```
src/
├── api/                     # 🌐 API 层
│   ├── request.ts          # Axios 实例 + 拦截器
│   └── auth.ts             # 认证 API
├── stores/                 # 📦 状态管理
│   └── user.ts             # 用户 Store
├── views/                  # 📄 页面组件
│   └── auth/LoginView.vue  # 登录页面
├── router/                 # 🗺️ 路由
│   └── index.ts            # 路由配置
├── types/                  # 📝 类型定义
│   └── api.ts              # API 类型
├── App.vue                 # 🏠 根组件
└── main.ts                 # 🚀 应用入口
```

## 🔧 修改 API 地址

编辑 `.env.development`：

```env
VITE_API_BASE_URL=http://your-backend-url:port/api
```

**重要**：修改后需要重启开发服务器（Ctrl+C 后重新运行 `npm run dev`）

## ⚡ 常见问题

### Q1: 页面刷新后 token 丢失？

**A**: 检查 `localStorage.getItem('soundvibe-token')` 是否存在。Store 初始化时会自动读取。

### Q2: API 请求提示 CORS 错误？

**A**: 后端需要配置 CORS 允许前端域名（`http://localhost:5173`）。

### Q3: 路径别名 `@` 无法解析？

**A**: 已配置在 `vite.config.ts` 和 `tsconfig.app.json` 中，重启 IDE 即可。

### Q4: 登录成功但页面没跳转？

**A**: 当前版本只是打印 Console，未实现跳转。后续 Phase 2 会添加 Dashboard。

## 📚 下一步开发

请查看 `IMPLEMENTATION_PHASE1.md` 了解详细技术实现。

**Phase 2** 计划：

- [ ] 用户注册页面
- [ ] 忘记密码流程
- [ ] 路由守卫（未登录拦截）
- [ ] Dashboard 主页
- [ ] 用户个人资料页面

---

**开发日期**: 2026-02-02  
**技术栈**: Vue 3 + TypeScript + Pinia + Vue Router + Tailwind CSS

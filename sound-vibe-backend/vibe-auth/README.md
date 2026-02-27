# 🎵 SoundVibe Auth Service

> SoundVibe 微服务架构 - 用户认证模块

## ✨ 功能特性

- ✅ **用户注册**: 用户名查重 + BCrypt 密码加密
- ✅ **用户登录**: 密码校验 + JWT Token 生成
- ✅ **参数校验**: Jakarta Validation 自动校验
- ✅ **异常处理**: 全局统一异常拦截
- ✅ **安全防护**: SQL 注入防护、密码加密存储
- ✅ **自动填充**: MyBatis-Plus 时间戳自动填充

## 🚀 快速开始

### 1. 启动依赖服务

```bash
# 进入 docker 目录
cd /Users/penguin/cursor_projects/SoundVibe-Project/sound-vibe-backend/docker

# 启动 MySQL、Redis、Nacos
docker-compose up -d mysql redis nacos
```

### 2. 初始化数据库

```bash
# 连接 MySQL
mysql -h 127.0.0.1 -P 3306 -u root -proot123

# 创建数据库
CREATE DATABASE IF NOT EXISTS sound_vibe_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行初始化脚本
SOURCE src/main/resources/sql/schema.sql;

# 退出 MySQL
exit
```

### 3. 启动服务

```bash
# 方式 1: 使用 Maven (开发环境)
mvn clean spring-boot:run

# 方式 2: 打包后运行 (生产环境)
mvn clean package
java -jar target/vibe-auth-1.0.0-SNAPSHOT.jar
```

### 4. 验证服务

```bash
# 健康检查 (预期: 服务正常运行)
curl http://localhost:8081/auth/register

# 查看 Nacos 注册状态
# 访问: http://localhost:8848/nacos (账号: nacos / nacos)
# 应该能看到 vibe-auth 服务已注册
```

## 📡 API 接口

### 1. 用户注册

**接口**: `POST /auth/register`

**请求体**:
```json
{
  "username": "producer01",
  "password": "pass123456"
}
```

**成功响应**:
```json
{
  "code": 200,
  "message": "成功",
  "data": null
}
```

**测试命令**:
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"producer01","password":"pass123456"}'
```

---

### 2. 用户登录

**接口**: `POST /auth/login`

**请求体**:
```json
{
  "username": "producer01",
  "password": "pass123456"
}
```

**成功响应**:
```json
{
  "code": 200,
  "message": "成功",
  "data": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
}
```

**测试命令**:
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"producer01","password":"pass123456"}'
```

## 📂 项目结构

```
vibe-auth/
├── config/              # 配置类
│   └── MyBatisPlusConfig.java
├── controller/          # 控制器层
│   └── AuthController.java
├── domain/              # 领域模型
│   └── entity/
│       └── User.java
├── enums/               # 枚举定义
│   └── UserRole.java
├── exception/           # 异常处理
│   └── GlobalExceptionHandler.java
├── mapper/              # 数据访问层
│   └── UserMapper.java
├── model/               # 数据传输对象
│   └── dto/
│       └── AuthDTO.java
├── service/             # 业务逻辑层
│   ├── UserService.java
│   └── impl/
│       └── UserServiceImpl.java
└── util/                # 工具类
    └── JwtUtil.java
```

## 🔐 安全说明

### 密码加密
- 使用 **BCrypt** 算法（Salt + Hash）
- 密码不可逆加密，即使数据库泄露也无法还原明文
- 每个密码的 Salt 都是唯一的

### JWT Token
- 密钥: `soundvibe-secret-2026` (生产环境请修改)
- 有效期: 7 天
- Payload 包含: `id`, `role`, `iat`, `exp`

### SQL 注入防护
- 严格使用 MyBatis-Plus `LambdaQueryWrapper`
- 禁止字符串拼接 SQL

## 🛠️ 技术栈

| 技术               | 版本    | 说明                  |
|--------------------|---------|----------------------|
| Spring Boot        | 3.2.0   | 应用框架              |
| Spring Cloud       | 2023.0  | 微服务框架            |
| Nacos              | 2.3.0   | 服务注册与配置中心    |
| MyBatis-Plus       | 3.5.5   | ORM 增强工具          |
| MySQL              | 8.0     | 关系型数据库          |
| Lombok             | 1.18.30 | 样板代码生成          |
| Hutool             | 5.8.25  | Java 工具库           |
| Jakarta Validation | 3.0.2   | 参数校验              |

## 📖 相关文档

- [API 测试文档](./API_TEST.md) - 详细的接口测试用例
- [架构设计文档](./ARCHITECTURE.md) - 完整的架构设计说明
- [数据库设计](../.cursor/schema_design.md) - 数据库表结构定义

## 🐛 常见问题

### Q1: 连接数据库失败

**问题**: `Communications link failure`

**解决**:
```bash
# 检查 MySQL 容器是否运行
docker ps | grep mysql

# 重启 MySQL 容器
docker-compose restart mysql
```

---

### Q2: Nacos 注册失败

**问题**: `failed to req API: /nacos/v1/ns/instance`

**解决**:
```bash
# 检查 Nacos 容器是否运行
docker ps | grep nacos

# 查看 Nacos 日志
docker logs nacos
```

---

### Q3: 参数校验不生效

**问题**: 传入空参数时没有返回 400 错误

**解决**:
- 确保 DTO 上有 `@NotBlank`, `@Size` 注解
- 确保 Controller 方法参数有 `@Validated` 注解
- 确保 pom.xml 中引入了 `spring-boot-starter-validation`

---

### Q4: JWT Token 解析失败

**问题**: `JWTUtil.verify()` 返回 false

**解决**:
- 检查密钥是否一致 (生成和验证时必须使用同一密钥)
- 检查 Token 是否已过期
- 使用 [jwt.io](https://jwt.io/) 解析 Token 查看 Payload

## 📊 性能指标

### 接口响应时间
- 注册接口: ~100ms (包含 BCrypt 加密)
- 登录接口: ~80ms (包含 BCrypt 校验 + JWT 生成)

### 数据库查询
- 用户名查询: ~5ms (使用索引)
- 用户插入: ~10ms

## 🔮 后续计划

- [ ] 添加验证码功能
- [ ] 实现登录限流
- [ ] Token 刷新机制
- [ ] 第三方登录 (微信、GitHub)
- [ ] 登录日志记录

## 📞 联系我们

- **项目**: SoundVibe
- **团队**: SoundVibe Team
- **邮箱**: dev@soundvibe.com

## 📄 许可证

Copyright © 2026 SoundVibe Team. All rights reserved.

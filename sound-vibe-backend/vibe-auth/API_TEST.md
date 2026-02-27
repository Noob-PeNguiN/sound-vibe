# SoundVibe Auth API 测试文档

## 环境配置
- **服务端口**: 8081
- **Base URL**: `http://localhost:8081`
- **数据库**: `sound_vibe_db`

## 前置准备

### 1. 启动依赖服务
```bash
cd docker
docker-compose up -d mysql redis nacos
```

### 2. 初始化数据库
```bash
# 连接 MySQL
mysql -h 127.0.0.1 -P 3306 -u root -proot123

# 创建数据库
CREATE DATABASE IF NOT EXISTS sound_vibe_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行初始化脚本
SOURCE vibe-auth/src/main/resources/sql/schema.sql;
```

### 3. 启动服务
```bash
cd vibe-auth
mvn spring-boot:run
```

---

## API 接口测试

### 1. 用户注册

**接口**: `POST /auth/register`

**请求示例** (curl):
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "producer01",
    "password": "pass123456"
  }'
```

**请求示例** (HTTPie):
```bash
http POST http://localhost:8081/auth/register \
  username=producer01 \
  password=pass123456
```

**成功响应** (200):
```json
{
  "code": 200,
  "message": "成功",
  "data": null
}
```

**失败响应 - 用户名已存在** (601):
```json
{
  "code": 601,
  "message": "用户名已存在",
  "data": null
}
```

**失败响应 - 参数校验失败** (400):
```json
{
  "code": 400,
  "message": "用户名不能为空; 密码长度必须在 6-50 个字符之间",
  "data": null
}
```

---

### 2. 用户登录

**接口**: `POST /auth/login`

**请求示例** (curl):
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "producer01",
    "password": "pass123456"
  }'
```

**请求示例** (HTTPie):
```bash
http POST http://localhost:8081/auth/login \
  username=producer01 \
  password=pass123456
```

**成功响应** (200):
```json
{
  "code": 200,
  "message": "成功",
  "data": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
}
```

**失败响应 - 用户不存在** (602):
```json
{
  "code": 602,
  "message": "用户不存在",
  "data": null
}
```

**失败响应 - 密码错误** (603):
```json
{
  "code": 603,
  "message": "密码错误",
  "data": null
}
```

---

## 测试用例

### 正常流程测试

#### 1. 注册新用户
```bash
# Step 1: 注册用户 producer02
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "producer02",
    "password": "mypassword123"
  }'

# 预期结果: 返回 code=200
```

#### 2. 登录获取 Token
```bash
# Step 2: 登录用户 producer02
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "producer02",
    "password": "mypassword123"
  }'

# 预期结果: 返回 code=200 和 JWT Token
# 保存 Token 用于后续认证
```

---

### 异常流程测试

#### 1. 重复注册
```bash
# 尝试注册已存在的用户
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "producer01",
    "password": "anypassword"
  }'

# 预期结果: code=601, message="用户名已存在"
```

#### 2. 用户名过短
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "ab",
    "password": "pass123456"
  }'

# 预期结果: code=400, message="用户名长度必须在 3-20 个字符之间"
```

#### 3. 密码过短
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "producer03",
    "password": "123"
  }'

# 预期结果: code=400, message="密码长度必须在 6-50 个字符之间"
```

#### 4. 登录不存在的用户
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "nonexistent",
    "password": "anypassword"
  }'

# 预期结果: code=602, message="用户不存在"
```

#### 5. 密码错误
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "producer01",
    "password": "wrongpassword"
  }'

# 预期结果: code=603, message="密码错误"
```

---

## 数据库验证

### 查询用户表
```sql
USE sound_vibe_db;

-- 查看所有用户
SELECT id, username, role, create_time, update_time 
FROM users;

-- 查看特定用户
SELECT * FROM users WHERE username = 'producer01';
```

### 验证密码哈希
```sql
-- password_hash 字段应该是 BCrypt 格式 ($2a$10$...)
SELECT username, password_hash FROM users;
```

---

## JWT Token 解析

使用 [jwt.io](https://jwt.io/) 解析返回的 Token，可以看到 Payload 包含:
```json
{
  "id": 1,
  "role": "PRODUCER",
  "iat": 1706515200000,
  "exp": 1707120000000
}
```

---

## 常见问题排查

### 1. 连接数据库失败
```bash
# 检查 MySQL 容器是否运行
docker ps | grep mysql

# 检查数据库连接
mysql -h 127.0.0.1 -P 3306 -u root -proot123
```

### 2. Nacos 注册失败
```bash
# 检查 Nacos 容器是否运行
docker ps | grep nacos

# 访问 Nacos 控制台
# http://localhost:8848/nacos (账号: nacos / nacos)
```

### 3. 查看服务日志
```bash
# 查看 Spring Boot 启动日志
# 检查是否有 MyBatis 映射扫描日志
# 检查是否有数据库连接成功日志
```

---

## 性能指标

### 接口响应时间基准
- **注册接口**: < 100ms (首次 BCrypt 加密可能需要 200ms)
- **登录接口**: < 80ms (BCrypt 校验 + JWT 生成)

### 并发测试 (可选)
```bash
# 使用 Apache Bench 测试
ab -n 1000 -c 10 -p register.json -T application/json \
  http://localhost:8081/auth/register
```

---

## 安全检查清单

- [x] 密码使用 BCrypt 加密存储
- [x] 输入参数使用 @Validated 校验
- [x] SQL 查询使用 LambdaQueryWrapper (防止注入)
- [x] 异常信息不暴露敏感数据
- [x] JWT 密钥独立配置
- [x] 密码传输通过 HTTPS (生产环境)

---

## 后续开发建议

1. **添加验证码**: 防止暴力破解
2. **登录限流**: 使用 Redis 记录失败次数
3. **刷新 Token**: 实现 Access Token + Refresh Token 机制
4. **登录日志**: 记录登录 IP、时间、设备信息
5. **多端登录**: 支持 Web/iOS/Android 不同设备

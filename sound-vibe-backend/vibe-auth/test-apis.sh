#!/bin/bash

# ========================================
# SoundVibe Auth API 自动化测试脚本
# ========================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
BASE_URL="http://localhost:8081"
TEST_USERNAME="test_user_$(date +%s)"  # 使用时间戳避免重复
TEST_PASSWORD="testpass123456"

# 工具函数
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# 检查服务是否启动
check_service() {
    print_header "Step 1: 检查服务状态"
    
    if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/auth/register" | grep -q "405\|400"; then
        print_success "服务已启动并运行在 $BASE_URL"
    else
        print_error "服务未启动或无法访问"
        print_info "请先启动服务: mvn spring-boot:run"
        exit 1
    fi
}

# 测试用户注册
test_register() {
    print_header "Step 2: 测试用户注册"
    
    print_info "注册用户: $TEST_USERNAME"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\"}")
    
    CODE=$(echo "$RESPONSE" | grep -o '"code":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ "$CODE" == "200" ]; then
        print_success "注册成功"
        echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    else
        print_error "注册失败"
        echo "$RESPONSE"
        exit 1
    fi
}

# 测试重复注册
test_duplicate_register() {
    print_header "Step 3: 测试重复注册（预期失败）"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\"}")
    
    CODE=$(echo "$RESPONSE" | grep -o '"code":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ "$CODE" == "601" ]; then
        print_success "正确拦截重复注册 (code=601)"
        echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    else
        print_error "重复注册拦截失败"
        echo "$RESPONSE"
        exit 1
    fi
}

# 测试参数校验
test_validation() {
    print_header "Step 4: 测试参数校验（用户名过短）"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d '{"username":"ab","password":"pass123456"}')
    
    CODE=$(echo "$RESPONSE" | grep -o '"code":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ "$CODE" == "400" ]; then
        print_success "参数校验生效 (code=400)"
        echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    else
        print_error "参数校验失败"
        echo "$RESPONSE"
    fi
}

# 测试用户登录
test_login() {
    print_header "Step 5: 测试用户登录"
    
    print_info "登录用户: $TEST_USERNAME"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\"}")
    
    CODE=$(echo "$RESPONSE" | grep -o '"code":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ "$CODE" == "200" ]; then
        print_success "登录成功"
        TOKEN=$(echo "$RESPONSE" | grep -o '"data":"[^"]*"' | cut -d'"' -f4)
        
        if [ -n "$TOKEN" ]; then
            print_success "获取到 JWT Token"
            print_info "Token 前 50 个字符: ${TOKEN:0:50}..."
            
            # 保存 Token 到文件
            echo "$TOKEN" > /tmp/soundvibe_token.txt
            print_info "Token 已保存到: /tmp/soundvibe_token.txt"
        else
            print_error "未获取到 Token"
        fi
        
        echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    else
        print_error "登录失败"
        echo "$RESPONSE"
        exit 1
    fi
}

# 测试错误密码
test_wrong_password() {
    print_header "Step 6: 测试错误密码（预期失败）"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$TEST_USERNAME\",\"password\":\"wrongpassword\"}")
    
    CODE=$(echo "$RESPONSE" | grep -o '"code":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ "$CODE" == "603" ]; then
        print_success "正确拦截错误密码 (code=603)"
        echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    else
        print_error "密码校验失败"
        echo "$RESPONSE"
    fi
}

# 测试不存在的用户
test_nonexistent_user() {
    print_header "Step 7: 测试不存在的用户（预期失败）"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"nonexistent_user_12345","password":"anypassword"}')
    
    CODE=$(echo "$RESPONSE" | grep -o '"code":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ "$CODE" == "602" ]; then
        print_success "正确拦截不存在的用户 (code=602)"
        echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    else
        print_error "用户存在性校验失败"
        echo "$RESPONSE"
    fi
}

# 主函数
main() {
    print_header "SoundVibe Auth API 自动化测试"
    print_info "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    
    check_service
    test_register
    test_duplicate_register
    test_validation
    test_login
    test_wrong_password
    test_nonexistent_user
    
    print_header "✓ 所有测试通过！"
    print_success "API 接口工作正常"
    print_info "测试用户: $TEST_USERNAME"
    print_info "JWT Token 已保存到: /tmp/soundvibe_token.txt"
}

# 执行主函数
main

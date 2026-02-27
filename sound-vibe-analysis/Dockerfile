# ============================================
# SoundVibe Audio Analysis Service
# 基础镜像: Python 3.10-slim
# ============================================
FROM python:3.10-slim

# 设置工作目录
WORKDIR /app

# 安装系统依赖
# - ffmpeg: librosa 音频解码必需
# - libsndfile1: soundfile 库依赖
# 安装后清理 apt 缓存，减小镜像体积
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ffmpeg \
        libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# 先安装 Python 依赖（利用 Docker 层缓存）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制源代码
COPY src/ src/
COPY .env .env

# 暴露健康检查端口
EXPOSE 8090

# 启动命令
CMD ["python", "-m", "src.main"]

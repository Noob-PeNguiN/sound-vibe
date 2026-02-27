"""
SoundVibe CLAP Model Manager - 音频/文本嵌入向量生成
使用 HuggingFace CLAP (laion/clap-htsat-unfused) 模型生成 512 维嵌入向量

架构说明:
  - 单例模式: 模型仅在首次调用 get_manager() 时加载一次
  - 设备优先级: MPS (Apple Silicon) > CUDA > CPU
  - 向量维度: 512 维 (CLAP HTSAT-unfused 默认输出)
"""

from __future__ import annotations

import logging
import threading
from typing import Optional

import librosa
import numpy as np
import torch
from transformers import ClapModel, ClapProcessor

logger = logging.getLogger(__name__)

EMBEDDING_DIM = 512
_MODEL_ID = "laion/clap-htsat-unfused"
_SAMPLE_RATE = 48000  # CLAP 模型要求 48kHz 输入

_instance: Optional[CLAPModelManager] = None
_lock = threading.Lock()


def _select_device() -> torch.device:
    if torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


class CLAPModelManager:
    """
    CLAP 模型单例管理器

    线程安全：通过模块级锁保证只初始化一次
    """

    def __init__(self) -> None:
        self.device = _select_device()
        logger.info("正在加载 CLAP 模型: %s (device=%s) ...", _MODEL_ID, self.device)

        self.processor: ClapProcessor = ClapProcessor.from_pretrained(_MODEL_ID)
        self.model: ClapModel = ClapModel.from_pretrained(_MODEL_ID)
        self.model.to(self.device)
        self.model.eval()

        logger.info(
            "CLAP 模型加载完成 (embedding_dim=%d, device=%s)",
            EMBEDDING_DIM, self.device,
        )

    @torch.no_grad()
    def get_audio_embedding(self, file_path: str) -> list[float]:
        """
        从音频文件生成 512 维嵌入向量

        :param file_path: 本地音频文件路径
        :return: 512 维浮点数列表
        """
        waveform, _ = librosa.load(file_path, sr=_SAMPLE_RATE, mono=True)

        inputs = self.processor(
            audio=waveform,
            sampling_rate=_SAMPLE_RATE,
            return_tensors="pt",
        )
        inputs = {k: v.to(self.device) for k, v in inputs.items()}

        embeddings = self.model.get_audio_features(**inputs)
        if hasattr(embeddings, 'audio_embeds'):
            embeddings = embeddings.audio_embeds
        elif hasattr(embeddings, 'pooler_output'):
            embeddings = embeddings.pooler_output
        vector = embeddings.squeeze().cpu().numpy().tolist()

        logger.debug("音频嵌入生成完成: dim=%d, file=%s", len(vector), file_path)
        return vector

    @torch.no_grad()
    def get_text_embedding(self, text: str) -> list[float]:
        """
        从文本描述生成 512 维嵌入向量

        :param text: 文本描述 (如 "cinematic dark synthwave")
        :return: 512 维浮点数列表
        """
        inputs = self.processor(
            text=[text],
            return_tensors="pt",
            padding=True,
        )
        inputs = {k: v.to(self.device) for k, v in inputs.items()}

        embeddings = self.model.get_text_features(**inputs)
        if hasattr(embeddings, 'text_embeds'):
            embeddings = embeddings.text_embeds
        elif hasattr(embeddings, 'pooler_output'):
            embeddings = embeddings.pooler_output
        vector = embeddings.squeeze().cpu().numpy().tolist()

        logger.debug("文本嵌入生成完成: dim=%d, text='%s'", len(vector), text[:60])
        return vector


def get_manager() -> CLAPModelManager:
    """
    获取 CLAP 模型管理器单例（线程安全）

    首次调用时加载模型，后续调用直接返回缓存实例
    """
    global _instance
    if _instance is None:
        with _lock:
            if _instance is None:
                _instance = CLAPModelManager()
    return _instance

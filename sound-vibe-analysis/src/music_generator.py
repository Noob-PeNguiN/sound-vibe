"""
SoundVibe MusicGen Engine - AI 音乐生成
使用 Meta MusicGen (facebook/musicgen-small) 根据文本描述生成音频片段

架构说明:
  - 单例模式: 模型仅在首次调用 get_generator() 时加载一次
  - 设备优先级: MPS (Apple Silicon) > CUDA > CPU
  - 采样率: 32000 Hz (MusicGen 默认输出)
"""

from __future__ import annotations

import logging
import os
import tempfile
import threading
from typing import Optional

import numpy as np
import scipy.io.wavfile
import torch
from transformers import AutoProcessor, MusicgenForConditionalGeneration

logger = logging.getLogger(__name__)

_MODEL_ID = "facebook/musicgen-small"
_SAMPLE_RATE = 32000

_instance: Optional[MusicGenerator] = None
_lock = threading.Lock()


def _select_device() -> torch.device:
    if torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


class MusicGenerator:
    """
    MusicGen 模型单例管理器

    线程安全：通过模块级锁保证只初始化一次
    """

    def __init__(self) -> None:
        self.device = _select_device()
        logger.info("正在加载 MusicGen 模型: %s (device=%s) ...", _MODEL_ID, self.device)

        self.processor = AutoProcessor.from_pretrained(_MODEL_ID)
        self.model = MusicgenForConditionalGeneration.from_pretrained(_MODEL_ID)
        self.model.to(self.device)
        self.model.eval()

        logger.info("MusicGen 模型加载完成 (sample_rate=%d, device=%s)", _SAMPLE_RATE, self.device)

    @torch.no_grad()
    def generate_audio(self, prompt: str, duration: int = 5) -> str:
        """
        根据文本描述生成音频并保存为临时 .wav 文件

        :param prompt: 文本描述 (如 "A heavy electronic drum loop")
        :param duration: 生成时长（秒），默认 5 秒
        :return: 临时 .wav 文件的绝对路径（由调用方负责清理）
        """
        # MusicGen 的帧率约为 50 tokens/s
        max_new_tokens = duration * 50

        inputs = self.processor(text=[prompt], padding=True, return_tensors="pt")
        inputs = {k: v.to(self.device) for k, v in inputs.items()}

        audio_values = self.model.generate(**inputs, max_new_tokens=max_new_tokens)

        waveform = audio_values[0, 0].cpu().float().numpy()

        # 归一化到 int16 范围以写入 WAV
        waveform = waveform / (np.abs(waveform).max() + 1e-8)
        waveform_int16 = (waveform * 32767).astype(np.int16)

        fd, temp_path = tempfile.mkstemp(suffix=".wav", prefix="musicgen_")
        os.close(fd)

        scipy.io.wavfile.write(temp_path, _SAMPLE_RATE, waveform_int16)
        logger.info(
            "音频生成完成: prompt='%s', duration=%ds, path=%s",
            prompt[:60], duration, temp_path,
        )
        return temp_path


def get_generator() -> MusicGenerator:
    """
    获取 MusicGen 生成器单例（线程安全）

    首次调用时加载模型，后续调用直接返回缓存实例
    """
    global _instance
    if _instance is None:
        with _lock:
            if _instance is None:
                _instance = MusicGenerator()
    return _instance

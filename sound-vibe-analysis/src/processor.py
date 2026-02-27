"""
SoundVibe Audio Intelligence - 音频特征提取引擎
使用 librosa 分析音频文件，提取 BPM、调性、时长等元数据

性能说明:
  - 使用 sr=22050 降采样，平衡分析精度与处理速度
  - 单文件处理完成后立即释放 numpy 数组，避免内存累积
"""

import gc
import logging
from typing import TypedDict

import librosa
import numpy as np

logger = logging.getLogger(__name__)

# 降采样率（22050 Hz 足够用于节拍/调性分析，节省内存和 CPU）
_SAMPLE_RATE = 22050

# 音乐调性映射表
# chroma 索引 -> 音名
_PITCH_CLASSES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]

# 大调模板 (Krumhansl-Schmuckler key profiles)
_MAJOR_PROFILE = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
_MINOR_PROFILE = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])


class AnalysisResult(TypedDict):
    """音频分析结果"""
    bpm: int
    key: str
    duration: int


def analyze_audio(file_path: str) -> AnalysisResult:
    """
    分析音频文件，提取 BPM、调性和时长

    :param file_path: 本地音频文件路径
    :return: 分析结果字典 {'bpm': 128, 'key': 'C Major', 'duration': 225}
    :raises Exception: 文件无法加载或分析失败时抛出
    """
    logger.info("开始分析音频: %s", file_path)

    try:
        # 1. 加载音频（降采样到 22050 Hz）
        y, sr = librosa.load(file_path, sr=_SAMPLE_RATE)

        # 2. 提取时长（秒）
        duration = int(librosa.get_duration(y=y, sr=sr))
        logger.info("  时长: %d 秒", duration)

        # 3. 提取 BPM
        bpm = _extract_bpm(y, sr)
        logger.info("  BPM: %d", bpm)

        # 4. 提取调性
        key = _extract_key(y, sr)
        logger.info("  调性: %s", key)

        result: AnalysisResult = {
            "bpm": bpm,
            "key": key,
            "duration": duration,
        }

        logger.info("音频分析完成: %s -> %s", file_path, result)
        return result

    finally:
        # 释放大型 numpy 数组，防止内存泄漏
        # noinspection PyBroadException
        try:
            del y  # noqa: F821
        except Exception:
            pass
        gc.collect()


def _extract_bpm(y: np.ndarray, sr: int) -> int:
    """
    使用 librosa beat tracking 提取 BPM

    :param y: 音频时域信号
    :param sr: 采样率
    :return: 估计的 BPM（取整）
    """
    tempo, _ = librosa.beat.beat_track(y=y, sr=sr)

    # librosa >= 0.10 返回 ndarray，需要取标量值
    if isinstance(tempo, np.ndarray):
        tempo = float(tempo[0])

    return max(1, int(round(tempo)))


def _extract_key(y: np.ndarray, sr: int) -> str:
    """
    使用 Chroma CQT + Krumhansl-Schmuckler 算法估计调性

    算法流程:
      1. 计算 Chroma CQT 特征矩阵
      2. 取各 chroma bin 的均值，得到 12 维分布向量
      3. 对 12 个调分别计算与大调/小调模板的相关系数
      4. 取最高相关系数对应的调性

    :param y: 音频时域信号
    :param sr: 采样率
    :return: 调性字符串（如 'C Major', 'A Minor'）
    """
    # 计算 Chroma CQT
    chroma = librosa.feature.chroma_cqt(y=y, sr=sr)
    # 均值化 -> 12 维向量
    chroma_mean = np.mean(chroma, axis=1)

    best_corr = -2.0
    best_key = "C Major"

    for shift in range(12):
        # 循环移位 chroma 分布，模拟不同调性
        shifted = np.roll(chroma_mean, -shift)

        # 与大调模板相关
        major_corr = float(np.corrcoef(shifted, _MAJOR_PROFILE)[0, 1])
        if major_corr > best_corr:
            best_corr = major_corr
            best_key = f"{_PITCH_CLASSES[shift]} Major"

        # 与小调模板相关
        minor_corr = float(np.corrcoef(shifted, _MINOR_PROFILE)[0, 1])
        if minor_corr > best_corr:
            best_corr = minor_corr
            best_key = f"{_PITCH_CLASSES[shift]} Minor"

    return best_key

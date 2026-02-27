"""
SoundVibe Zero-Shot Audio Tagger
基于 CLAP 模型的零样本音频标签匹配

原理:
  1. 启动时将所有候选标签通过 CLAP 文本编码器预计算为 512 维向量
  2. 音频处理时，用音频向量与所有标签向量计算余弦相似度
  3. 返回相似度超过阈值的 Top-N 标签

性能:
  - 预计算耗时约 2-5 秒（一次性开销，启动时完成）
  - 每次标签匹配仅需矩阵乘法，< 1ms
"""

from __future__ import annotations

import logging
from typing import Optional

import numpy as np

logger = logging.getLogger(__name__)

CANDIDATE_TAGS: list[str] = [
    # ── 流派 (Genre) ──────────────────────────────────
    "hip hop beat",
    "trap beat",
    "lo-fi hip hop",
    "boom bap",
    "drill beat",
    "R&B",
    "pop",
    "electronic dance music",
    "house music",
    "techno",
    "dubstep",
    "drum and bass",
    "ambient",
    "chillwave",
    "synthwave",
    "vaporwave",
    "jazz",
    "soul music",
    "funk",
    "reggae",
    "rock",
    "indie rock",
    "metal",
    "classical music",
    "cinematic orchestral",
    "country",
    "blues",
    "latin music",
    "afrobeat",
    "k-pop",
    "gospel",
    # ── 氛围 / 情绪 (Mood & Vibe) ─────────────────────
    "dark and moody",
    "upbeat and energetic",
    "melancholic and sad",
    "happy and cheerful",
    "aggressive and intense",
    "calm and relaxing",
    "dreamy and ethereal",
    "epic and cinematic",
    "mysterious and suspenseful",
    "romantic and emotional",
    "nostalgic",
    "futuristic",
    "meditative",
    # ── 乐器 (Instruments) ────────────────────────────
    "piano",
    "acoustic guitar",
    "electric guitar",
    "bass guitar",
    "violin and strings",
    "saxophone",
    "trumpet and brass",
    "flute",
    "drums and percussion",
    "synthesizer",
    "808 bass",
    "organ",
    "harp",
    "ukulele",
    # ── 人声 (Vocals) ─────────────────────────────────
    "male vocals",
    "female vocals",
    "vocal chops",
    "vocal harmony",
    "rap vocals",
    "singing with autotune",
    "acapella",
    # ── 制作风格 (Production Style) ────────────────────
    "heavy bass",
    "distorted and glitchy",
    "clean and minimal",
    "lush pads and textures",
    "punchy drums",
    "fast tempo",
    "slow tempo",
    "reverb heavy and spacious",
    "sample based and chopped",
    "acoustic and unplugged",
    # ── 用途场景 (Use Case) ────────────────────────────
    "background music",
    "workout and gym music",
    "study music",
    "gaming soundtrack",
    "film score",
    "commercial jingle",
    "meditation and yoga",
    "party music",
]

_SIMILARITY_THRESHOLD = 0.1
_TOP_N = 5

_tag_vectors: Optional[np.ndarray] = None


def precompute_tag_embeddings() -> None:
    """
    启动时预计算所有候选标签的文本嵌入向量

    结果缓存在模块级变量 _tag_vectors 中，形状为 (N, 512)，已 L2 归一化。
    """
    global _tag_vectors

    from src.model_manager import get_manager

    manager = get_manager()
    logger.info("开始预计算 %d 个候选标签的文本嵌入...", len(CANDIDATE_TAGS))

    vectors = []
    for tag in CANDIDATE_TAGS:
        vec = manager.get_text_embedding(tag)
        vectors.append(vec)

    mat = np.array(vectors, dtype=np.float32)
    norms = np.linalg.norm(mat, axis=1, keepdims=True)
    norms = np.where(norms == 0, 1, norms)
    _tag_vectors = mat / norms

    logger.info(
        "标签嵌入预计算完成: %d 个标签, 矩阵形状=%s",
        len(CANDIDATE_TAGS), _tag_vectors.shape,
    )


def match_tags(audio_vector: list[float]) -> Optional[str]:
    """
    将音频嵌入与所有候选标签做余弦相似度匹配

    :param audio_vector: 512 维音频嵌入向量
    :return: 逗号分隔的标签字符串，若无匹配则返回 None
    """
    if _tag_vectors is None:
        logger.warning("标签向量尚未初始化，跳过自动标注")
        return None

    audio_vec = np.array(audio_vector, dtype=np.float32)
    norm = np.linalg.norm(audio_vec)
    if norm == 0:
        logger.warning("音频向量范数为零，跳过自动标注")
        return None
    audio_vec = audio_vec / norm

    similarities = _tag_vectors @ audio_vec

    top_indices = np.argsort(similarities)[::-1]
    top_scores = [(CANDIDATE_TAGS[i], float(similarities[i])) for i in top_indices[:10]]
    logger.info(
        "相似度 Top10: %s",
        " | ".join(f"{tag}({score:.3f})" for tag, score in top_scores),
    )

    matched = [
        (tag, score) for tag, score in top_scores[:_TOP_N]
        if score >= _SIMILARITY_THRESHOLD
    ]

    if not matched:
        logger.info(
            "没有标签超过阈值 %.2f (最高: %.3f), 回退取 Top3",
            _SIMILARITY_THRESHOLD,
            top_scores[0][1] if top_scores else 0,
        )
        matched = top_scores[:3]

    tag_str = ",".join(tag for tag, _ in matched)
    logger.info(
        "自动标注结果: %s",
        " | ".join(f"{tag}({score:.3f})" for tag, score in matched),
    )
    return tag_str

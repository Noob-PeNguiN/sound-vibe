package com.soundvibe.search.client.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 文本嵌入响应 DTO
 * vibe-analysis Python 服务返回的 CLAP 文本向量（512 维）
 */
public record TextEmbedResponse(List<Float> vector) implements Serializable {
}

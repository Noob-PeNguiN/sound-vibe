package com.soundvibe.search.client.dto;

import java.io.Serializable;

/**
 * 文本嵌入请求 DTO
 * 发送给 vibe-analysis Python 服务，将文本转换为 CLAP 向量
 */
public record TextEmbedRequest(String text) implements Serializable {
}

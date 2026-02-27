package com.soundvibe.search.client;

import com.soundvibe.search.client.dto.TextEmbedRequest;
import com.soundvibe.search.client.dto.TextEmbedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * vibe-analysis Python 服务 Feign 客户端
 * 调用 FastAPI 暴露的文本嵌入接口，将搜索文本转换为 CLAP 向量
 */
@FeignClient(name = "analysis-service", url = "${analysis.service.url}")
public interface AnalysisClient {

    /**
     * 将文本转换为 CLAP 嵌入向量（512 维）
     *
     * @param request 包含待嵌入的文本
     * @return 包含 float 向量的响应
     */
    @PostMapping("/api/embed/text")
    TextEmbedResponse embedText(@RequestBody TextEmbedRequest request);
}

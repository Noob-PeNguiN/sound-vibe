package com.soundvibe.search.service;

import com.soundvibe.search.document.TrackDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * 作品搜索服务接口
 * 基于 Elasticsearch 提供全文搜索、结构化过滤和语义搜索能力
 *
 * @author SoundVibe Team
 */
public interface TrackSearchService {

    /**
     * 搜索作品
     *
     * @param keyword    关键词（匹配 title 或 tags）
     * @param minBpm     最小 BPM（可选，范围下界）
     * @param maxBpm     最大 BPM（可选，范围上界）
     * @param genre      音乐风格（可选，精确匹配 genres 数组中的某个值）
     * @param musicalKey 音乐调式（可选，精确匹配，如 "C Major"）
     * @param pageable   分页参数
     * @return 分页搜索结果
     */
    Page<TrackDoc> search(String keyword, Integer minBpm, Integer maxBpm, String genre, String musicalKey, Pageable pageable);

    /**
     * 语义搜索（基于 CLAP 向量的 kNN 近邻搜索）
     * 将用户文本通过 Python 分析服务转换为向量，再用 ES kNN 查找最相似的音频作品
     *
     * @param queryText 用户的自然语言搜索文本（如 "dark trap beat with 808"）
     * @param k         返回的最近邻数量
     * @return 按相似度排序的作品列表
     */
    List<TrackDoc> semanticSearch(String queryText, int k);

    /**
     * 获取当前可用的筛选选项（动态聚合）
     * 支持级联过滤：选了某个 key 后只返回含该 key 的 genres，反之亦然
     *
     * @param musicalKey 当前已选的调式（可选）
     * @param genre      当前已选的风格（可选）
     * @return { "musicalKeys": [...], "genres": [...] }
     */
    Map<String, Object> getAvailableFilters(String musicalKey, String genre);
}

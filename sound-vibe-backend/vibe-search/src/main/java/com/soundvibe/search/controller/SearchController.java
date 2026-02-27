package com.soundvibe.search.controller;

import com.soundvibe.common.result.Result;
import com.soundvibe.search.document.TrackDoc;
import com.soundvibe.search.service.TrackSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 搜索控制器
 * 提供作品元数据搜索 API（关键词、BPM 范围、风格过滤）
 * <p>
 * 接口设计：
 * - GET /search/tracks?keyword=xxx&genre=Trap&minBpm=120&maxBpm=160&page=0&size=20
 * - 所有参数均可选，无条件时返回最新作品列表
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final TrackSearchService trackSearchService;

    /**
     * 搜索作品
     *
     * @param keyword    关键词（匹配标题和标签）
     * @param genre      音乐风格（精确匹配）
     * @param minBpm     最小 BPM（范围下界）
     * @param maxBpm     最大 BPM（范围上界）
     * @param musicalKey 音乐调式（精确匹配，如 "C minor"）
     * @param page       页码（从 0 开始）
     * @param size       每页大小
     * @return 分页搜索结果
     */
    @GetMapping("/tracks")
    public Result<Page<TrackDoc>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "minBpm", required = false) Integer minBpm,
            @RequestParam(value = "maxBpm", required = false) Integer maxBpm,
            @RequestParam(value = "musicalKey", required = false) String musicalKey,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        log.info("搜索作品: keyword={}, genre={}, bpm=[{}-{}], musicalKey={}, page={}, size={}",
                keyword, genre, minBpm, maxBpm, musicalKey, page, size);

        var pageable = PageRequest.of(page, size);
        var result = trackSearchService.search(keyword, minBpm, maxBpm, genre, musicalKey, pageable);
        return Result.success(result);
    }

    /**
     * 获取当前可用的筛选选项（动态聚合，支持级联）
     * 选了某个调式 → 只返回拥有该调式的作品中出现的风格
     * 选了某个风格 → 只返回拥有该风格的作品中出现的调式
     */
    @GetMapping("/filters")
    public Result<Map<String, Object>> getFilters(
            @RequestParam(value = "musicalKey", required = false) String musicalKey,
            @RequestParam(value = "genre", required = false) String genre) {

        log.info("获取筛选选项: musicalKey={}, genre={}", musicalKey, genre);
        var filters = trackSearchService.getAvailableFilters(musicalKey, genre);
        return Result.success(filters);
    }

    /**
     * 语义搜索（基于 CLAP 音频向量的 kNN 近邻搜索）
     * 用户输入自然语言描述（如 "dark trap beat with 808"），
     * 系统将文本转换为向量并在 ES 中执行 kNN 搜索，返回音频特征最相似的作品
     *
     * @param q 搜索文本（自然语言描述）
     * @param k 返回结果数量（默认 10）
     * @return 按相似度排序的作品列表
     */
    @GetMapping("/semantic")
    public Result<List<TrackDoc>> semanticSearch(
            @RequestParam("q") String q,
            @RequestParam(value = "k", defaultValue = "10") int k) {

        log.info("语义搜索: q='{}', k={}", q, k);
        var results = trackSearchService.semanticSearch(q, k);
        return Result.success(results);
    }
}

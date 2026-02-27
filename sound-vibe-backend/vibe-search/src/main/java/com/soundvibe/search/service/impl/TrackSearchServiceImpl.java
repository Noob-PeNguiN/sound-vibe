package com.soundvibe.search.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.soundvibe.search.client.AnalysisClient;
import com.soundvibe.search.client.dto.TextEmbedRequest;
import com.soundvibe.search.document.TrackDoc;
import com.soundvibe.search.service.TrackSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.json.JsonData;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 作品搜索服务实现
 * 基于 Spring Data Elasticsearch 5.x + NativeQuery 构建复合查询
 * 支持关键词搜索、结构化过滤和 CLAP 向量语义搜索（kNN）
 *
 * @author SoundVibe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackSearchServiceImpl implements TrackSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final AnalysisClient analysisClient;

    private static final int STATUS_PUBLISHED = 1;
    private static final String INDEX_NAME = "tracks";

    @Override
    public Page<TrackDoc> search(String keyword, Integer minBpm, Integer maxBpm, String genre, String musicalKey, Pageable pageable) {

        var boolQueryBuilder = new BoolQuery.Builder();

        boolQueryBuilder.filter(f -> f.term(t -> t.field("status").value(STATUS_PUBLISHED)));

        if (StrUtil.isNotBlank(keyword)) {
            boolQueryBuilder.must(m -> m.multiMatch(mm -> mm
                    .fields("title", "tags")
                    .query(keyword)
            ));
        }

        if (StrUtil.isNotBlank(genre)) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("genres").value(genre)));
        }

        if (StrUtil.isNotBlank(musicalKey)) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("musicalKeys").value(musicalKey)));
        }

        if (minBpm != null || maxBpm != null) {
            boolQueryBuilder.filter(f -> f.range(r -> {
                var rangeQuery = r.field("bpmValues");
                if (minBpm != null) {
                    rangeQuery.gte(JsonData.of(minBpm));
                }
                if (maxBpm != null) {
                    rangeQuery.lte(JsonData.of(maxBpm));
                }
                return rangeQuery;
            }));
        }

        var query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQueryBuilder.build()))
                .withSort(s -> s.field(fs -> fs.field("id").order(SortOrder.Desc)))
                .withPageable(pageable)
                .build();

        log.debug("ES 搜索查询: keyword={}, genre={}, musicalKey={}, bpm=[{}-{}], page={}",
                keyword, genre, musicalKey, minBpm, maxBpm, pageable);

        SearchHits<TrackDoc> searchHits = elasticsearchOperations.search(query, TrackDoc.class);
        SearchPage<TrackDoc> searchPage = SearchHitSupport.searchPageFor(searchHits, pageable);
        return (Page<TrackDoc>) SearchHitSupport.unwrapSearchHits(searchPage);
    }

    @Override
    public Map<String, Object> getAvailableFilters(String musicalKey, String genre) {

        var boolQueryBuilder = new BoolQuery.Builder();
        boolQueryBuilder.filter(f -> f.term(t -> t.field("status").value(STATUS_PUBLISHED)));

        if (StrUtil.isNotBlank(musicalKey)) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("musicalKeys").value(musicalKey)));
        }
        if (StrUtil.isNotBlank(genre)) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("genres").value(genre)));
        }

        var query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQueryBuilder.build()))
                .withAggregation("available_keys",
                        Aggregation.of(a -> a.terms(t -> t.field("musicalKeys").size(50))))
                .withAggregation("available_genres",
                        Aggregation.of(a -> a.terms(t -> t.field("genres").size(100))))
                .withMaxResults(0)
                .build();

        SearchHits<TrackDoc> hits = elasticsearchOperations.search(query, TrackDoc.class);

        List<String> keys = new ArrayList<>();
        List<String> genres = new ArrayList<>();

        if (hits.getAggregations() != null) {
            try {
                var esAggs = (ElasticsearchAggregations) hits.getAggregations();
                var aggMap = esAggs.aggregationsAsMap();

                for (StringTermsBucket bucket : aggMap.get("available_keys").aggregation().getAggregate().sterms().buckets().array()) {
                    keys.add(bucket.key().stringValue());
                }
                for (StringTermsBucket bucket : aggMap.get("available_genres").aggregation().getAggregate().sterms().buckets().array()) {
                    genres.add(bucket.key().stringValue());
                }
            } catch (Exception e) {
                log.warn("解析聚合结果失败: {}", e.getMessage(), e);
            }
        }

        Collections.sort(keys);
        Collections.sort(genres);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("musicalKeys", keys);
        result.put("genres", genres);
        return result;
    }

    // ======================== 语义搜索（kNN） ========================

    @Override
    public List<TrackDoc> semanticSearch(String queryText, int k) {
        log.info("语义搜索: queryText='{}', k={}", queryText, k);

        // 1. 调用 Python 分析服务，将文本转换为 CLAP 向量
        var embedResponse = analysisClient.embedText(new TextEmbedRequest(queryText));
        List<Float> queryVector = embedResponse.vector();

        if (queryVector == null || queryVector.isEmpty()) {
            log.warn("文本嵌入返回空向量: queryText='{}'", queryText);
            return List.of();
        }

        log.debug("文本嵌入完成: dims={}", queryVector.size());

        // 2. 使用 ES 8.x kNN 搜索
        try {
            SearchResponse<TrackDoc> response = elasticsearchClient.search(s -> s
                    .index(INDEX_NAME)
                    .knn(knn -> knn
                            .field("audioVector")
                            .queryVector(queryVector)
                            .k(k)
                            .numCandidates(k * 10)
                            .filter(f -> f.term(t -> t.field("status").value(STATUS_PUBLISHED)))
                    )
                    .size(k),
                    TrackDoc.class
            );

            List<TrackDoc> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("语义搜索完成: queryText='{}', 命中 {} 条", queryText, results.size());
            return results;

        } catch (Exception e) {
            log.error("ES kNN 搜索异常: queryText='{}', error={}", queryText, e.getMessage(), e);
            return List.of();
        }
    }
}

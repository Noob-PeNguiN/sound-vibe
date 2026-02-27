package com.soundvibe.search.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Elasticsearch 作品文档
 * 映射到 ES 索引 "tracks"，用于全文搜索和过滤
 * <p>
 * v2 重构：
 * - bpm/musicalKey/duration 变为数组（支持 PACK 含多个音频场景）
 * - 移除 audioId（PACK 有多个文件，不再使用单一 audioId）
 * - 新增 trackType 区分 SINGLE / PACK
 * - ES 的 TermQuery/RangeQuery 对数组天然支持"any match"语义
 * <p>
 * 同步来源：vibe-catalog 通过 RabbitMQ 发送聚合后的数组
 *
 * @author SoundVibe Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "tracks")
public class TrackDoc {

    /**
     * 作品 ID（与 MySQL tracks.id 一致）
     */
    @Id
    private Long id;

    /**
     * 作品标题（全文检索，使用 standard 分词器）
     */
    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String title;

    /**
     * 作品类型: SINGLE / PACK
     */
    @Field(type = FieldType.Keyword)
    private String trackType;

    /**
     * 发布者 ID
     */
    @Field(type = FieldType.Long)
    private Long producerId;

    /**
     * 发布者用户名（精确匹配）
     */
    @Field(type = FieldType.Keyword)
    private String producerName;

    /**
     * 封面资产 ID
     */
    @Field(type = FieldType.Long)
    private Long coverId;

    /**
     * BPM 值列表（从关联的 assets 聚合）
     * SINGLE: [120]  |  PACK: [120, 140, 160]
     * ES RangeQuery 对数组字段: 只要有任一值在范围内即命中
     */
    @Field(type = FieldType.Integer)
    private List<Integer> bpmValues;

    /**
     * 音乐调式列表（从关联的 assets 聚合）
     * SINGLE: ["C minor"]  |  PACK: ["C minor", "A major"]
     * ES TermQuery 对数组字段: 只要包含目标值即命中
     */
    @Field(type = FieldType.Keyword)
    private List<String> musicalKeys;

    /**
     * 音乐风格/流派列表（从 tags 逗号分隔解析）
     * 用于精确匹配过滤和聚合统计
     */
    @Field(type = FieldType.Keyword)
    private List<String> genres;

    /**
     * 音频时长列表/秒（从关联的 assets 聚合）
     * SINGLE: [210]  |  PACK: [120, 180, 210]
     */
    @Field(type = FieldType.Integer)
    private List<Integer> durations;

    /**
     * 标签（全文检索，逗号分隔的标签字符串）
     */
    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String tags;

    /**
     * 价格
     */
    @Field(type = FieldType.Double)
    private BigDecimal price;

    /**
     * 作品状态: 0=草稿/私密, 1=已上架/公开
     */
    @Field(type = FieldType.Integer)
    private Integer status;

    /**
     * CLAP 音频特征向量（512 维）
     * 由 vibe-analysis Python 服务生成，用于语义搜索（kNN）
     * ES 8.x dense_vector 默认使用 cosine 相似度 + HNSW 索引
     */
    @Field(type = FieldType.Dense_Vector, dims = 512)
    private float[] audioVector;
}

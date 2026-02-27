package com.soundvibe.search.repository;

import com.soundvibe.search.document.TrackDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * 作品 Elasticsearch 文档仓库
 * 提供基于 Spring Data Elasticsearch 的 CRUD 操作
 *
 * @author SoundVibe Team
 */
@Repository
public interface TrackDocRepository extends ElasticsearchRepository<TrackDoc, Long> {
}

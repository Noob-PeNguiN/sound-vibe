package com.soundvibe.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soundvibe.catalog.domain.entity.Track;
import org.apache.ibatis.annotations.Mapper;

/**
 * 音乐作品 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper，提供基础 CRUD 能力
 *
 * @author SoundVibe Team
 */
@Mapper
public interface TrackMapper extends BaseMapper<Track> {
}

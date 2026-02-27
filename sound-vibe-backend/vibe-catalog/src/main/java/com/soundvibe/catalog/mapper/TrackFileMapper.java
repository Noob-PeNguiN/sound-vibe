package com.soundvibe.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soundvibe.catalog.domain.entity.TrackFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作品文件 Mapper 接口
 * 用于 PACK（合集）类型作品的文件列表管理
 *
 * @author SoundVibe Team
 */
@Mapper
public interface TrackFileMapper extends BaseMapper<TrackFile> {
}

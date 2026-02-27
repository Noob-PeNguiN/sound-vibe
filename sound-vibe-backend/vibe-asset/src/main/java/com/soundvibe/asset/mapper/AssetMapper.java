package com.soundvibe.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soundvibe.asset.domain.entity.Asset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数字资产 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper，提供基础 CRUD 能力
 *
 * @author SoundVibe Team
 */
@Mapper
public interface AssetMapper extends BaseMapper<Asset> {
}

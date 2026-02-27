package com.soundvibe.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soundvibe.catalog.domain.entity.Purchase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购买记录 Mapper 接口
 *
 * @author SoundVibe Team
 */
@Mapper
public interface PurchaseMapper extends BaseMapper<Purchase> {
}

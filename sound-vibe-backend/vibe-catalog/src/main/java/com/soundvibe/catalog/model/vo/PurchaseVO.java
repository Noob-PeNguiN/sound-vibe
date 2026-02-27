package com.soundvibe.catalog.model.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 购买记录视图对象
 * 向客户端返回购买信息，包含关联的作品快照
 *
 * @param id           购买记录 ID
 * @param userId       购买者 ID
 * @param trackId      作品 ID
 * @param pricePaid    实付金额（0 = 免费获取）
 * @param track        关联的作品视图（含完整信息）
 * @param createTime   购买时间
 * @author SoundVibe Team
 */
public record PurchaseVO(
        Long id,
        Long userId,
        Long trackId,
        BigDecimal pricePaid,
        TrackVO track,
        Date createTime
) implements Serializable {
}

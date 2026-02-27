package com.soundvibe.catalog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.soundvibe.catalog.model.vo.PurchaseVO;

/**
 * 购买服务接口
 * <p>
 * 当前阶段为模拟购买（不涉及真实支付），
 * 用户点击"购买"或"免费获取"后立即创建购买记录，
 * 后续可对接支付网关实现真实交易。
 *
 * @author SoundVibe Team
 */
public interface PurchaseService {

    /**
     * 购买作品（模拟，直接成功）
     *
     * @param trackId 作品 ID
     * @param userId  当前登录用户 ID
     * @return 购买记录视图
     */
    PurchaseVO purchaseTrack(Long trackId, Long userId);

    /**
     * 查询我的已购作品列表（分页）
     *
     * @param userId  当前登录用户 ID
     * @param current 当前页码
     * @param size    每页大小
     * @return 分页购买记录
     */
    IPage<PurchaseVO> listMyPurchases(Long userId, long current, long size);

    /**
     * 检查当前用户是否已购买某作品
     *
     * @param trackId 作品 ID
     * @param userId  当前登录用户 ID
     * @return true = 已购买
     */
    boolean hasPurchased(Long trackId, Long userId);

    /**
     * 订单支付成功后，由 vibe-order 调用的内部接口，
     * 跳过自购校验和库存扣减（这些在下单时已完成）。
     * 幂等：若已存在相同 userId+trackId 的记录则跳过。
     *
     * @param trackId  作品 ID
     * @param userId   购买者 ID
     * @param pricePaid 实付金额
     */
    void confirmPurchase(Long trackId, Long userId, java.math.BigDecimal pricePaid);
}

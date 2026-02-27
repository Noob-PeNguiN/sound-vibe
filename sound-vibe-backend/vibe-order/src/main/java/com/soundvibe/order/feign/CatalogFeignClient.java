package com.soundvibe.order.feign;

import com.soundvibe.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Catalog 服务 Feign 客户端
 * 用于远程调用 vibe-catalog 校验作品价格和库存，以及同步购买记录
 *
 * @author SoundVibe Team
 */
@FeignClient(name = "vibe-catalog", url = "${feign.catalog.url:}", path = "/catalog")
public interface CatalogFeignClient {

    /**
     * 获取作品详情
     */
    @GetMapping("/tracks/{id}")
    Result<Map<String, Object>> getTrackDetail(@PathVariable("id") Long id);

    /**
     * 订单支付成功后，同步创建购买记录到 purchase 表
     */
    @PostMapping("/purchases/confirm")
    Result<Void> confirmPurchase(@RequestParam("trackId") Long trackId,
                                 @RequestParam("userId") Long userId,
                                 @RequestParam("pricePaid") BigDecimal pricePaid);
}

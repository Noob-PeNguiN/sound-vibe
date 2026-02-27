package com.soundvibe.order.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车条目 DTO
 * 存储在 Redis Hash 中的单个购物车项
 *
 * @author SoundVibe Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO implements Serializable {

    @NotNull(message = "作品ID不能为空")
    private Long trackId;

    @NotBlank(message = "作品标题不能为空")
    private String title;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    @NotBlank(message = "授权类型不能为空")
    private String licenseType;

    private String coverUrl;
}

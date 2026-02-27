package com.soundvibe.asset.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 重命名资产请求体
 *
 * @param newName 新文件名（不含扩展名，扩展名保持不变）
 * @author SoundVibe Team
 */
public record RenameAssetRequest(
        @NotBlank(message = "新文件名不能为空")
        @Size(max = 200, message = "文件名不能超过200个字符")
        String newName
) implements Serializable {
}

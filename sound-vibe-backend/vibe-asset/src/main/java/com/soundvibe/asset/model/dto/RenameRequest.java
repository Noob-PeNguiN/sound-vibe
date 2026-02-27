package com.soundvibe.asset.model.dto;

import java.io.Serializable;

/**
 * 重命名请求体
 *
 * @param newName 新文件名
 * @author SoundVibe Team
 */
public record RenameRequest(
        String newName
) implements Serializable {
}

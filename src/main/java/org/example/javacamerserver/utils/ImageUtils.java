package org.example.javacamerserver.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
public class ImageUtils {
    // 默认保存路径（可配置在 application.yml）
    private static final String UPLOAD_DIR = "captured_images/";

    /**
     * 将 Base64 字符串保存为图片文件
     * @param base64Data 完整的 Base64 字符串（可包含 data:image/png;base64, 前缀）
     * @param customFileName 自定义文件名（可选，若为空则生成随机文件名）
     * @return 保存后的文件路径，失败返回 null
     */
    public static String saveBase64Image(String base64Data, String customFileName) {
        if (base64Data == null || base64Data.isEmpty()) {
            return null;
        }

        try {
            // 1. 清理 Base64 字符串（去除 data:image/...;base64, 前缀）
            String cleanedBase64 = base64Data.replaceAll("^data:image/[^;]+;base64,", "");

            // 2. 解码 Base64 为字节数组
            byte[] imageBytes = Base64.getDecoder().decode(cleanedBase64);

            // 3. 确定文件扩展名（根据 MIME 类型或默认 .png）
            String fileExtension ="png"; //getFileExtensionFromBase64(base64Data);
            String fileName = (customFileName != null && !customFileName.isEmpty()) ?
                    customFileName + "." + fileExtension :
                    generateRandomFileName(fileExtension);
            // 4. 创建目标目录（如果不存在）
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs(); // 创建多级目录
            }

            // 5. 写入文件
            String filePath = UPLOAD_DIR + fileName;
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(imageBytes);
            }

            return filePath;
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从 Base64 字符串中提取文件扩展名（如 "png"）
     */
    private static String getFileExtensionFromBase64(String base64Data) {
        String mimeType = base64Data.split(";")[0].split("/")[1];
        return mimeType.equalsIgnoreCase("jpeg") ? "jpg" : mimeType;
    }

    /**
     * 生成随机文件名（UUID + 扩展名）
     */
    private static String generateRandomFileName(String fileExtension) {
        return UUID.randomUUID().toString() + "." + fileExtension;
    }
}

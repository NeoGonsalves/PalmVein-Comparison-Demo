package org.example.javacamerserver.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
public class ByteBufferUtil {
    public static float[] base64ToFloatArray(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return new float[0];
        }
        // 1. 健壮的Base64解码
        byte[] bytes = robustBase64Decode(base64String);
//        Path path = Paths.get(base64String.substring(0,5)+".bin");
//        try (OutputStream os = Files.newOutputStream(path,
//                StandardOpenOption.CREATE,    // 创建文件（如果不存在）
//                StandardOpenOption.TRUNCATE_EXISTING)) { // 清空已存在内容
//
//            os.write(bytes); // 写入字节数组
//
//            System.out.println("文件保存成功: " + path.toAbsolutePath());
//        } catch (IOException e) {
//            System.err.println("文件保存失败: " + e.getMessage());
//        }
        if (bytes == null) {
            throw new IllegalArgumentException("无效的Base64字符串");
        }
        //System.out.println(bytes);
        // 2. 检查字节长度是否为4的倍数（每个float占4字节）
        if (bytes.length % 4 != 0) {
            throw new IllegalArgumentException("字节长度必须是4的倍数，实际长度: " + bytes.length);
        }

        // 3. 创建ByteBuffer并设置字节序
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        // 根据您的系统设置正确的字节序
         buffer.order(ByteOrder.LITTLE_ENDIAN); // 小端序（Windows系统常用）
//        buffer.order(ByteOrder.BIG_ENDIAN);    // 大端序（Java默认，网络传输常用）

        // 4. 转换为float数组
        int floatCount = bytes.length / 4;
        float[] floatArray = new float[floatCount];

        for (int i = 0; i < floatCount; i++) {
            floatArray[i] = buffer.getFloat();
        }

        return floatArray;
    }
    // 健壮的Base64解码方法
    public static byte[] robustBase64Decode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            System.err.println("Base64字符串为空");
            return null;
        }

        try {
            // 移除所有非Base64字符
            String cleaned = base64.replaceAll("[^a-zA-Z0-9+/]", "");

            // 处理填充问题
            int mod = cleaned.length() % 4;
            if (mod != 0) {
                cleaned += "==".substring(0, 4 - mod);
            }

            // 尝试标准解码
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e1) {
            System.err.println("标准Base64解码失败: " + e1.getMessage());
            try {
                // 尝试MIME解码（处理换行符等）
                return Base64.getMimeDecoder().decode(base64);
            } catch (IllegalArgumentException e2) {
                System.err.println("MIME Base64解码失败: " + e2.getMessage());
                try {
                    // 尝试URL安全解码
                    return Base64.getUrlDecoder().decode(base64);
                } catch (IllegalArgumentException e3) {
                    System.err.println("URL安全Base64解码失败: " + e3.getMessage());
                    try {
                        // 最后尝试：移除所有非字母数字字符
                        String fallback = base64.replaceAll("[^a-zA-Z0-9]", "");
                        return Base64.getDecoder().decode(fallback);
                    } catch (IllegalArgumentException e4) {
                        System.err.println("最终Base64解码失败: " + e4.getMessage());
                        return null;
                    }
                }
            }
        }
    }

    public static float[] byteArrayToFloatArray(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("无效的Base64字符串");
        }
        //System.out.println(bytes);
        // 2. 检查字节长度是否为4的倍数（每个float占4字节）
        if (bytes.length % 4 != 0) {
            throw new IllegalArgumentException("字节长度必须是4的倍数，实际长度: " + bytes.length);
        }

        // 3. 创建ByteBuffer并设置字节序
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        // 根据您的系统设置正确的字节序
        buffer.order(ByteOrder.LITTLE_ENDIAN); // 小端序（Windows系统常用）
//        buffer.order(ByteOrder.BIG_ENDIAN);    // 大端序（Java默认，网络传输常用）

        // 4. 转换为float数组
        int floatCount = bytes.length / 4;
        float[] floatArray = new float[floatCount];

        for (int i = 0; i < floatCount; i++) {
            floatArray[i] = buffer.getFloat();
        }

        return floatArray;
    }

    // 将byte[]转为标准Base64字符串
    public static String encodeToBase64(byte[] data) {
        if (data == null || data.length == 0) {
            System.err.println("输入字节数组为空");
            return "";
        }
        return Base64.getEncoder().encodeToString(data);
    }
}

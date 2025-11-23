package org.example.javacamerserver.xrCamer;

import java.util.concurrent.atomic.AtomicReference;

public final class CameraState {
    // ------------------------------
    // 私有静态变量（线程安全）
    // ------------------------------
    private static volatile boolean isCameraFlag = false; // 是否需要拍照
    private static final AtomicReference<String> cameraName =
        new AtomicReference<>(""); // 图片路径（线程安全）
    private static volatile boolean isTransform = true; // 是否要转换
    // 禁止实例化
    private CameraState() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    // ------------------------------
    // isCameraFlag 的访问方法
    // ------------------------------
    /**
     * 获取拍照标志状态
     * @return true=需要拍照, false=不需要
     */
    public static boolean isCameraFlag() {
        return isCameraFlag;
    }

    /**
     * 设置拍照标志状态
     * @param flag true=需要拍照, false=不需要
     */
    public static synchronized void setCameraFlag(boolean flag) {
        isCameraFlag = flag;
    }

    public static boolean isIsTransform() {
        return isTransform;
    }

    public static void setIsTransform(boolean flag) {
        isTransform = flag;
    }
// ------------------------------
    // cameraName 的访问方法
    // ------------------------------
    /**
     * 获取当前图片路径
     * @return 图片路径（可能为空）
     */
    public static String getCameraName() {
        return cameraName.get();
    }

    /**
     * 设置图片路径
     * @param name 新的图片路径
     */
    public static void setCameraName(String name) {
        cameraName.set(name != null ? name : "");
    }

    /**
     * 清空图片路径
     */
    public static void clearCameraName() {
        cameraName.set("");
    }
}
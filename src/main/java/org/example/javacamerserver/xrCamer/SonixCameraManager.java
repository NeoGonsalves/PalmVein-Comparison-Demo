package org.example.javacamerserver.xrCamer;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

// 摄像头管理器，用于封装操作逻辑
@Component // 添加这个注解
public class SonixCameraManager {
    private final SonixCamera api;
    private SonixCamera.scDevice device;
    private WinDef.HWND hWnd;
    private SonixCamera.SampleGrabberCB callback;
    private SonixCamera.SampleGrabberCB2 callback2;
    private boolean isInitialized = false;
    public boolean isOpened = false;
    private ScheduledExecutorService scheduler;
    private int captureInterval = 1000; // 默认1秒
    private String capturePath = "captured_images/";

    private SonixCamera.scDevice[] myCamerasList;

    private AtomicInteger photoCount = new AtomicInteger(0);
    private Pointer callbackPointer; // 存储回调指针
    // 添加一个方法来获取单例实例（如果需要）
    private static SonixCameraManager instance;

    public static SonixCameraManager getInstance() {
        return instance;
    }

    @PostConstruct
    public void registerInstance() {
        instance = this;
    }
    // 使用构造函数注入（推荐）
    public SonixCameraManager() {
        this.api = SonixCamera.INSTANCE;
        this.hWnd = new WinDef.HWND();
        this.scheduler = Executors.newScheduledThreadPool(1);



        //初始化myCamerasList
        //final VeinProcessor veinProcessor = new VeinProcessor();
        //myCamerasList = veinProcessor.getCamerasList();
    }

    // 初始化摄像头
    public boolean initialize() {
        if (isInitialized) {
            return true;
        }

        if (api.SonixCam_Init()) {
            // 创建用于接收设备数量的引用
            IntByReference devCount = new IntByReference();
            // 创建单个结构体实例
            SonixCamera.scDevice prototype = new SonixCamera.scDevice();
            myCamerasList = (SonixCamera.scDevice[]) prototype.toArray(10);
            // 枚举设备
            System.out.println("开始枚举设备");
            boolean res = SonixCamera.INSTANCE.SonixCam_EnumCameras(devCount, myCamerasList, 1);
            System.out.println("结束枚举设备："+res);
            isInitialized = true;
            return true;
        }



        return false;
    }
    // 初始化摄像头
    public boolean release() {
        if (isInitialized) {
            if (api.SonixCam_UnInit()) {
                isInitialized = false;
                return true;
            }
        }

        return false;
    }

    // 打开指定摄像头
    public boolean openCamera(int deviceIndex) {
        if (!isInitialized) {
            if (!initialize()) {
                return false;
            }
        }
/*
        IntByReference deviceCount = new IntByReference();
        PointerByReference deviceList = new PointerByReference();*/
        //
        //if (!api.SonixCam_GetDeviceList(deviceList, deviceCount)) {
        //    System.err.println("获取设备列表失败");
        //    return false;
        //}


        // 使用toArray方法创建连续内存的数组
        //SonixCamera.scDevice[]
        // 显式清空数组元素（如果数组复用）

        //SonixCamera.scDeviceInfo deviceInfo = new SonixCamera.scDeviceInfo();
        //打开视频回调方法
        //判断锁是否打开，如果打开，则不执行保存的方法
        callback = (sampleTime, buffer, bufferSize,  ptrClass) -> {
            //System.out.println("33");


            // 1. 读取 Base64 编码的 JPEG 数据（注意：buffer 可能已直接返回 Base64 字符串）
            String base64Image = Base64.getEncoder().encodeToString(buffer.getByteArray(0, bufferSize));
            // 或直接获取 buffer 中的字符串（若回调返回的是字符串而非字节数组）
            // String base64Image = buffer.getString(0);

            // 2. 解码 Base64 为 JPEG 字节数组
            byte[] jpegData = Base64.getDecoder().decode(base64Image);

            // 3. 验证是否为 JPEG 格式（可选）
            if (!isJpegData(jpegData)) {
                System.err.println("非 JPEG 数据！");
//                return 0;
            }



            //VeinProcessor veinProcessor = new VeinProcessor();
            boolean isCameraFlag = CameraState.isCameraFlag();
            boolean isTerminated = CameraState.isIsTransform();
            if (isCameraFlag&&isTerminated ) {
                // 4. 将 JPEG 数据转为 BufferedImage（Java 内置方式）
                ByteArrayInputStream bis = new ByteArrayInputStream(jpegData);
                BufferedImage originalImage = ImageIO.read(bis);
                try {
                    bis.close();
                    // 5. 旋转图像（以顺时针 90 度为例）
                    int width = originalImage.getWidth();
                    int height = originalImage.getHeight();
                    BufferedImage rotatedImage = new BufferedImage(height, width, originalImage.getType());
                    Graphics2D g2d = rotatedImage.createGraphics();
                    // 设置旋转中心为旋转后图像的中心，并执行旋转
                    g2d.rotate(Math.toRadians(90), height / 2, width / 2);
                    // 调整绘制位置：将原图中心对齐到旋转中心
                    g2d.drawImage(originalImage, (height - width) / 2, (width - height) / 2, null);
                    g2d.dispose();
                    // 6. 将旋转后的图像转为 Base64（可选：如需保持 JPEG 格式）

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    ImageIO.write(rotatedImage, "JPEG", outputStream);
                    String rotatedBase64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                    byte[] data = new byte[bufferSize];
                    buffer.read(0, data, 0, bufferSize);
//                    String base64Image = Base64.getEncoder().encodeToString(data);
                    //设置不抓拍图片
                    //veinProcessor.setIsCameraFlag(false);
                    //CameraState.setCameraFlag(false);
                    //System.out.println("base64Image:"+base64Image);
                    //设置图片路径
                    System.out.println("抓拍");
                    CameraState.setCameraName(rotatedBase64);
                    CameraState.setIsTransform(false);
                    CameraState.setCameraFlag(false);
                    System.out.println("抓拍结束");
                    data=null;
                    base64Image=null;
                    /*try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }*/
                    //return 0; // 返回0表示处理成功
                } catch (Exception e){
                    e.printStackTrace();
                    //callbackLatch.countDown(); // 释放等待锁
                }
            } else {
                //System.out.println("不抓拍");
            }
            // 这里可以添加帧处理逻辑

        };

        if (api.SonixCam_OpenCamera(myCamerasList[0].getPointer(), hWnd, callback, Pointer.NULL)) {
            isOpened = true;
            return true;
        }

        return false;
    }

    // 检查数据是否是JPEG格式
    private boolean isJpegData(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        // JPEG文件开头两个字节是0xFF 0xD8
        return (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
    }

    // 将原始图像数据转换为JPEG格式
    private boolean convertToJpeg(byte[] data, int width, int height, File outputFile) {
        try {
            // 创建BufferedImage
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

            // 将原始数据复制到图像
            WritableRaster raster = image.getRaster();
            DataBufferByte buffer = (DataBufferByte) raster.getDataBuffer();
            byte[] imageData = buffer.getData();
            System.arraycopy(data, 0, imageData, 0, Math.min(data.length, imageData.length));

            // 保存为JPEG文件
            ImageIO.write(image, "JPEG", outputFile);
            return true;
        } catch (Exception e) {
            System.err.println("转换为JPEG失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // 开始定时抓拍
    public void startCapture() throws InterruptedException {
        if (!isOpened) {
            System.err.println("摄像头未打开");
            return;
        }

        // 如果已经有任务在运行，先停止
        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = Executors.newScheduledThreadPool(1);
        }

        //scheduler.scheduleAtFixedRate(() -> {
        //    try {
        //        System.out.println("执行了哦");
        //        //SonixCamera.SonixCam_SampleGrabberBuffer buffer = new SonixCamera.SonixCam_SampleGrabberBuffer();
        //        //if (api.SonixCam_StillSnapshot(myCamerasList[0].getPointer(), buffer, Pointer.NULL)) {
        //        //    System.out.println(buffer);
        //        //    saveImage(buffer, System.currentTimeMillis() + ".bin");
        //        //}
        //
        //
        //    } catch (Exception e) {
        //        // 捕获所有异常，确保任务不会终止
        //        System.err.println("抓拍过程中出现异常: " + e.getMessage());
        //        e.printStackTrace();
        //    }
        //
        //}, 0, captureInterval, TimeUnit.MILLISECONDS);

        //开始预览，会触发callback 方法回调，就在callback实现图片抓拍
        boolean startPreview = SonixCamera.INSTANCE.SonixCam_StartPreview(myCamerasList[0].getPointer());

        //设置预览的格式为1920 * 1080
//        SonixCamera.INSTANCE.SonixCam_SetPreviewFormat(myCamerasList[0].getPointer(), 0, new WinDef.HWND(Pointer.NULL) );

        SonixCamera.scVideoOutFormat pFormat = new SonixCamera.scVideoOutFormat();
        boolean previewFormat =  SonixCamera.INSTANCE.SonixCam_GetPreviewFormat(myCamerasList[0].getPointer(), pFormat );


        //开始抓拍
        //VeinProcessor veinProcessor = new VeinProcessor();
        //veinProcessor.setIsCameraFlag(true);
        CameraState.setCameraFlag(true);

        //单次抓拍好像没有成功
        //System.out.println(startPreview);
        //callback2 = ( sampleTime,  buffer,  bufferSize,  ptrClass)-> {
        //        System.out.println("999999");
        //};
        //
        //// 打开摄像头并设置回调
        //boolean success = api.SonixCam_StillSnapshot(myCamerasList[0].getPointer(), callback2, Pointer.NULL);
        //
        //Thread.sleep(5000);
        // 等待回调执行完成（最多5秒）
        //try {
        //    if (!callbackLatch.await(5, TimeUnit.SECONDS)) {
        //        System.err.println("等待回调超时");
        //    }
        //} catch (InterruptedException e) {
        //    Thread.currentThread().interrupt();
        //    System.err.println("等待过程被中断");
        //}
    }





    // 保存图像
    private void saveImage(SonixCamera.SonixCam_SampleGrabberBuffer buffer, String fileName) {
        System.out.println("保存照片");
        try (FileOutputStream fos = new FileOutputStream(capturePath + fileName)) {
            //byte[] data = new byte[buffer.cbBuffer];
            //buffer.pBuffer.read(0, data, 0, buffer.cbBuffer);
            //fos.write(data);
            //System.out.println("图像保存成功: " + fileName + ", 大小: " + data.length + " 字节");
            System.out.println("保存方法");
        } catch (IOException e) {
            System.err.println("保存图像失败: " + e.getMessage());
        }
    }

    // 设置抓拍间隔(毫秒)
    public void setCaptureInterval(int interval) {
        this.captureInterval = interval;
    }

    // 设置保存路径
    public void setCapturePath(String path) {
        this.capturePath = path;
    }

    // 关闭摄像头
    public void closeCamera() {
        if (isOpened) {
            scheduler.shutdown();
            try {
                //api.INSTANCE.SonixCam_UnInit();
            }catch (Exception e ){
                e.printStackTrace();
            }

            //api.INSTANCE.SonixCam_CloseCamera(myCamerasList[0].getPointer());
            //api.INSTANCE.SonixCam_FreeDeviceList(myCamerasList[0].getPointer());
//            api.INSTANCE.SonixCam_PausePreview(myCamerasList[0].getPointer());
//            api.INSTANCE.SonixCam_StopPreview(myCamerasList[0].getPointer());
            //isOpened = false;
        }

        if (isInitialized) {
            //api.SonixCam_Release();
//            api.INSTANCE.SonixCam_CloseCamera(myCamerasList[0].getPointer());
            /*if (myCamerasList != null) {
                Arrays.fill(myCamerasList, null);
            }*/
            //isInitialized = false;
        }
    }

    // 程序结束时调用
    protected void finalize() throws Throwable {
        closeCamera();
        super.finalize();
    }
}

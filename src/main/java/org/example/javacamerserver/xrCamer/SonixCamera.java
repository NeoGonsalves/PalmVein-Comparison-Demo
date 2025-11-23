package org.example.javacamerserver.xrCamer;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * SonixLib摄像头库的JNA接口
 */
public interface SonixCamera extends Library {
    SonixCamera INSTANCE = Native.load("SonixCamera", SonixCamera.class);

    //// 定义回调函数接口
    //interface SampleGrabberCB extends com.sun.jna.Callback {
    //    void invoke(Pointer pBuffer, int bufferLen, int width, int height, int stride, int format, Pointer ptrClass);
    //}

    // 定义 SonixCam_SampleGrabberBuffer 结构体
    //class SonixCam_SampleGrabberBuffer extends Structure {
    //    public int cbBuffer;
    //    public Pointer pBuffer;
    //    public byte buffer;
    //
    //    @Override
    //    protected List<String> getFieldOrder() {
    //        return Arrays.asList("cbBuffer", "pBuffer", "buffer");
    //    }
    //}

    // 定义SampleGrabberBuffer结构体
    //class SonixCam_SampleGrabberBuffer extends Structure {
    //    public double sampleTime;
    //    public Pointer buffer;
    //    public int bufferSize;
    //    public Pointer ptrClass;
    //
    //    @Override
    //    protected List<String> getFieldOrder() {
    //        return Arrays.asList("sampleTime", "buffer", "bufferSize", "ptrClass");
    //    }
    //}

    interface SonixCam_SampleGrabberBuffer extends StdCallLibrary.StdCallCallback {
        /**
         * 视频流数据回调函数
         * @param sampleTime 采样时间戳（单位：秒，精确到毫秒）
         * @param buffer     原始视频帧数据指针
         * @param bufferSize 数据缓冲区大小（字节数）
         * @param ptrClass   用户自定义上下文指针（可选）
         * @return           状态码（0 表示成功，非零为错误码）
         */
        int invoke(double sampleTime, Pointer buffer, int bufferSize, Pointer ptrClass);
    }

    interface SonixCam_SampleGrabberBuffer2 extends StdCallLibrary.StdCallCallback {
        /**
         * 视频流数据回调函数
         * @param sampleTime 采样时间戳（单位：秒，精确到毫秒）
         * @param buffer     原始视频帧数据指针
         * @param bufferSize 数据缓冲区大小（字节数）
         * @param ptrClass   用户自定义上下文指针（可选）
         * @return           状态码（0 表示成功，非零为错误码）
         */
        int invoke(double sampleTime, Pointer buffer, int bufferSize, Pointer ptrClass);
    }

    // 定义抓拍回调接口
    interface SnapshotCallback extends StdCallLibrary.StdCallCallback {
        int invoke(double sampleTime, Pointer buffer, int bufferSize, Pointer ptrClass);
    }

    // 定义帧抓取回调接口
    interface SampleGrabberCB extends StdCallLibrary.StdCallCallback {
        //void invoke(Pointer pBuffer, int bufferLen, int width, int height, int stride, int format, Pointer ptrClass);
        void invoke(double sampleTime, Pointer buffer, int bufferSize, Pointer ptrClass) throws IOException;
    }

    interface SampleGrabberCB2 extends StdCallLibrary.StdCallCallback {
        //void invoke(Pointer pBuffer, int bufferLen, int width, int height, int stride, int format, Pointer ptrClass);
        void invoke(double sampleTime, Pointer buffer, int bufferSize, Pointer ptrClass);
    }

    // 视频输出格式结构体
    class scVideoOutFormat extends Structure {
        public int width;
        public int height;
        public int pixelFormat;
        public int fourCC;
        public int bitsPerPixel;
        public int frameInterval;
        public int reserved1;
        public int reserved2;
        public int reserved3;
        public int reserved4;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("width", "height", "pixelFormat", "fourCC", "bitsPerPixel", "frameInterval",
                    "reserved1", "reserved2", "reserved3", "reserved4");
        }
    }

    // 设备信息结构体
    class scDeviceInfo extends Structure {
        public byte[] deviceName = new byte[128];
        public byte[] friendlyName = new byte[128];
        public byte[] devicePath = new byte[256];
        public int deviceType;
        public int reserved1;
        public int reserved2;
        public int reserved3;
        public int reserved4;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("deviceName", "friendlyName", "devicePath", "deviceType",
                    "reserved1", "reserved2", "reserved3", "reserved4");
        }

        @Override
        public String toString() {
            try {
                String name = new String(deviceName, "UTF-8").trim();
                String friendly = new String(friendlyName, "UTF-8").trim();
                String path = new String(devicePath, "UTF-8").trim();
                return "设备名称: " + name + ", 友好名称: " + friendly + ", 设备路径: " + path;
            } catch (Exception e) {
                return super.toString();
            }
        }
    }

    // 设备结构体
    class scDevice extends Structure {
        public Pointer handle;
        public int deviceId;
        public byte[] devicePath = new byte[256];
        public int deviceType;
        public int reserved1;
        public int reserved2;
        public int reserved3;
        public int reserved4;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("handle", "deviceId", "devicePath", "deviceType",
                    "reserved1", "reserved2", "reserved3", "reserved4");
        }
    }

    // 电源频率枚举
    class scPowerLine extends Structure {
        public static final int PL_AUTO = 0;
        public static final int PL_50Hz = 1;
        public static final int PL_60Hz = 2;

        public int value;

        public scPowerLine() {
        }

        public scPowerLine(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("value");
        }
    }

    // 属性枚举
    enum scProperty {
        PROP_BRIGHTNESS,
        PROP_CONTRAST,
        PROP_HUE,
        PROP_SATURATION,
        PROP_SHARPNESS,
        PROP_GAMMA,
        PROP_COLORENABLE,
        PROP_WHITEBALANCE,
        PROP_BACKLIGHTCOMPENSATION,
        PROP_GAIN,
        PROP_POWERLINEFREQUENCY,
        PROP_HUE_AUTO,
        PROP_WHITEBALANCE_AUTO,
        PROP_EXPOSURE_AUTO,
        PROP_EXPOSURE_ABSOLUTE,
        PROP_FOCUS_AUTO,
        PROP_FOCUS_ABSOLUTE,
        PROP_IRIS_ABSOLUTE,
        PROP_ZOOM_ABSOLUTE,
        PROP_PAN_RELATIVE,
        PROP_TILT_RELATIVE,
        PROP_ROLL_RELATIVE,
        PROP_PAN_ABSOLUTE,
        PROP_TILT_ABSOLUTE,
        PROP_ROLL_ABSOLUTE,
        PROP_PRIVACY,
        PROP_AUTO_EXPOSURE_PRIORITY,
        PROP_SHUTTER_SPEED,
        PROP_GAIN_DB,
        PROP_WHITEBALANCE_TEMPERATURE,
        PROP_DIGITAL_MULTIPLIER,
        PROP_DIGITAL_MULTIPLIER_LIMIT,
        PROP_HUE_RELATIVE,
        PROP_SATURATION_RELATIVE,
        PROP_SHARPNESS_RELATIVE,
        PROP_GAMMA_RELATIVE,
        PROP_BRIGHTNESS_RELATIVE,
        PROP_CONTRAST_RELATIVE
    }

    // 属性标志枚举
    enum scPropertyFlag {
        PROP_FLAG_MANUAL,
        PROP_FLAG_AUTO,
        PROP_FLAG_ABSOLUTE,
        PROP_FLAG_RELATIVE
    }

    // 控制枚举
    enum scControl {
        CTRL_PAN,
        CTRL_TILT,
        CTRL_ROLL,
        CTRL_ZOOM,
        CTRL_EXPOSURE,
        CTRL_IRIS,
        CTRL_FOCUS
    }

    // 初始化库
    boolean SonixCam_Init();

    // 反初始化
    boolean SonixCam_UnInit();

    // 获取设备列表
    boolean SonixCam_GetDeviceList(PointerByReference ppDeviceList, IntByReference pDeviceCount);

    // 释放设备列表
    boolean SonixCam_FreeDeviceList(Pointer pDeviceList);
    //boolean SonixCam_FreeDeviceList(scDevice pDeviceList);

    // 获取设备信息
    //boolean SonixCam_GetDeviceInfo(Pointer pDevice, scDeviceInfo pInfo);
    boolean SonixCam_GetDeviceInfo(scDevice[] devices, scDeviceInfo pInfo);

    // 打开摄像头设备
    //boolean SonixCam_OpenCamera(Pointer pDevice, WinDef.HWND hWnd, SampleGrabberCB callback, Pointer ptrClass);
    //boolean SonixCam_OpenCamera(Pointer pDevice, WinDef.HWND hWnd, SampleGrabberCB callback, Pointer ptrClass);

    // 关闭摄像头设备
    boolean SonixCam_CloseCamera(Pointer pDevice);
    //boolean SonixCam_CloseCamera(scDevice[] pDevice);

    // 是否打开了摄像头
    boolean SonixCam_IsOpened(Pointer pDevice);

    // 摄像头是否正在浏览中
    boolean SonixCam_IsPreviewing(Pointer pDevice);

    // 调整浏览窗口
    boolean SonixCam_AdjustPreviewWindow(Pointer pDevice, boolean visable, int x, int y, int right, int bottom);

    // 开始浏览
    boolean SonixCam_StartPreview(Pointer pDevice);

    // 暂停浏览
    boolean SonixCam_PausePreview(Pointer pDevice);

    // 停止浏览
    boolean SonixCam_StopPreview(Pointer pDevice);

    // 获得浏览或采样视频格式数量
    boolean SonixCam_GetFormatCount(Pointer pDevice, IntByReference formatCount);

    // 获得指定索引对应的视频格式
    boolean SonixCam_GetFormat(Pointer pDevice, byte formatIndex, scVideoOutFormat pFormat);

    // 获得当前浏览格式
    boolean SonixCam_GetPreviewFormat(Pointer pDevice, scVideoOutFormat pFormat);

    // 设置浏览或采样视频格式
    boolean SonixCam_SetPreviewFormat(Pointer pDevice, int formatIndex, WinDef.HWND preWndHandle);

    // 获得Still Pin拍照格式数量
    boolean SonixCam_GetStillFormatCount(Pointer pDevice, IntByReference formatCount);

    // 获得索引对应的Still Pin拍照格式
    boolean SonixCam_GetStillFormat(Pointer pDevice, int formatIndex, scVideoOutFormat pFormat);

    // 获得当前Still Pin设置的视频格式
    boolean SonixCam_GetCurStillFormat(Pointer pDevice, scVideoOutFormat pFormat);

    // 设置Still Pin拍照格式
    boolean SonixCam_SetStillFormat(Pointer pDevice, int formatIndex);

    // Still Pin拍照
    //boolean SonixCam_StillSnapshot(Pointer pDevice, SonixCam_SampleGrabberBuffer sampleGrab, Pointer ptrClass);
    boolean SonixCam_StillSnapshot(Pointer pDevice, SampleGrabberCB2 callback, Pointer ptrClass);
    //boolean SonixCam_StillSnapshot(scDevice[] pDevice, SonixCam_SampleGrabberBuffer sampleGrab, Pointer ptrClass);
    boolean SonixCam_OpenCamera(Pointer pDevice, WinDef.HWND hWnd, SampleGrabberCB callback, Pointer ptrClass);

    // 获得属性范围
    boolean SonixCam_PropertyGetRange(Pointer pDevice, scProperty property, LongByReference min, LongByReference max,
                                      LongByReference step, LongByReference defValue, IntByReference flags);

    // 获得属性项值
    boolean SonixCam_PropertyGet(Pointer pDevice, scProperty property, LongByReference value, IntByReference flags);

    // 设置属性
    boolean SonixCam_PropertySet(Pointer pDevice, scProperty property, long value, int flags);

    // 获得控制范围
    boolean SonixCam_ControlGetRange(Pointer pDevice, scControl control, LongByReference min, LongByReference max,
                                     LongByReference step, LongByReference defValue, IntByReference flags);

    // 获得控制项值
    boolean SonixCam_ControlGet(Pointer pDevice, scControl control, LongByReference value, IntByReference flags);

    // 设置控制项值
    boolean SonixCam_ControlSet(Pointer pDevice, scControl control, long value, int flags);

    // 获得帧率
    boolean SonixCam_GetFrameRate(Pointer pDevice, ByteByReference fps);

    // 设置帧率
    boolean SonixCam_SetFrameRate(Pointer pDevice, int fps);

    // 获得电力频域值
    boolean SonixCam_GetPowerLine(Pointer pDevice, scPowerLine powerLine);

    // 设置电力频率值
    boolean SonixCam_SetPowerLine(Pointer pDevice, scPowerLine powerLine);

    // 获得低亮度补偿状态
    boolean SonixCam_GetAutoExposurePriority(Pointer pDevice, LongByReference check);

    // 设置低亮度补偿状态
    boolean SonixCam_SetAutoExposurePriority(Pointer pDevice, long check);

    boolean SonixCam_AsicRegisterRead(scDevice[] pDevice, int addr, byte[] data, int dataLen);

    boolean SonixCam_AsicRegisterWrite(scDevice[] pDevice, int addr, byte[] data, int dataLen);

    //枚举所有SONIX摄像头设备
    boolean SonixCam_EnumCameras(IntByReference devCount, scDevice devs[], int devsArraySize);


    //定义 XR_BSP_Chip_SM2Decrypt 方法
    int XR_BSP_Chip_SM2Decrypt(Pointer dev_list, byte[] in_buff, short in_len, byte[] out_buff, short out_len);


}

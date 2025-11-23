package org.example.javacamerserver.xrCamer;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.javacamerserver.utils.ByteBufferUtil.*;
import static org.example.javacamerserver.utils.ByteBufferUtil.base64ToFloatArray;


@Component // 添加这个注解
public class VeinProcessor {

    //用来判断是否需要拍照
    private boolean isCameraFlag = false;
    //异步拍照的时候，通过这个变量传递图片路径
    private String cameraName = "";

    private Pointer algHandle;
    private Pointer algHandleNew;
    private static final int XR_VEIN_FEATURE_INFO_SIZE = 512; // 根据实际SDK调整


    private static final int FEATURE_SIZE = 512; // 特征值长度

    private boolean isOk;
    private byte[] chipSendBuf;
    private byte[] chipRecvBuf;
    private boolean openFlag;

    private Pointer[] devList; // 设备列表
    // 全局变量：存储当前设备句柄
    private  Pointer currentDeviceHandle = null;

    private SonixCamera.scDevice[] camerasList;
    private static VeinProcessor instance;
    //构造函数，初始化数组
    public VeinProcessor() {
        //初始化摄像头
        isOk = SonixCamera.INSTANCE.SonixCam_Init();
        //TODO
        //openFlag = false;
        openFlag = true;

        chipSendBuf = new byte[FEATURE_SIZE];
        chipRecvBuf = new byte[FEATURE_SIZE];
    }

    // 提供静态获取实例的方法（可选，用于非Spring环境）
    public static VeinProcessor getInstance() {
        if (instance == null) {
            synchronized (VeinProcessor.class) {
                if (instance == null) {
                    instance = new VeinProcessor();
                }
            }
        }
        return instance;
    }

    //释放资源
    public void release() {
        //初始化摄像头
        //isOk = SonixCamera.INSTANCE.SonixCam_Release();
        //TODO
        //openFlag = false;

        chipSendBuf = null;
        chipRecvBuf = null;
    }

    public SonixCamera.scDevice[] getCamerasList() {
        return camerasList;
    }

    public boolean getIsCameraFlag() {
        return this.isCameraFlag;
        //return true;
    }

    public void setIsCameraFlag(boolean isCameraFlag) {
        this.isCameraFlag = isCameraFlag;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
    }

    // 初始化算法引擎
    @PostConstruct
    public boolean init() throws VeinException, UnsupportedEncodingException {
        PointerByReference pAlgHandle = new PointerByReference();
        int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_Init(pAlgHandle);
        checkError(ret, "初始化失败");
        algHandle = pAlgHandle.getValue();




        //打开摄像头
        // 获取授权码
        byte[] licData = new byte[112];
        IntByReference licLen = new IntByReference(112);
        ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_GetLicCode(algHandle, licData, licLen);
        checkError(ret, "获取授权码失败");


        //通过授权码获取激活码
        //输出数据
        byte[] dstData1= new byte[16 + 1];
        //ShortByReference outLen = new ShortByReference((short) dstData1.length);
        int[] outLen = new int[16];
        outLen[0] = 16;

        //打开设备
        OpenDevice();

        // 获取摄像头设备信息并提取VID和PID
        Map<String, String> deviceInfo = getCameraVIDandPID();

        int decryptRet = XR_BSP_Chip_SM2Decrypt(licData, licLen.getValue(), dstData1, outLen);

        String activationCode = "";
        if (decryptRet == VeinReturnCode.PV_OK) {
            activationCode = new String(dstData1, "UTF-8");

            System.out.println("dstData1: " + activationCode);

            String subStr = activationCode.substring(0, 16);
            System.out.println("subStr: " + subStr);
            activationCode = activationCode.substring(0,16);
        }
        // TODO: 这里应调用HSC芯片解密逻辑
        //String activationCode = version2; // 示例激活码

        // SDK激活
        ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_ActivateVeinSDK(
                algHandle, activationCode.getBytes(), activationCode.length());
        checkError(ret, "激活SDK失败");
        System.out.println("激活SDK成功");
        PointerByReference pAlgHandleNew = new PointerByReference();
        ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_Init(pAlgHandleNew, 3000000);
        if (ret == VeinReturnCode.PV_OK) {
            algHandleNew = pAlgHandleNew.getValue();
            System.out.println("Database initialized, handle: " + algHandleNew);
        }
        checkError(ret, "初始化失败");
        /**
         * 初始化注册器
         * @param pDevHandle SDK的句柄
         * @return 成功返回PV_OK，失败返回错误码
         */
        XRCommonVeinAlgAPI.INSTANCE.XR_Vein_InitEnrollEnv(algHandle);
        return true;
    }

    /**
     * 从设备路径中提取VID和PID
     * @return 包含VID和PID的Map
     */
    private Map<String, String> getCameraVIDandPID() {
        Map<String, String> result = new HashMap<>();

        try {
            // 使用PowerShell精确查询USB Camera设备
            String[] commands = {
                    "powershell",
                    "Get-PnpDevice -Class Camera | Where-Object {$_.FriendlyName -eq 'USB Camera'} | Select-Object -Property InstanceId"
            };

            Process process = Runtime.getRuntime().exec(commands);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            boolean foundDevice = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("USB\\VID_") && line.contains("PID_")) {
                    Pattern pattern = Pattern.compile("VID_([0-9A-F]{4})&PID_([0-9A-F]{4})", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(line);

                    if (matcher.find()) {
                        String vid = matcher.group(1);
                        String pid = matcher.group(2);
                        result.put("VID", vid);
                        result.put("PID", pid);
                        System.out.println("找到USB Camera设备 - VID: " + vid + ", PID: " + pid);
                        foundDevice = true;
                        break;
                    }
                }
            }

            reader.close();

            if (!foundDevice) {
                System.out.println("未找到名为'USB Camera'的设备，尝试其他方法...");
                // 如果精确匹配失败，尝试其他方法
                findUSBCameraByAlternativeMethods(result);
            }

        } catch (Exception e) {
            System.err.println("通过PowerShell获取USB Camera设备信息失败: " + e.getMessage());
            // 尝试其他方法
            findUSBCameraByAlternativeMethods(result);
        }

        // 如果所有方法都失败，使用默认值
        if (!result.containsKey("VID")) {
            result.put("VID", "0C45");
            result.put("PID", "636B");
            System.out.println("使用默认VID和PID: 0C45, 636B");
        }

        return result;
    }

    private void findUSBCameraByAlternativeMethods(Map<String, String> result) {
        try {
            // 方法1: 列出所有摄像头设备，然后筛选
            System.out.println("正在列出所有摄像头设备...");
            String[] commands = {"powershell", "Get-PnpDevice -Class Camera | Select-Object -Property FriendlyName, InstanceId"};

            Process process = Runtime.getRuntime().exec(commands);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            boolean headerPassed = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 跳过表头
                if (line.contains("FriendlyName") && line.contains("InstanceId")) {
                    headerPassed = true;
                    continue;
                }

                if (headerPassed) {
                    System.out.println("发现设备: " + line);

                    // 查找包含"USB Camera"的设备
                    if (line.contains("USB Camera")) {
                        Pattern pattern = Pattern.compile("VID_([0-9A-F]{4})&PID_([0-9A-F]{4})", Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(line);

                        if (matcher.find()) {
                            String vid = matcher.group(1);
                            String pid = matcher.group(2);
                            result.put("VID", vid);
                            result.put("PID", pid);
                            System.out.println("通过列表找到USB Camera设备 - VID: " + vid + ", PID: " + pid);
                            reader.close();
                            return;
                        }
                    }
                }
            }

            reader.close();

            // 方法2: 使用WMIC命令
            System.out.println("尝试使用WMIC命令查找...");
            String[] wmicCommands = {"wmic", "path", "Win32_PnPEntity", "where", "Name='USB Camera'", "get", "DeviceID", "/value"};

            Process wmicProcess = Runtime.getRuntime().exec(wmicCommands);
            BufferedReader wmicReader = new BufferedReader(new InputStreamReader(wmicProcess.getInputStream()));

            String wmicLine;
            while ((wmicLine = wmicReader.readLine()) != null) {
                if (wmicLine.startsWith("DeviceID=")) {
                    String deviceID = wmicLine.substring(9);
                    Pattern pattern = Pattern.compile("VID_([0-9A-F]{4})&PID_([0-9A-F]{4})", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(deviceID);

                    if (matcher.find()) {
                        String vid = matcher.group(1);
                        String pid = matcher.group(2);
                        result.put("VID", vid);
                        result.put("PID", pid);
                        System.out.println("通过WMIC找到USB Camera设备 - VID: " + vid + ", PID: " + pid);
                        wmicReader.close();
                        return;
                    }
                }
            }

            wmicReader.close();

        } catch (Exception e) {
            System.err.println("通过备选方法查找USB Camera失败: " + e.getMessage());
        }
    }

    // 使用 @PreDestroy 清理资源
    @PreDestroy
    public void destroy() {
        release();
    }
    // 注册流程
    public void initEnrollEnv() throws IOException {
        /**
         * 初始化注册器
         * @param pDevHandle SDK的句柄
         * @return 成功返回PV_OK，失败返回错误码
         */
        System.out.println("开始初始化注册器");
        chipSendBuf = new byte[FEATURE_SIZE];
        chipRecvBuf = new byte[FEATURE_SIZE];
        XRCommonVeinAlgAPI.INSTANCE.XR_Vein_InitEnrollEnv(algHandle);
        System.out.println("结束初始化注册器");
    }

    // 注册流程
    public byte[] enrollProcessBat(String[] imagePaths) throws IOException {
        XRCommonVeinAlgAPI.INSTANCE.XR_Vein_InitEnrollEnv(algHandle);

        byte[] featBuf = new byte[XR_VEIN_FEATURE_INFO_SIZE];
        IntByReference enrollStep = new IntByReference(0);

        for (String imgPath : imagePaths) {
            BufferedImage img = ImageIO.read(new File(imgPath));
            byte[] grayImg = convertToGray(img);

            sPalmInfo palmInfo = new sPalmInfo();
            byte[] highBright = new byte[112];
            IntByReference capTip = new IntByReference();

            int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_TryEnroll(
                    algHandle, grayImg, img.getHeight(), img.getWidth(),
                    1, enrollStep, capTip, palmInfo, highBright);

            handleCapResult(ret, capTip.getValue());

            if (ret ==0 && enrollStep.getValue() == 3) {
                break;
            }
        }

        // 完成注册
        //byte[] roiImg = new byte[160*160];
        //IntByReference roiLen = new IntByReference(160*160);
        //IntByReference featLen = new IntByReference(XR_VEIN_FEATURE_INFO_SIZE);
        //
        //int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_FinishEnroll(
        //        algHandle, roiImg, roiLen, featBuf, featLen);

        // 准备 roi_img_buf 参数

        int roiImgWidth = 160;
        int roiImgHeight = 160;
        int roiImgBufSize = roiImgWidth * roiImgHeight;
        byte[] roiImgBuf = new byte[roiImgBufSize];

        // 准备 roi_len 参数
        IntByReference roiLen = new IntByReference(roiImgBufSize);

        // 准备 feat_len 参数
        int featLen = FeatureConstants.XR_VEIN_FEATURE_INFO_SIZE;
        IntByReference featLenRef = new IntByReference(featLen);

        // 准备 feat_buf 参数
        //byte[] featBuf2 = new byte[featLen];

        // 调用方法
        int ret = 0;//XRCommonVeinAlgAPI.INSTANCE.XR_Vein_FinishEnroll(algHandle, roiImgBuf, roiLen, featBuf, featLenRef);

        if (ret != 0 ) {
            return null;
        }

        //把特征值保存到本地
        try (FileOutputStream fos = new FileOutputStream("reg_img.bin")) {
            int actualRoiLen = roiLen.getValue();
            fos.write(roiImgBuf, 0, actualRoiLen);
            System.out.println("注册成功success");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return featBuf;
    }


    /*public byte[] enrollProcess() throws IOException {
        XRCommonVeinAlgAPI.INSTANCE.XR_Vein_InitEnrollEnv(algHandle);

        byte[] featBuf = new byte[XR_VEIN_FEATURE_INFO_SIZE];
        IntByReference enrollStep = new IntByReference(0);

        int i = 0;
        while (i <= 50) {
            if (i >= 50) {
                break;
            }

            if (!CameraState.isCameraFlag()) {
                i++;

                System.out.println(CameraState.getCameraName());
                BufferedImage img = ImageIO.read(new File("captured_images/"+CameraState.getCameraName()));
                byte[] grayImg = convertToGray(img);

                sPalmInfo palmInfo = new sPalmInfo();
                byte[] highBright = new byte[112];
                IntByReference capTip = new IntByReference();

                int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_TryEnroll(
                        algHandle, grayImg, img.getHeight(), img.getWidth(),
                        1, enrollStep, capTip, palmInfo, highBright);

                handleCapResult(ret, capTip.getValue());

                if (ret ==0 && enrollStep.getValue() == 3) {
                    break;
                }
                CameraState.setCameraFlag(true);
                //isCameraFlag = true;
            }
        }

        // 完成注册
        // 准备 roi_img_buf 参数
        int roiImgWidth = 160;
        int roiImgHeight = 160;
        int roiImgBufSize = roiImgWidth * roiImgHeight;
        byte[] roiImgBuf = new byte[roiImgBufSize];

        // 准备 roi_len 参数
        IntByReference roiLen = new IntByReference(roiImgBufSize);

        // 准备 feat_len 参数
        int featLen = FeatureConstants.XR_VEIN_FEATURE_INFO_SIZE;
        IntByReference featLenRef = new IntByReference(featLen);

        // 准备 feat_buf 参数
        //byte[] featBuf2 = new byte[featLen];

        // 调用方法
        int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_FinishEnroll(algHandle, roiImgBuf, roiLen, featBuf, featLenRef);

        if (ret != 0 ) {
            return null;
        }

        //把特征值保存到本地
        try (FileOutputStream fos = new FileOutputStream("reg_img.bin")) {
            int actualRoiLen = roiLen.getValue();
            fos.write(roiImgBuf, 0, actualRoiLen);
            System.out.println("注册成功success");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return featBuf;
    }*/
    // 识别流程
    //public float recognize(byte[] regFeat, String testImagePath) throws IOException {
    //    BufferedImage img = ImageIO.read(new File(testImagePath));
    //    byte[] grayImg = convertToGray(img);
    //
    //    byte[] testFeat = new byte[XR_VEIN_FEATURE_INFO_SIZE];
    //    IntByReference featLen = new IntByReference(XR_VEIN_FEATURE_INFO_SIZE);
    //    IntByReference capTip = new IntByReference();
    //    TDXVeinAlg.PalmInfo palmInfo = new TDXVeinAlg.PalmInfo();
    //    ByteByReference highBright = new ByteByReference();
    //
    //    int ret = TDXVeinAlg.INSTANCE.TDX_Vein_GrabFeatureFromFullImg(
    //            algHandle, grayImg, img.getHeight(), img.getWidth(),
    //            0, testFeat, featLen, capTip, palmInfo, highBright);
    //
    //    if (ret != 0) return -1;
    //
    //    FloatByReference score = new FloatByReference();
    //    TDXVeinAlg.INSTANCE.TDX_Vein_CalcFeatureDist(
    //            regFeat, XR_VEIN_FEATURE_INFO_SIZE,
    //            testFeat, XR_VEIN_FEATURE_INFO_SIZE, score);
    //
    //    return score.getValue();
    //}
    public Map<String,Object> enrollProcessNew(BufferedImage image) throws IOException {
        Map<String,Object> map = new HashMap<>();
        try {
            byte[] featBuf = new byte[XR_VEIN_FEATURE_INFO_SIZE];
            System.out.println("开始解析数据");
            IntByReference enrollStep = new IntByReference(0);
            Integer index = null;
            // 本地资源路径
            String msg = "";
            byte[] grayImg = convertToGray(image);
            sPalmInfo palmInfo = new sPalmInfo();
            byte[] highBright = new byte[256];
            IntByReference capTip = new IntByReference();
            // 在关键代码段添加内存日志（单位MB）
            Runtime rt = Runtime.getRuntime();
            System.out.printf(
                    "[MEM] Total: %.2fMB, Used: %.2fMB, Free: %.2fMB, Max: %.2fMB\n",
                    rt.totalMemory()/1024.0/1024,
                    (rt.totalMemory() - rt.freeMemory())/1024.0/1024,
                    rt.freeMemory()/1024.0/1024,
                    rt.maxMemory()/1024.0/1024
            );

            int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_TryEnroll(
                    algHandle, grayImg,
                    image.getHeight(),
                    image.getWidth(),
                    1,
                    enrollStep,
                    capTip,
                    palmInfo,
                    highBright);
            handleCapResult(ret, capTip.getValue());
            switch ( capTip.getValue()) {
                case EnrollTip.PV_TIP_INPUT_PALM: msg = "Please insert your palm"; break;
                case EnrollTip.PV_TIP_MOVE_FARAWAY: msg = "Please move your palm further away."; break;
                case EnrollTip.PV_TIP_MOVE_CLOSER: msg = "Please move your palm closer."; break;
                case EnrollTip.PV_TIP_INVALID_BRIGHT: msg = "Abnormal brightness."; break;
                case EnrollTip.PV_TIP_KEEP_PALM_STABLE: msg = "Please keep your palm stable."; break;
                case EnrollTip.PV_TIP_KEEP_PALM_DIRECTION: msg = "Please keep your palm facing the correct direction."; break;
                case EnrollTip.PV_TIP_MOVE_PALM_DOWN: msg = "Please move your palm down a little bit."; break;
                case EnrollTip.PV_TIP_MOVE_PALM_UP: msg = "Please move your palm up a little bit."; break;
                case EnrollTip.PV_TIP_MOVE_PALM_LEFT: msg = "Please move your palm to the left a little bit."; break;
                case EnrollTip.PV_TIP_MOVE_PALM_RIGHT: msg = "Please move your palm to the right a little bit."; break;
                case EnrollTip.PV_TIP_CAP_SUCCESS: msg = "Please keep your palm steady."; break;
                case EnrollTip.PV_TIP_ENROLL_FINISH: msg = "Registration successful."; break;
                // ...other prompt processing
                default: msg = "Photo not qualified, please adjust your pose. Unknown prompt code: " + capTip.getValue();
            }
            // 完成注册
            // 准备 roi_img_buf 参数


            // 准备 feat_buf 参数
            //byte[] featBuf2 = new byte[featLen];



            map.put("msg",msg);
            map.put("index",index);
            map.put("sPalmInfo",palmInfo);
            // 在 enrollProcessNew 方法中修改调用逻辑
            if (ret == 0 && enrollStep.getValue() == 3) {
                System.out.println("获取特征值");
                // 1. 计算 ROI 图像缓冲区大小（文档说明默认 160x160，增加 10% 安全边界）
                int baseRoiSize = 160 * 160; // 文档明确默认大小
                int roiImgBufSize = (int) (baseRoiSize * 1.1); // 安全边界

                // 2. 分配对齐的原生内存（JNA 的 Memory 自动按系统要求对齐）
                Memory roiImgMemory = new Memory(roiImgBufSize);
                Memory featMemory = new Memory(FeatureConstants.XR_VEIN_FEATURE_INFO_SIZE); // 特征缓冲区

                // 3. 初始化内存（避免脏数据导致的不可预期错误）
                roiImgMemory.clear();
                featMemory.clear();

                // 4. 准备长度参数
                IntByReference roiLen = new IntByReference(roiImgBufSize); // 输入缓冲区大小
                IntByReference featLenRef = new IntByReference(FeatureConstants.XR_VEIN_FEATURE_INFO_SIZE);

                // 5. 调用 C 接口（传递原生内存指针）
                int rets = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_FinishEnroll(
                        algHandle,
                        roiImgMemory,    // 传递 Memory 对象（自动转为 C 指针）
                        roiLen,
                        featMemory,      // 特征缓冲区同样用 Memory
                        featLenRef
                );

                // 6. 检查返回结果
                if (rets != 0) {
                    System.err.println("FinishEnroll 失败，错误码: " + rets);
                    return map;
                }

                // 7. 从原生内存中读取结果（若需要）
                // 7.1 读取 ROI 图像数据（实际长度由 roiLen 返回）
                byte[] roiImgResult = new byte[roiLen.getValue()];
                roiImgMemory.read(0, roiImgResult, 0, roiLen.getValue());

                // 7.2 读取特征值数据（实际长度由 featLenRef 返回）
                byte[] featBufResult = new byte[featLenRef.getValue()];
                featMemory.read(0, featBufResult, 0, featLenRef.getValue());
                /*try {
                    Path path = Paths.get(featBufResult.toString().substring(0,5)+".bin");
                    try (OutputStream os = Files.newOutputStream(path,
                            StandardOpenOption.CREATE,    // 创建文件（如果不存在）
                            StandardOpenOption.TRUNCATE_EXISTING)) { // 清空已存在内容

                        os.write(featBufResult); // 写入字节数组

                        System.out.println("文件保存成功: " + path.toAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("文件保存失败: " + e.getMessage());
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }*/
                // 后续处理（如转 Base64）
                String base64 = encodeToBase64(featBufResult);
                System.out.println("注册成功success");
                map.put("baseFeature", base64);
                map.put("index", 1);
                return map;
            }else {
                return map;
            }
            /*if (capTip.getValue() != 100 ) {
                return map;
            }*/
            //把特征值保存到本地
//        try (FileOutputStream fos = new FileOutputStream("reg_img.bin")) {
//            int actualRoiLen = roiLen.getValue();
//            fos.write(roiImgBuf, 0, actualRoiLen);
//            System.out.println("注册成功success");
//            index=1;
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        }catch (Exception e){
            e.printStackTrace();
        }
        return map;
    }

    //扫描获取特征值
    public Map<String, Object> scanningComparison(BufferedImage image) throws IOException {
        Map<String, Object> map = new HashMap<>();
        try {
            // 1. 准备灰度图数据（使用原生内存存储，保证对齐）
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            int grayImgSize = imgWidth * imgHeight; // 灰度图每个像素1字节
            Memory grayImgMemory = new Memory(grayImgSize); // 分配对齐内存
            // 转换图像为灰度并写入原生内存
            byte[] grayImg = convertToGray(image);
            grayImgMemory.write(0, grayImg, 0, grayImgSize); // 复制到原生内存

            // 2. 准备特征缓冲区（原生内存，避免byte[]对齐问题）
            int featBufSize = FeatureConstants.XR_Buf_FEATURE_INFO_SIZE;
            // 增加10%安全边界，防止缓冲区不足
            int safeFeatBufSize = (int) (featBufSize * 1.1);
            Memory featBufMemory = new Memory(safeFeatBufSize);
            featBufMemory.clear(); // 初始化内存，避免脏数据

            // 3. 准备高亮度数据缓冲区（原生内存）
            int highBrightSize = 256; // 原数组大小，保持一致
            Memory highBrightMemory = new Memory(highBrightSize);
            highBrightMemory.clear();

            // 4. 其他参数
            System.out.println("开始解析数据");
            IntByReference enrollStep = new IntByReference(0);
            Integer index = null;
            String msg = "开始比对";
            sPalmInfo palmInfo = new sPalmInfo();
            IntByReference capTip = new IntByReference();
            IntByReference featLenRef = new IntByReference(safeFeatBufSize); // 输入缓冲区大小

            // 5. 调用C接口（传递原生内存指针）
            int retNew = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_GrabFeatureFromFullImg(
                    algHandle,
                    grayImgMemory,   // 灰度图原生内存指针
                    imgHeight,
                    imgWidth,        // 确认宽高顺序与C接口一致
                    1,               // 原参数中的1，根据文档确认含义
                    featBufMemory,   // 特征缓冲区原生内存指针
                    featLenRef,
                    capTip,
                    palmInfo,
                    highBrightMemory // 高亮度数据原生内存指针
            );

            // 6. 处理返回结果
            if (retNew != 0) {
                System.err.println("GrabFeature 失败，错误码: " + retNew);
                map.put("msg", "Comparison failed, error code:" + retNew);
                return map;
            }

            // 7. 从原生内存读取特征值（实际长度由featLenRef返回）
            int actualFeatLen = featLenRef.getValue();
            byte[] featBuf = new byte[actualFeatLen];
            featBufMemory.read(0, featBuf, 0, actualFeatLen);

            // 后续处理
            msg = "Comparison completed";
            String base64 = encodeToBase64(featBuf);
            System.out.println("特征值: " + base64);
            map.put("sPalmInfo",palmInfo);
            map.put("baseFeature", base64);
            map.put("index", 1);
            map.put("msg", msg);

        } catch (Exception e) {
            e.printStackTrace();
            map.put("msg", "Comparison exception:" + e.getMessage());
        }
        return map;
    }

    // 图像转灰度
    private byte[] convertToGray(BufferedImage img) {
        /*int w = img.getWidth();
        int h = img.getHeight();
        byte[] gray = new byte[w*h];

        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y*w + x] = (byte)(0.299*r + 0.587*g + 0.114*b);
            }
        }
        return gray;*/
        int w = img.getWidth();
        int h = img.getHeight();
        byte[] gray = new byte[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // 添加四舍五入取整
                int grayValue = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                gray[y * w + x] = (byte) grayValue;
            }
        }
        return gray;
    }

    // 处理采集提示
    private void handleCapResult(int ret, int tipCode) {
        switch (tipCode) {
            case EnrollTip.PV_TIP_INPUT_PALM: System.out.println("请放入手掌"); break;
            case EnrollTip.PV_TIP_MOVE_FARAWAY: System.out.println("请将手掌远离一些\n"); break;
            case EnrollTip.PV_TIP_MOVE_CLOSER: System.out.println("请将手掌靠近一些\n"); break;
            case EnrollTip.PV_TIP_INVALID_BRIGHT: System.out.println("亮度异常\n"); break;
            case EnrollTip.PV_TIP_KEEP_PALM_STABLE: System.out.println("请保持手掌姿势稳定\n"); break;
            case EnrollTip.PV_TIP_KEEP_PALM_DIRECTION: System.out.println("请保持正确的手掌朝向\n"); break;
            case EnrollTip.PV_TIP_MOVE_PALM_DOWN: System.out.println("请将手掌靠下一些\n"); break;
            case EnrollTip.PV_TIP_MOVE_PALM_UP: System.out.println("请将手掌靠上一些\n"); break;
            case EnrollTip.PV_TIP_MOVE_PALM_LEFT: System.out.println("请将手掌靠左一些\n"); break;
            case EnrollTip.PV_TIP_MOVE_PALM_RIGHT: System.out.println("请将手掌靠右一些\n"); break;
            case EnrollTip.PV_TIP_CAP_SUCCESS: System.out.println("采集成功\n"); break;
            case EnrollTip.PV_TIP_ENROLL_FINISH: System.out.println("注册成功\n"); break;
            // ...其他提示处理
            default: System.out.println("未知提示码: "+tipCode);
        }
    }

    //打开摄像头
    //public int openDevice() {
    //    if (!isOk) {
    //        return VeinReturnCode.PV_ERR_USB_INIT;
    //    }
    //
    //    int de
    //}


    //打开摄像头
    public int OpenDevice() {
        if (!isOk) {
            return VeinReturnCode.PV_ERR_USB_INIT;
        }

        // 创建用于接收设备数量的引用
        IntByReference devCount = new IntByReference();

        // 初始化设备列表数组
        //devList = new Pointer[1]; // 最大设备数量

        // 声明并初始化数组（例如，最多支持10个设备）
        //int maxDevices = 10;
        //SonixCamera.scDeviceInfo[] devList = new SonixCamera.scDeviceInfo[maxDevices];
        //
        //// 初始化每个数组元素
        //for (int i = 0; i < maxDevices; i++) {
        //    devList[i] = new SonixCamera.scDeviceInfo();
        //}


        // 创建单个结构体实例
        SonixCamera.scDevice prototype = new SonixCamera.scDevice();

        // 使用toArray方法创建连续内存的数组
        //SonixCamera.scDevice[]
        camerasList = (SonixCamera.scDevice[]) prototype.toArray(10);

        // 枚举设备
        boolean res = SonixCamera.INSTANCE.SonixCam_EnumCameras(devCount, camerasList, 1);

        if (!res) {
            System.out.println("枚举设备失败");
            return VeinReturnCode.PV_ERR_NO_DEVICE;
        }

        if (devCount.getValue() == 0) {
            System.out.println("未找到摄像头设备");
            return VeinReturnCode.PV_ERR_NO_DEVICE;
        } else {
            int count = devCount.getValue();
            System.out.println("找到 " + count + " 个设备");

            // 保存第一个设备的句柄到全局变量
            currentDeviceHandle = camerasList[0].handle;
            System.out.println("已保存第一个设备句柄: " + currentDeviceHandle);

            openFlag = true;
            System.out.println("找到摄像头设备");

            return VeinReturnCode.PV_OK;
        }




    }

    //解密
    public int XR_BSP_Chip_SM2Decrypt(byte[] inBuf, int inLen, byte[] outBuf, int[] outLen) throws VeinException {
        if (inLen < 97 || inLen > 246) {
            return VeinReturnCode.PV_ERR_PARAM;
        }

        if (outLen == null || outLen[0] < 1) {
            return VeinReturnCode.PV_ERR_PARAM;
        }

        int crcVal = 0;
        int cmdLen = inLen + 5;

        chipSendBuf[0] = 0x00;
        chipSendBuf[1] = (byte) ((cmdLen >> 8) & 0xff);
        chipSendBuf[2] = (byte) (cmdLen & 0xff);

        chipSendBuf[3] = 0x00;
        chipSendBuf[4] = (byte) 0xE1;
        chipSendBuf[5] = 0x01;
        chipSendBuf[6] = (byte) ((inLen >> 8) & 0xff);
        chipSendBuf[7] = (byte) (inLen & 0xff);

        for (int i = 0; i < inLen; i++) {
            chipSendBuf[i + 8] = inBuf[i];
        }

        crcVal = XR_CalcCRC16_CCITT(Arrays.copyOfRange(chipSendBuf, 3, 3 + cmdLen), cmdLen);

        chipSendBuf[inLen + 8] = (byte) ((crcVal >> 8) & 0x00ff);
        chipSendBuf[inLen + 9] = (byte) (crcVal & 0x00ff);





        int ret = send_data(chipSendBuf, inLen + 10);
        if (ret != VeinReturnCode.PV_OK) {
            return ret;
        }

        Sleep(100);

        int pkt_len = outLen[0] + 7;
        ret = recv_data(chipRecvBuf, pkt_len);

        if (ret == VeinReturnCode.PV_OK) {
            System.arraycopy(chipRecvBuf, 3, outBuf, 0, outLen[0]);
        }

        return ret;

    }


    // 替换原代码中的模拟方法
    private int XR_CalcCRC16_CCITT(byte[] pdata, int len) {
        final int[] CRC16_CCIT_Tab = {
                0x0000, 0x1189, 0x2312, 0x329b, 0x4624, 0x57ad, 0x6536, 0x74bf,
                0x8c48, 0x9dc1, 0xaf5a, 0xbed3, 0xca6c, 0xdbe5, 0xe97e, 0xf8f7,
                0x1081, 0x0108, 0x3393, 0x221a, 0x56a5, 0x472c, 0x75b7, 0x643e,
                0x9cc9, 0x8d40, 0xbfdb, 0xae52, 0xdaed, 0xcb64, 0xf9ff, 0xe876,
                0x2102, 0x308b, 0x0210, 0x1399, 0x6726, 0x76af, 0x4434, 0x55bd,
                0xad4a, 0xbcc3, 0x8e58, 0x9fd1, 0xeb6e, 0xfae7, 0xc87c, 0xd9f5,
                0x3183, 0x200a, 0x1291, 0x0318, 0x77a7, 0x662e, 0x54b5, 0x453c,
                0xbdcb, 0xac42, 0x9ed9, 0x8f50, 0xfbef, 0xea66, 0xd8fd, 0xc974,
                0x4204, 0x538d, 0x6116, 0x709f, 0x0420, 0x15a9, 0x2732, 0x36bb,
                0xce4c, 0xdfc5, 0xed5e, 0xfcd7, 0x8868, 0x99e1, 0xab7a, 0xbaf3,
                0x5285, 0x430c, 0x7197, 0x601e, 0x14a1, 0x0528, 0x37b3, 0x263a,
                0xdecd, 0xcf44, 0xfddf, 0xec56, 0x98e9, 0x8960, 0xbbfb, 0xaa72,
                0x6306, 0x728f, 0x4014, 0x519d, 0x2522, 0x34ab, 0x0630, 0x17b9,
                0xef4e, 0xfec7, 0xcc5c, 0xddd5, 0xa96a, 0xb8e3, 0x8a78, 0x9bf1,
                0x7387, 0x620e, 0x5095, 0x411c, 0x35a3, 0x242a, 0x16b1, 0x0738,
                0xffcf, 0xee46, 0xdcdd, 0xcd54, 0xb9eb, 0xa862, 0x9af9, 0x8b70,
                0x8408, 0x9581, 0xa71a, 0xb693, 0xc22c, 0xd3a5, 0xe13e, 0xf0b7,
                0x0840, 0x19c9, 0x2b52, 0x3adb, 0x4e64, 0x5fed, 0x6d76, 0x7cff,
                0x9489, 0x8500, 0xb79b, 0xa612, 0xd2ad, 0xc324, 0xf1bf, 0xe036,
                0x18c1, 0x0948, 0x3bd3, 0x2a5a, 0x5ee5, 0x4f6c, 0x7df7, 0x6c7e,
                0xa50a, 0xb483, 0x8618, 0x9791, 0xe32e, 0xf2a7, 0xc03c, 0xd1b5,
                0x2942, 0x38cb, 0x0a50, 0x1bd9, 0x6f66, 0x7eef, 0x4c74, 0x5dfd,
                0xb58b, 0xa402, 0x9699, 0x8710, 0xf3af, 0xe226, 0xd0bd, 0xc134,
                0x39c3, 0x284a, 0x1ad1, 0x0b58, 0x7fe7, 0x6e6e, 0x5cf5, 0x4d7c,
                0xc60c, 0xd785, 0xe51e, 0xf497, 0x8028, 0x91a1, 0xa33a, 0xb2b3,
                0x4a44, 0x5bcd, 0x6956, 0x78df, 0x0c60, 0x1de9, 0x2f72, 0x3efb,
                0xd68d, 0xc704, 0xf59f, 0xe416, 0x90a9, 0x8120, 0xb3bb, 0xa232,
                0x5ac5, 0x4b4c, 0x79d7, 0x685e, 0x1ce1, 0x0d68, 0x3ff3, 0x2e7a,
                0xe70e, 0xf687, 0xc41c, 0xd595, 0xa12a, 0xb0a3, 0x8238, 0x93b1,
                0x6b46, 0x7acf, 0x4854, 0x59dd, 0x2d62, 0x3ceb, 0x0e70, 0x1ff9,
                0xf78f, 0xe606, 0xd49d, 0xc514, 0xb1ab, 0xa022, 0x92b9, 0x8330,
                0x7bc7, 0x6a4e, 0x58d5, 0x495c, 0x3de3, 0x2c6a, 0x1ef1, 0x0f78
        };

        int crc = 0x0000;
        for (int i = 0; i < len; i++) {
            crc = (crc >>> 8) ^ CRC16_CCIT_Tab[(crc ^ (pdata[i] & 0xFF)) & 0xFF];
        }
        return crc;
    }

    private int send_data(byte[] data_buf, int param_len) {
        if (!isOk) {
            return VeinReturnCode.PV_ERR_USB_INIT;
        }

        if (!openFlag) {
            return VeinReturnCode.PV_ERR_OPEN_DEV;
        }

        boolean res;
        byte[] trig_flag = new byte[1];
        byte len = (byte) (param_len & 0xff);

        Sleep(100);

        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterRead(camerasList, 0x0B07, trig_flag, 1);
        if (!res) {
            System.out.println("read trig flag failed");
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterWrite(camerasList, 0xB06, new byte[]{len}, 1);
        if (!res) {
            System.out.printf("write len failed: %b\n", res);
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterWrite(camerasList, 0xB08, data_buf, len & 0xFF);
        if (!res) {
            System.out.printf("write data failed: %b\n", res);
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        trig_flag[0] = 0x00;
        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterWrite(camerasList, 0xB07, trig_flag, 1);
        if (!res) {
            System.out.printf("write trigger failed: %b\n", res);
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        Sleep(100);

        return VeinReturnCode.PV_OK;
    }
    //Web比对接口
    public Float comparisonPalmWeb(byte[] featBuf1,byte[] featBuf2) throws IOException {

        try {
            // 假设你已经有两个String格式的掌静脉特征值
            //String veinFeature1 = "IAEBAAAAAABq/gEMfAEz+ZT/l/8P/w4ATQCu/sP09vLF/4QFMwXpBykBBwAwArgBUgN/Ac7/YwDaAZ4CD/1BAuUB8QANAQEDDgVnCJwI+gJpAzf/3Ps8+bUBU/j5Bf7/kfr4A0MCEQBiDMr2AQrV/OH0HvqzAjsEkAk6AQMBYAjv/C4ABPpQ/QsDfgQKAhIAIATF/439Dv38ARX9SvwXCNf9dQiBBeL66gWXA58A6vxuCFwDVP1uA8D8cQLg/4L+wAal/uwERQDZ+TcDAwEG/cwEyAHYAmoGaQHrATsCcwEw/3UDVQWDBbsEcPsJAt8A/ATVAwIFCP9mALLzm/o+/1wHmARN+7MGZv+IAEUJrge+AOn6Lwjx+q8ExP7x+af2eQtM8CMC2AF2/q0FxgPe/jT+Nfrn+TUIdgCf+AMBOv50Aq35zAFM+2IAZvoj+ikCMP49/LkE+Pd4+Kv+9APdAZ74QPfi/hwBq/xx8hj/lfnz/rn9E/5l+kL4QPeCBRQNbvcJBE7/OP4ZA0IB2ADGAtH3qf9jCOkCXPuZ+oH5WALB/S8FkP9+/hL+lP+P/rj+G/1O+Ib5mfvG/5f9FABU9ucAcAhC/dP36Psk/pL7Q/nLBZb9FAK5Arb+hgI9BfcDh/13+uP/wwfM/Sv+AgI6+0MJyP1d/okA2vbeAh3+yfs=";
            //String veinFeature2 = "IAEBAAAAAAD6B1wJQgVp/SD5CAJD/10BRP0Y/bH13/LO/iEHegeoB4r8FP7mAP7+tAOH/h4A6v1p/T0DnAQxAsQHLQhGAWj8QAVeAqoFIQM9Brj/h/hn+ab/OP0VBrj8LwPSAVj89wDLDEf9PQu6/rTxFvZtAeX/WwloAx4DNwL8/HMBTv7H/XECoQcqAtL9pgUTBUb/qwFJ/psADf8hCeb4JATIA/n8VAZPAgEA0/z2BV8DxQRQAij7mgHwAbX53wYoACkAV/3g+Ef9bv5U/W0IWf+C/i7/RQIf/1AASAVt/04BXAHsBKABB/6UA1T9kgFABHYBU/ykBWX0DvtQA3YCIf9J+yQKvgAqAAQHAwUW/WL7PgixAJz/9/iO+Sf6LwhG+3UCVQItAJQFGARh//L78Pu58/gBkf8d9jAG8f7YBU/5iQBO/O7/qfw2AW3/Mf3g/XMHZvb2+mz+/gdFBqb0S/aH/44BIvpf+Kz9S/cW/fz5zgFp+AT9ZvNPBJYErPpsAkQA4PsxCvD8nv1lAvj9dQCeBIUEzvw3+Bj48wHY/GgDgP+L+Uz89QGq/7/48Po2+rD2GfMa++P/+P2Y/fb//QohBJD2jf33/Ab4IvdkBBP8bAf3AFUBAfyeAnEAIP6u+gUE1QUH+079gwE5ANgIegP0AiQAWvoVAur9/vw=";

            // 将String转换为byte数组
            //featBuf1 = Base64.getDecoder().decode(veinFeature1);
            //featBuf2 = Base64.getDecoder().decode(veinFeature2);
            //float[] featBuf1 =veinFeature1;
            //float[] featBuf2 =byteArrayToFloatArray(featBuf2s);
            //System.out.println(featBuf1.length);
            // 创建用于存储距离结果的float数组
            //WriteBytesToFile(featBuf1);
            // 调用特征比对
            FloatByReference score = new FloatByReference();
            //score.setValue(0.65F);
            // 调用C函数进行特征比对
            int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_CalcFeatureDist(
                    featBuf1, 1036,
                    featBuf2, 1036,
                    score
            );
            float value = score.getValue();
            // 检查返回值并处理结果
//            if (ret == PV_OK) {
//                System.out.println("特征比对成功，距离值: " +value);
//                if (value >= 0.95f) {
//                    System.out.println("特征匹配，超过建议阈值0.95"+value);
//                } else {
//                    System.out.println("特征不匹配，低于建议阈值0.95"+value);
//                }
//                return score.getValue();
//            } else {
//                System.out.println("特征比对失败，错误码: " + ret+value);
//                return value;
//            }
            return value;
        }catch (Exception e){
            e.printStackTrace();
        }
        return 0F;
    }
    //安卓比对接口
    public Float comparisonPalm(float[] featBuf1,byte[] featBuf2s) throws IOException {

        try {
            // 假设你已经有两个String格式的掌静脉特征值
//            veinFeature1 = "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";
//            veinFeature2 = "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";

            // 将String转换为byte数组
            //byte[] featBuf1 = Base64.getDecoder().decode(veinFeature1);
            //byte[] featBuf2 = Base64.getDecoder().decode(veinFeature2);
            //float[] featBuf1 =veinFeature1;
            float[] featBuf2 =byteArrayToFloatArray(featBuf2s);
            //System.out.println(featBuf1.length);
            // 创建用于存储距离结果的float数组
            // 调用特征比对
            FloatByReference score = new FloatByReference();
            //score.setValue(0.65F);
            // 调用C函数进行特征比对
            int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Vein_CalcFeatureDistByFloat(
                    featBuf1, 512,
                    featBuf2, 512,
                    score
            );
            float value = score.getValue();
            // 检查返回值并处理结果
//            if (ret == PV_OK) {
//                System.out.println("特征比对成功，距离值: " +value);
//                if (value >= 0.95f) {
//                    System.out.println("特征匹配，超过建议阈值0.95"+value);
//                } else {
//                    System.out.println("特征不匹配，低于建议阈值0.95"+value);
//                }
//                return score.getValue();
//            } else {
//                System.out.println("特征比对失败，错误码: " + ret+value);
//                return value;
//            }
            return value;
        }catch (Exception e){
            e.printStackTrace();
        }
        return 0F;
    }
    private int recv_data(byte[] data_buf, int param_len) {
        if (!isOk) {
            return VeinReturnCode.PV_ERR_USB_INIT;
        }

        if (!openFlag) {
            return VeinReturnCode.PV_ERR_OPEN_DEV;
        }

        boolean res;
        byte[] trig_flag = new byte[1];
        byte len = (byte) (param_len & 0xff);

        Sleep(100);
        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterRead(camerasList, 0x0B07, trig_flag, 1);
        if (!res) {
            System.out.println("read trig flag failed");
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterWrite(camerasList, 0xB06, new byte[]{len}, 1);
        if (!res) {
            System.out.printf("read len failed: %b\n", res);
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        trig_flag[0] = 0x01;
        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterWrite(camerasList, 0xB07, trig_flag, 1);
        if (!res) {
            System.out.printf("start read failed: %b\n", res);
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        Sleep(200);

        res = SonixCamera.INSTANCE.SonixCam_AsicRegisterRead(camerasList, 0xB08, data_buf, len & 0xFF);
        if (!res) {
            System.out.printf("read data failed: %b\n", res);
            return VeinReturnCode.PV_ERR_USB_TRANSFER;
        }

        return VeinReturnCode.PV_OK;
    }

    // 修正后的 Sleep 方法
    private void Sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // 恢复中断状态
            Thread.currentThread().interrupt();
            // 记录日志或进行其他处理
            System.err.println("Sleep interrupted: " + e.getMessage());
        }
    }


    // 错误处理
    private void checkError(int retCode, String message) throws VeinException {
        if (retCode != VeinReturnCode.PV_OK) {
            throw new VeinException(message + " (错误码: " + retCode + ")");
        }
    }
    public static void deleteImage(String imagePath) {
        Path path = Paths.get(imagePath);

        // 检查文件是否存在
        if (!Files.exists(path)) {
            System.out.println("文件不存在: " + imagePath);
            return;
        }

        // 确保路径是常规文件
        if (!Files.isRegularFile(path)) {
            System.out.println("路径不是常规文件: " + imagePath);
            return;
        }

        try {
            Files.delete(path);
            System.out.println("删除成功: " + imagePath);
        } catch (NoSuchFileException e) {
            System.out.println("文件不存在，可能已被删除: " + imagePath);
        } catch (DirectoryNotEmptyException e) {
            System.out.println("路径是目录且不为空: " + imagePath);
        } catch (AccessDeniedException e) {
            System.err.println("拒绝访问，文件可能被占用或无权限: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("删除失败: " + e.getMessage());
        }
    }

    /*将字节写入到bin文件*/
    public void WriteBytesToFile (byte[] featBuf) {
        // 指定输出文件路径
        String filePath =new Date().getTime()+ "output.bin";

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(featBuf); // 将整个字节数组写入文件
            System.out.println("字节已成功写入 " + filePath);
        } catch (IOException e) {
            System.err.println("写入文件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //新增接口
// 添加用户
    public boolean addUser(int userId,Integer type, String feature) {
        if (algHandleNew == null) {
            System.err.println("Database not initialized");
            return false;
        }

        if (userId == 0) {
            System.err.println("User ID cannot be 0");
            return false;
        }

        if (feature == null) {
            System.err.println("Feature data must be initialized");
            return false;
        }
        if (type!=null&&type==1){
            byte[] bytes = robustBase64Decode(feature);
            byte[] newByte = convertWindowsFeatureToInt16(bytes);
            if (newByte == null) {
                System.err.println("Feature data must be initialized");
                return false;
            }
            int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_AddUser(algHandleNew, userId,newByte , 2096);
            if (ret == VeinReturnCode.PV_OK) {
                System.out.println("User " + userId + " added successfully");
                return true;
            } else {
                System.err.println("Failed to add user " + userId + ". Error code: " + ret);
                return false;
            }
        }else if (type!=null&&type==2){
            float[] bytes = base64ToFloatArray(feature);
            byte[] newByte = convertFloatFeatureToInt16(bytes);
            if (newByte == null) {
                System.err.println("Feature data must be initialized");
                return false;
            }
            int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_AddUser(algHandleNew, userId,newByte , 2096);
            if (ret == VeinReturnCode.PV_OK) {
                System.out.println("User " + userId + " added successfully");
                return true;
            } else {
                System.err.println("Failed to add user " + userId + ". Error code: " + ret);
                return false;
            }
        } else {
            System.err.println("Failed to add user " + userId + ".");
            return false;
        }

    }
    /**
     * 将Windows特征值转换为后台比对特征值
     * @param windowsFeature Windows设备输出的特征值（1036字节）
     * @return 转换后的特征值（2096字节），如果转换失败则返回null
     */
    public byte[] convertWindowsFeatureToInt16(byte[] windowsFeature) {
        // 验证输入参数
        if (windowsFeature == null ) {
            System.err.println("Windows feature must be initialized");
            return null;
        }

        // 准备输出缓冲区（2096字节）
        byte[] int16Feature = new byte[2096];

        // 调用转换接口
        int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_CvtWinFeatToInt16Feat(
                windowsFeature,  // Windows特征值
                1036,            // Windows特征值长度
                int16Feature,    // 转换后的特征值缓冲区
                2096             // 转换后的特征值缓冲区长度
        );

        // 检查转换结果
        if (ret == VeinReturnCode.PV_OK) {
            System.out.println("Feature conversion successful");
            return int16Feature;
        } else {
            System.err.println("Feature conversion failed with error code: " + ret);
            return null;
        }
    }

    /**
     * 特征格式转换接口，将安卓端输出的特征值转换为后台比对特征值
     * @param floatFeature 安卓端输出的特征值 浮点型特征值长度, 数值只能选1024或512;单掌静脉固定512，掌纹掌静脉固定1024；且特征值组合为掌静脉（512） + 掌纹（512）
     * @return 转换后的特征值（2096字节），如果转换失败则返回null
     */
    public byte[] convertFloatFeatureToInt16(float[] floatFeature) {
        // 验证输入参数
        if (floatFeature == null ) {
            System.err.println("Windows feature must be initialized");
            return null;
        }

        // 准备输出缓冲区（2096字节）
        byte[] int16Feature = new byte[2096];

        // 调用转换接口
        int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_CvtFloatFeatToInt16Feat(
                floatFeature,  // 安卓端特征值
                512,            // 安卓端特征值长度
                int16Feature,    // 转换后的特征值缓冲区
                2096             // 转换后的特征值缓冲区长度
        );

        // 检查转换结果
        if (ret == VeinReturnCode.PV_OK) {
            System.out.println("Feature conversion successful");
            return int16Feature;
        } else {
            System.err.println("Feature conversion failed with error code: " + ret);
            return null;
        }
    }
    // 删除用户
    public boolean deleteUser(int userId) {
        if (algHandleNew == null) {
            System.err.println("Database not initialized");
            return false;
        }

        int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_DelUser(algHandleNew, userId);

        if (ret == VeinReturnCode.PV_OK) {
            System.out.println("User " + userId + " deleted successfully");
            return true;
        } else {
            System.err.println("Failed to delete user " + userId + ". Error code: " + ret);
            return false;
        }
    }

    // 删除用户
    public boolean deleteAllUser() {
        if (algHandleNew == null) {
            System.err.println("Database not initialized");
            return false;
        }

        int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_ClearUser(algHandleNew);

        if (ret == VeinReturnCode.PV_OK) {
            System.out.println( "deleted successfully");
            return true;
        } else {
            System.err.println("Failed to delete " + ". Error code: " + ret);
            return false;
        }
    }

    // 释放数据库资源
    public boolean cleanup() {
        if (algHandleNew == null) {
            return true; // 已经释放或未初始化
        }

        int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Data_DeInit(algHandleNew);

        if (ret == VeinReturnCode.PV_OK) {
            System.out.println("Database released successfully");
            //algHandle = null; // 重置句柄
            return true;
        } else {
            System.err.println("Failed to release database. Error code: " + ret);
            return false;
        }
    }
    //Web比对接口
    public Map<String,Object> comparisonPalmWeb(byte[] featureData) throws IOException {

        try {
            // 假设你已经有两个String格式的掌静脉特征值
            //String veinFeature1 = "IAEBAAAAAABq/gEMfAEz+ZT/l/8P/w4ATQCu/sP09vLF/4QFMwXpBykBBwAwArgBUgN/Ac7/YwDaAZ4CD/1BAuUB8QANAQEDDgVnCJwI+gJpAzf/3Ps8+bUBU/j5Bf7/kfr4A0MCEQBiDMr2AQrV/OH0HvqzAjsEkAk6AQMBYAjv/C4ABPpQ/QsDfgQKAhIAIATF/439Dv38ARX9SvwXCNf9dQiBBeL66gWXA58A6vxuCFwDVP1uA8D8cQLg/4L+wAal/uwERQDZ+TcDAwEG/cwEyAHYAmoGaQHrATsCcwEw/3UDVQWDBbsEcPsJAt8A/ATVAwIFCP9mALLzm/o+/1wHmARN+7MGZv+IAEUJrge+AOn6Lwjx+q8ExP7x+af2eQtM8CMC2AF2/q0FxgPe/jT+Nfrn+TUIdgCf+AMBOv50Aq35zAFM+2IAZvoj+ikCMP49/LkE+Pd4+Kv+9APdAZ74QPfi/hwBq/xx8hj/lfnz/rn9E/5l+kL4QPeCBRQNbvcJBE7/OP4ZA0IB2ADGAtH3qf9jCOkCXPuZ+oH5WALB/S8FkP9+/hL+lP+P/rj+G/1O+Ib5mfvG/5f9FABU9ucAcAhC/dP36Psk/pL7Q/nLBZb9FAK5Arb+hgI9BfcDh/13+uP/wwfM/Sv+AgI6+0MJyP1d/okA2vbeAh3+yfs=";
            //String veinFeature2 = "IAEBAAAAAAD6B1wJQgVp/SD5CAJD/10BRP0Y/bH13/LO/iEHegeoB4r8FP7mAP7+tAOH/h4A6v1p/T0DnAQxAsQHLQhGAWj8QAVeAqoFIQM9Brj/h/hn+ab/OP0VBrj8LwPSAVj89wDLDEf9PQu6/rTxFvZtAeX/WwloAx4DNwL8/HMBTv7H/XECoQcqAtL9pgUTBUb/qwFJ/psADf8hCeb4JATIA/n8VAZPAgEA0/z2BV8DxQRQAij7mgHwAbX53wYoACkAV/3g+Ef9bv5U/W0IWf+C/i7/RQIf/1AASAVt/04BXAHsBKABB/6UA1T9kgFABHYBU/ykBWX0DvtQA3YCIf9J+yQKvgAqAAQHAwUW/WL7PgixAJz/9/iO+Sf6LwhG+3UCVQItAJQFGARh//L78Pu58/gBkf8d9jAG8f7YBU/5iQBO/O7/qfw2AW3/Mf3g/XMHZvb2+mz+/gdFBqb0S/aH/44BIvpf+Kz9S/cW/fz5zgFp+AT9ZvNPBJYErPpsAkQA4PsxCvD8nv1lAvj9dQCeBIUEzvw3+Bj48wHY/GgDgP+L+Uz89QGq/7/48Po2+rD2GfMa++P/+P2Y/fb//QohBJD2jf33/Ab4IvdkBBP8bAf3AFUBAfyeAnEAIP6u+gUE1QUH+079gwE5ANgIegP0AiQAWvoVAur9/vw=";

            // 将String转换为byte数组
            //featBuf1 = Base64.getDecoder().decode(veinFeature1);
            //featBuf2 = Base64.getDecoder().decode(veinFeature2);
            //float[] featBuf1 =veinFeature1;
            //float[] featBuf2 =byteArrayToFloatArray(featBuf2s);
            //System.out.println(featBuf1.length);
            // 创建用于存储距离结果的float数组
            //WriteBytesToFile(featBuf1);
            Map<String,Object> objectMap = new HashMap<>();
            // 调用特征比对
            if (algHandleNew == null) {
                throw new IllegalStateException("Database not initialized");
            }

            if (featureData == null) {
                throw new IllegalArgumentException("Feature data must be initialized");
            }
            byte[] newByte = convertWindowsFeatureToInt16(featureData);
            if (newByte == null) {
                throw new IllegalArgumentException("Feature data must be initialized");
            }
            // 创建输出参数的引用
            IntByReference pRes = new IntByReference(0); // 比对结果
            IntByReference pResUserId = new IntByReference(0); // 匹配的用户ID
            FloatByReference pScore = new FloatByReference(0.0f); // 匹配得分

            // 调用识别接口
            int ret = XRCommonVeinAlgAPI.INSTANCE.XR_Palm_Identity(
                    algHandleNew,
                    newByte,
                    pRes,
                    pResUserId,
                    pScore
            );
            float value = pScore.getValue();
            // 检查返回值并处理结果
//            if (ret == VeinReturnCode.PV_OK) {
//                System.out.println("特征比对成功，距离值: " +value);
//                if (value >= 0.95f) {
//                    System.out.println("特征匹配，超过建议阈值0.95"+value);
//                } else {
//                    System.out.println("特征不匹配，低于建议阈值0.95"+value);
//                }
//                return pScore.getValue();
//            } else {
//                System.out.println("特征比对失败，错误码: " + ret+value);
//                return value;
//            }
            objectMap.put("pScore",value);
            objectMap.put("pResUserId",pResUserId.getValue());
            objectMap.put("pRes",pRes.getValue());
            System.out.println("value:"+value);
            return objectMap;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}


// 自定义异常类
class VeinException extends Exception {
    public VeinException(String message) {
        super(message);
    }
}
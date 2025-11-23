package org.example.javacamerserver.streamHolder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Pointer;
import nu.pattern.OpenCV;
import org.example.javacamerserver.utils.SpringContextUtil;
import org.example.javacamerserver.web.domain.PalmComparisonResult;
import org.example.javacamerserver.web.domain.TdxConfig;
import org.example.javacamerserver.web.domain.TdxPalm;
import org.example.javacamerserver.web.service.ITdxConfigService;
import org.example.javacamerserver.web.service.ITdxPalmService;
import org.example.javacamerserver.xrCamer.CameraState;
import org.example.javacamerserver.xrCamer.SonixCameraManager;
import org.example.javacamerserver.xrCamer.VeinProcessor;
import org.example.javacamerserver.xrCamer.sPalmInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;


/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
@Component // 添加这个注解
public class CameraStreamHolder extends TextWebSocketHandler {
    private static final int XR_VEIN_FEATURE_INFO_SIZE = 512; // 根据实际SDK调整

    private static final int FEATURE_SIZE = 512; // 特征值长度

    @Autowired
    private SonixCameraManager manager;
    //打开摄像头抓拍
    // 使用依赖注入
    @Autowired
    private VeinProcessor processor;

    @Autowired
    private ITdxPalmService palmService;

    @Autowired
    private ITdxConfigService configService;
    // 单例Holder模式
//    private static class Holder {
//        private static final CameraStreamHolder INSTANCE = new CameraStreamHolder();
//
//    }

    // 添加ObjectMapper用于JSON解析
    private final ObjectMapper objectMapper = new ObjectMapper();
   /* public static CameraStreamHolder getInstance() {
        return Holder.INSTANCE;
    }*/

    private Pointer algHandle;
    // 私有构造方法
    public CameraStreamHolder() {}

    // 状态控制
    private WebSocketSession activeSession;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    // 全局锁控制资源访问
    private final ReentrantLock processingLock = new ReentrantLock();

    //视频流状态（0是预览，采集是1，比对是2）
    private Integer streamType = 0;
    //最多抓取次数
    private Integer times = 0;
    //分数值
    private Float recognitionScore = 0F;
    //分数值
    private boolean afterComparison = false;
    //比对次数
    private Integer comTimes = 0;

    private boolean comparisons = false;
    private String palmId ="";

    static {
        // 加载OpenCV本地库
        OpenCV.loadShared();
    }
    private final Object websocketSendLock = new Object();
    // 安全的 WebSocket 发送方法
    private void safeSendMessage(TextMessage message) {
        synchronized (websocketSendLock) {
            if (activeSession != null && activeSession.isOpen()) {
                try {
                    activeSession.sendMessage(message);
                } catch (Exception e) {
                    e.printStackTrace();
                    // 处理发送失败的情况（可选）
                }
            }
        }
    }
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        try {
            // 解析JSON命令
            Map<String, String> commandMap = objectMapper.readValue(payload, Map.class);
            String command = commandMap.get("command");
            String palm_vein_id = commandMap.get("palm_vein_id");
            if ("register_palm_vein".equals(command)) {
                synchronized (this) {
                    if (null!=palm_vein_id&&!palm_vein_id.trim().equals("")){
                        //ITdxPalmService palmService = SpringContextUtil.getBean(ITdxPalmService.class);
                        TdxPalm tdxPalm = palmService.selectTdxPalmByPalmId(palm_vein_id);
                        if (null!=tdxPalm){
                            safeSendMessage(new TextMessage("{\"info\":\"This palm vein ID has been registered\"}"));
                        }else {
                            palmId=palm_vein_id;
                            streamType = 1; // 设置为注册模式
                            //enrollStep = 0;  // 重置注册步骤
                            times = 0 ;
                            afterComparison=false;
                            processor.initEnrollEnv(); // 初始化注册环境
                            safeSendMessage(new TextMessage("{\"info\":\"Start palm vein registration\"}"));
                        }
                    }else {
                        safeSendMessage(new TextMessage("{\"info\":\"Palm vein ID failed\"}"));
                    }
                }
            } else if ("compare_palm_vein".equals(command)) {
                synchronized (this) {
                   /* streamType = 2; // 设置为比对模式
                    afterComparison=false;
                    times = 0 ;
                    safeSendMessage(new TextMessage("{\"info\":\"Start palm vein comparison\"}"));*/
                    streamType = 0;
                }
            } else if ("update_palm_vein".equals(command)) {
                synchronized (this) {
                    if (null!=palm_vein_id&&!palm_vein_id.trim().equals("")){
                        //ITdxPalmService palmService = SpringContextUtil.getBean(ITdxPalmService.class);
                        TdxPalm tdxPalm = palmService.selectTdxPalmByPalmId(palm_vein_id);
                        if (null!=tdxPalm){
                            palmId=palm_vein_id;
                            streamType = 3; // 设置为修改模式
                            //enrollStep = 0;  // 重置注册步骤
                            times = 0 ;
                            afterComparison=false;
                            processor.initEnrollEnv(); // 初始化注册环境
                            safeSendMessage(new TextMessage("{\"info\":\"Start modifying palm vein\"}"));
                        }else {
                            safeSendMessage(new TextMessage("{\"info\":\"The palm vein ID is not registered\"}"));
                        }
                    }else {
                        safeSendMessage(new TextMessage("{\"info\":\"Palm vein ID failed\"}"));
                    }
                }
            } else if ("delete_palm_vein".equals(command)) {
                synchronized (this) {
                    if (null!=palm_vein_id&&!palm_vein_id.trim().equals("")){
                        //ITdxPalmService palmService = SpringContextUtil.getBean(ITdxPalmService.class);
                        TdxPalm tdxPalm = palmService.selectTdxPalmByPalmId(palm_vein_id);
                        if (null!=tdxPalm){
                            Integer deleteFlag = palmService.deleteTdxPalm(tdxPalm);
                            afterComparison=false;
                            if (deleteFlag>0){
                                safeSendMessage(new TextMessage("{\"info\":\"Palm vein removed successfully\"}"));
                            }else {
                                safeSendMessage(new TextMessage("{\"info\":\"Failed to delete palm vein\"}"));
                            }
                        }else {
                            safeSendMessage(new TextMessage("{\"info\":\"The palm vein ID is not registered\"}"));
                        }
                    }else {
                        safeSendMessage(new TextMessage("{\"info\":\"Palm vein ID failed\"}"));
                    }
                }
            }else {
                streamType = 0; // 设置为注册模式
            }
        } catch (Exception e) {
            safeSendMessage(new TextMessage(
                    "{\"error\":\"Command parsing failed: " + e.getMessage() + "\"}"
            ));
        }
    }
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        synchronized (this) {
            if (activeSession != null && activeSession.isOpen()) {
                // 已存在活跃连接，拒绝新连接
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Only one client allowed"));
                return;
            }
            try {
                //ITdxConfigService configService = SpringContextUtil.getBean(ITdxConfigService.class);
                TdxConfig tdxConfig = configService.selectTdxConfigByConfig("recognition_score");
                if (null==tdxConfig){
                    recognitionScore = 0.95F;
                } else {
                    recognitionScore = tdxConfig.getValue();
                }
                if (manager==null||processor==null){
//                    if (!processor.init()) {
//                        System.err.println("Initialization failed!");
//                        return;
//                    }
                    // 打开第一个摄像头
                    if (manager.openCamera(0)) {
                        System.out.println("Camera is on");

                        // 设置抓拍间隔为2秒
                        manager.setCaptureInterval(2000);
                        CameraState.setCameraFlag(true);

                        //System.out.println("454867687867");
                        // 关闭摄像头
                        System.out.println("The program has exited");
                    } else {
                        System.err.println("Unable to open the camera");
                    }

                }else {
                    if (!manager.isOpened){
                        // 打开第一个摄像头
                        if (manager.openCamera(0)) {
                            System.out.println("Camera is on");

                            // 设置抓拍间隔为2秒
                            manager.setCaptureInterval(2000);
                            CameraState.setCameraFlag(true);

                            //System.out.println("454867687867");
                            // 关闭摄像头
                            System.out.println("The program has exited");
                        } else {
                            System.err.println("Unable to open the camera");
                        }
                    }
                    processor.initEnrollEnv();
                }
            } catch (Exception e) {
                handleError(e);
                throw new RuntimeException(e);
            }
            // 注册新会话
            activeSession = session;
            streamType=0;
            afterComparison = false;
            startVideoStream();
        }
    }

    private void startVideoStream() {
        if (isStreaming.compareAndSet(false, true)) {
            // 启动视频流发送线程
            new Thread(this::streamFrames).start();
        }
    }

    private void streamFrames() {
        // 开始定时抓拍
        try {
            manager.startCapture();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Map<String,Object> map = new HashMap<>();
        try  {
            while (isStreaming.get() && activeSession.isOpen()) {
                if (!CameraState.isIsTransform()) {
                    try {
                        //System.out.println(CameraState.getCameraName());
                        String base64 = CameraState.getCameraName();
                        if (streamType==0){
                            if (afterComparison){
                                times++;
                                if (times>10){
                                    times=0;
                                    afterComparison=false;
//                                    if (null!=base64&&!base64.equals("")){
//                                        safeSendMessage(new TextMessage(
//                                                "{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" +" "+ "\"}"));
//                                    }
                                }
//                                if (null!=base64&&!base64.equals("")){
//                                    safeSendMessage(new TextMessage(
//                                            "{\"frame\":\"data:image/jpeg;base64," + base64 + "\"}"));
//                                }
                                /*CameraState.setIsTransform(true);
                                CameraState.setCameraFlag(true);*/
                            }else {
                                if(!comparisons){
                                    CameraState.setCameraName(null);
                                    // 2. 解码 Base64 字符串为 byte[]
                                    byte[] imageBytes = Base64.getDecoder().decode(base64);
                                    // 3. 将 byte[] 转换为 BufferedImage
                                    ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
                                    BufferedImage image = ImageIO.read(bis);
                                    // 处理图片
                                    map = processor.scanningComparison(image);
                                    comparisons =true;
                                    // 启动新线程执行特定方法
                                    Map<String, Object> finalMap = map;
                                    new Thread(() -> {
                                        comTimes++;
                                        System.out.println("有锁");
                                        CameraState.setIsTransform(true);
                                        CameraState.setCameraFlag(true);

                                        cleanupTempResources(imageBytes, bis, image);

                                        try {
                                            // 发送消息
                                            if (finalMap !=null){
                                                String coordinates = getPalmInfoJsonString(finalMap);
                                                if (finalMap.get("baseFeature")!=null&& finalMap.get("index")!=null&&Integer.valueOf(finalMap.get("index").toString())==1){
                                                    // 获取Bean并保存
                                                    //ITdxPalmService palmService = SpringContextUtil.getBean(ITdxPalmService.class);
                                                    PalmComparisonResult palmComparisonResult=palmService.palmComparison(finalMap.get("baseFeature").toString());
                                                    if (comTimes<10&&palmComparisonResult.getScore()<=recognitionScore){
                                                        // 以 Jackson 为例
                                                        ObjectMapper objectMapper = new ObjectMapper();
                                                        Map<String, Object> response = new HashMap<>();

                                                        // 拼接 info 字符串
                                                        String info = finalMap.get("msg").toString().trim()
                                                                + "，The score is " + palmComparisonResult.getScore()
                                                                + "，Palmprint ID is " + palmComparisonResult.getPalmId();
                                                        response.put("info", info);
                                                        response.put("palmId", palmComparisonResult.getPalmId());
                                                        response.put("", "Comparison successful");
                                                        response.put("success", "Comparison successful");
                                                        response.put("coordinates",coordinates);
                                                        response.put("frame", "data:image/jpeg;base64," + base64);
                                                        // 生成 JSON 字符串（自动处理特殊字符转义）
                                                        String jsonMessage = null;
                                                        try {
                                                            jsonMessage = objectMapper.writeValueAsString(response);
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        // 发送消息
                                                        safeSendMessage(new TextMessage(jsonMessage));
                                                        cleanupTempResources(imageBytes, bis, image);
                                                        try {
                                                            Thread.sleep(2000);
                                                        } catch (InterruptedException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        System.out.println("成功发送");
                                                        afterComparison=true;
                                                        comTimes=0;
                                                        times=0;
                                                        comparisons=false;
                                                        //break;
                                                    } else if (comTimes>10){
                                                        System.out.println("失败发送");
                                                        TextMessage textMessage =null;
                                                        Map<String, Object> response = new HashMap<>();
                                                        response.put("info", "Comparison failed");
                                                        response.put("frame", "data:image/jpeg;base64," + base64);
                                                        response.put("coordinates",coordinates);
                                                        System.out.println("失败发送");
                                                        String jsonMessage = null;
                                                        try {
                                                            jsonMessage = objectMapper.writeValueAsString(response);
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        // 发送消息
                                                        safeSendMessage(new TextMessage(jsonMessage));
                                                        cleanupTempResources(imageBytes, bis, image);
                                                        try {
                                                            Thread.sleep(2000);
                                                        } catch (InterruptedException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        textMessage = new TextMessage("{\"info\":\""  +" "+ "\"}");
                                                        System.out.println("失败发送");
                                                        safeSendMessage(textMessage);
                                                        comTimes=0;
                                                        times=0;
                                                        comparisons=false;
                                                    }else {
                                                        Map<String, Object> response = new HashMap<>();
                                                        response.put("info", "");
                                                        response.put("frame", "data:image/jpeg;base64," + base64);
                                                        response.put("coordinates",coordinates);
                                                        System.out.println("失败发送");
                                                        String jsonMessage = null;
                                                        try {
                                                            jsonMessage = objectMapper.writeValueAsString(response);
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        // 发送消息
                                                        safeSendMessage(new TextMessage(jsonMessage));
                                                        cleanupTempResources(imageBytes, bis, image);
                                                        comparisons=false;
                                                    }
                                                }else {
                                                    Map<String, Object> response = new HashMap<>();
                                                    response.put("info", "");
                                                    response.put("frame", "data:image/jpeg;base64," + base64);
                                                    response.put("coordinates",coordinates);
                                                    System.out.println("失败发送");
                                                    String jsonMessage = null;
                                                    try {
                                                        jsonMessage = objectMapper.writeValueAsString(response);
                                                    } catch (JsonProcessingException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    // 发送消息
                                                    safeSendMessage(new TextMessage(jsonMessage));
                                                    cleanupTempResources(imageBytes, bis, image);
                                                    comparisons=false;
                                                    comTimes=0;
                                                }
                                            }
                                        } finally {
                                            comparisons=false;
                                        }
                                    }).start();
                                }else {
                                    if (null!=base64&&!base64.equals("")){
                                        byte[] imageBytes = Base64.getDecoder().decode(base64);
                                        // 3. 将 byte[] 转换为 BufferedImage
                                        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
                                        BufferedImage image = ImageIO.read(bis);
                                        map = processor.scanningComparison(image);
                                        // 启动新线程执行特定方法
                                        Map<String, Object> finalMap = map;
                                        String coordinates = getPalmInfoJsonString(finalMap);
                                        Map<String, Object> response = new HashMap<>();
                                        response.put("info", "");
                                        response.put("frame", "data:image/jpeg;base64," + base64);
                                        response.put("coordinates",coordinates);
                                        System.out.println("失败发送");
                                        String jsonMessage = null;
                                        try {
                                            jsonMessage = objectMapper.writeValueAsString(response);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                        // 发送消息
                                        safeSendMessage(new TextMessage(jsonMessage));
                                        cleanupTempResources(imageBytes, bis, image);
                                    }else {
                                        TextMessage textMessage =null;
                                        textMessage = new TextMessage("{\"info\":\""  +" "+ "\"}");
                                        System.out.println("失败发送");
                                        safeSendMessage(textMessage);
                                    }
                                    System.out.println("停止");
                                    CameraState.setIsTransform(true);
                                    CameraState.setCameraFlag(true);
                                }
                            }
                        }else if (streamType==1){
                            CameraState.setCameraName(null);
                            // 2. 解码 Base64 字符串为 byte[]
                            byte[] imageBytes = Base64.getDecoder().decode(base64);
                            // 3. 将 byte[] 转换为 BufferedImage
                            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
                            BufferedImage image = ImageIO.read(bis);
                            if (processingLock.tryLock()) {
                                System.out.println("有锁");
                                CameraState.setIsTransform(true);
                                CameraState.setCameraFlag(true);
                                try {
                                    // 处理图片
                                    map = processor.enrollProcessNew(image);
                                    // 在关键代码段添加内存日志（单位MB）
                                    Runtime rt = Runtime.getRuntime();
                                    System.out.printf(
                                            "[MEM] Total: %.2fMB, Used: %.2fMB, Free: %.2fMB, Max: %.2fMB\n",
                                            rt.totalMemory()/1024.0/1024,
                                            (rt.totalMemory() - rt.freeMemory())/1024.0/1024,
                                            rt.freeMemory()/1024.0/1024,
                                            rt.maxMemory()/1024.0/1024
                                    );
                                    // 发送消息
                                    if (map!=null){
                                        TextMessage textMessage =null;
                                        String coordinates = getPalmInfoJsonString(map);
                                        if (map.get("baseFeature")!=null&&map.get("index")!=null&&Integer.valueOf(map.get("index").toString())==1){
                                            times++;
                                            if (times>3){
                                                cleanupTempResources(imageBytes, bis, image);
                                                TdxPalm tdxPalm = new TdxPalm();
                                                tdxPalm.setPalmId(palmId);
                                                tdxPalm.setFeature(map.get("baseFeature").toString());
                                                // 获取Bean并保存
                                                //ITdxPalmService palmService = SpringContextUtil.getBean(ITdxPalmService.class);
                                                PalmComparisonResult palmComparisonResult=palmService.palmComparison(map.get("baseFeature").toString());
                                                if (palmComparisonResult!=null&&palmComparisonResult.getScore()!=null&&palmComparisonResult.getScore()<=recognitionScore){
                                                    Map<String, Object> response = new HashMap<>();
                                                    response.put("info", "Registration failed, palm print already exists!");
                                                    response.put("frame", "data:image/jpeg;base64," + base64);
                                                    response.put("coordinates",coordinates);
                                                    System.out.println("失败发送");
                                                    String jsonMessage = null;
                                                    try {
                                                        jsonMessage = objectMapper.writeValueAsString(response);
                                                    } catch (JsonProcessingException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    // 发送消息
                                                    safeSendMessage(new TextMessage(jsonMessage));
                                                    cleanupTempResources(imageBytes, bis, image);
//                                                    textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" +"" + "\"}");
//                                                    cleanupTempResources(imageBytes, bis, image);
                                                    System.out.println("继续采集");
                                                    try {
                                                        processor.initEnrollEnv();
                                                        Thread.sleep(1000);
                                                    } catch (InterruptedException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    times=0;
                                                    //safeSendMessage(textMessage);
                                                }else {
                                                    tdxPalm.setPalmImg(base64);
                                                    tdxPalm.setPalmType(1);
                                                    System.out.println(map);
                                                    // 使用示例
                                                    sPalmInfo palmInfo = getPalmInfoSafely(map);
                                                    if (palmInfo != null) {
                                                        byte status = palmInfo.status;

                                                        if (status == 1) {
                                                            tdxPalm.setPalmNum(1);
                                                            // 处理状态1的逻辑
                                                        } else if (status == 2) {
                                                            tdxPalm.setPalmNum(2);
                                                            // 处理状态2的逻辑
                                                        } else {
                                                            System.out.println("未知状态: " + status);
                                                        }
                                                        //tdxPalm.setPalmNum(palmInfo.getStatus());
                                                    }
                                                    Integer save =palmService.saveTdxPalm(tdxPalm);
                                                    if (save>0){
                                                        /*String msg ="{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" + map.get("msg").toString().trim() +"\","+"\"feature\":\"" + map.get("baseFeature").toString() +"\","
                                                                +"\"palmId\":\"" + tdxPalm.getPalmId() +"\","+"\"success\":\"" + "Successful registration" + "\"}";
                                                        textMessage = new TextMessage(msg);
                                                        safeSendMessage(textMessage);*/
                                                        Map<String, Object> response = new HashMap<>();
                                                        response.put("frame", "data:image/jpeg;base64," + base64);
                                                        response.put("info", map.get("msg").toString().trim());
                                                        response.put("feature", map.get("baseFeature").toString());
                                                        response.put("palmId", tdxPalm.getPalmId());
                                                        response.put("success", "Successful registration");
                                                        response.put("coordinates",coordinates);
                                                        System.out.println("失败发送");
                                                        String jsonMessage = null;
                                                        try {
                                                            jsonMessage = objectMapper.writeValueAsString(response);
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        // 发送消息
                                                        safeSendMessage(new TextMessage(jsonMessage));
                                                        cleanupTempResources(imageBytes, bis, image);
                                                        // 1. 转换输入特征
                                                        //byte[] featBuf1 = robustBase64Decode(map.get("baseFeature").toString());
                                                        System.out.println("成功发送");
                                                        streamType=0;
                                                        try {
                                                            Thread.sleep(3000);
                                                        } catch (InterruptedException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    }else {
//                                                        textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" +"Registration failed, palm print already exists!" + "\"}");
//                                                        cleanupTempResources(imageBytes, bis, image);
                                                        Map<String, Object> response = new HashMap<>();
                                                        response.put("info", "Registration failed, palm print already exists!");
                                                        response.put("frame", "data:image/jpeg;base64," + base64);
                                                        response.put("coordinates",coordinates);
                                                        System.out.println("继续采集");
                                                        String jsonMessage = null;
                                                        try {
                                                            jsonMessage = objectMapper.writeValueAsString(response);
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        // 发送消息
                                                        safeSendMessage(new TextMessage(jsonMessage));
                                                        cleanupTempResources(imageBytes, bis, image);
                                                        try {
                                                            processor.initEnrollEnv();
                                                            Thread.sleep(200);
                                                        } catch (InterruptedException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        times=0;
                                                        safeSendMessage(textMessage);
                                                    }
                                                }
                                            }else {
                                                /*textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" +"Please keep your palm steady" + "\"}");
                                                cleanupTempResources(imageBytes, bis, image);
                                                System.out.println("继续采集");
                                                safeSendMessage(textMessage);*/
                                                Map<String, Object> response = new HashMap<>();
                                                response.put("info", "Please keep your palm steady");
                                                response.put("frame", "data:image/jpeg;base64," + base64);
                                                response.put("coordinates",coordinates);
                                                System.out.println("继续采集");
                                                String jsonMessage = null;
                                                try {
                                                    jsonMessage = objectMapper.writeValueAsString(response);
                                                } catch (JsonProcessingException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                // 发送消息
                                                safeSendMessage(new TextMessage(jsonMessage));
                                                cleanupTempResources(imageBytes, bis, image);
                                                try {
                                                    processor.initEnrollEnv();
                                                    Thread.sleep(200);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            //break;
                                        }else {
//                                            textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" + map.get("msg").toString().trim() + "\"}");
//                                            cleanupTempResources(imageBytes, bis, image);
                                            Map<String, Object> response = new HashMap<>();
                                            response.put("info", map.get("msg").toString().trim());
                                            response.put("frame", "data:image/jpeg;base64," + base64);
                                            response.put("coordinates",coordinates);
                                            System.out.println("失败发送");
                                            String jsonMessage = null;
                                            try {
                                                jsonMessage = objectMapper.writeValueAsString(response);
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            }
                                            // 发送消息
                                            safeSendMessage(new TextMessage(jsonMessage));
                                            cleanupTempResources(imageBytes, bis, image);
                                            /*System.out.println("失败发送");
                                            safeSendMessage(textMessage);*/
                                        }
                                    }
                                } finally {
                                    processingLock.unlock();
                                }
                            } else {
                                System.out.println("正常");
                                safeSendMessage(new TextMessage(
                                        "{\"frame\":\"data:image/jpeg;base64," + base64 + "\"}"));
                            }
                        }else if (streamType==2){

                        }else if (streamType==3){
                            CameraState.setCameraName(null);
                            // 2. 解码 Base64 字符串为 byte[]
                            byte[] imageBytes = Base64.getDecoder().decode(base64);
                            // 3. 将 byte[] 转换为 BufferedImage
                            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
                            BufferedImage image = ImageIO.read(bis);
                            if (processingLock.tryLock()) {
                                System.out.println("有锁");
                                CameraState.setIsTransform(true);
                                CameraState.setCameraFlag(true);
                                try {
                                    // 处理图片
                                    map = processor.enrollProcessNew(image);
                                    // 在关键代码段添加内存日志（单位MB）
                                    Runtime rt = Runtime.getRuntime();
                                    System.out.printf(
                                            "[MEM] Total: %.2fMB, Used: %.2fMB, Free: %.2fMB, Max: %.2fMB\n",
                                            rt.totalMemory()/1024.0/1024,
                                            (rt.totalMemory() - rt.freeMemory())/1024.0/1024,
                                            rt.freeMemory()/1024.0/1024,
                                            rt.maxMemory()/1024.0/1024
                                    );
                                    // 发送消息
                                    if (map!=null){
                                        TextMessage textMessage =null;
                                        String coordinates = getPalmInfoJsonString(map);
                                        if (map.get("baseFeature")!=null&&map.get("index")!=null&&Integer.valueOf(map.get("index").toString())==1){
                                            times++;
                                            if (times>3){
                                                cleanupTempResources(imageBytes, bis, image);
                                                //ITdxPalmService palmService = SpringContextUtil.getBean(ITdxPalmService.class);
                                                TdxPalm tdxPalm = palmService.selectTdxPalmByPalmId(palmId);
                                                tdxPalm.setFeature(map.get("baseFeature").toString());
                                                // 获取Bean并保存
                                                PalmComparisonResult palmComparisonResult=palmService.palmComparison(map.get("baseFeature").toString());
                                                if (palmComparisonResult!=null&&palmComparisonResult.getScore()!=null&&palmComparisonResult.getScore()<=recognitionScore&&!palmComparisonResult.getPalmId().equals(palmId)){
                                                   /* textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" +"Modification failed, the palm print already exists!" + "\"}");
                                                    cleanupTempResources(imageBytes, bis, image);
                                                    System.out.println("修改失败");
                                                    safeSendMessage(textMessage);*/
                                                    Map<String, Object> response = new HashMap<>();
                                                    response.put("info", "Registration failed, palm print already exists!");
                                                    response.put("frame", "data:image/jpeg;base64," + base64);
                                                    response.put("coordinates",coordinates);
                                                    System.out.println("失败发送");
                                                    String jsonMessage = null;
                                                    try {
                                                        jsonMessage = objectMapper.writeValueAsString(response);
                                                    } catch (JsonProcessingException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    // 发送消息
                                                    safeSendMessage(new TextMessage(jsonMessage));
                                                    cleanupTempResources(imageBytes, bis, image);
                                                    System.out.println("继续采集");
                                                    try {
                                                        processor.initEnrollEnv();
                                                        Thread.sleep(1000);
                                                    } catch (InterruptedException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    times=0;
                                                }else {
                                                    tdxPalm.setPalmImg(base64);
                                                    palmService.updateTdxPalm(tdxPalm);
                                                    /*textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" + "Modification successful" +"\","+"\"feature\":\"" + map.get("baseFeature").toString() +"\","
                                                            +"\"palmId\":\"" + tdxPalm.getPalmId() +"\","+"\"success\":\"" + "Modification successful" + "\"}");
                                                    safeSendMessage(textMessage);*/
                                                    Map<String, Object> response = new HashMap<>();
                                                    response.put("frame", "data:image/jpeg;base64," + base64);
                                                    response.put("info", "Modification successful");
                                                    response.put("feature", map.get("baseFeature").toString());
                                                    response.put("palmId", tdxPalm.getPalmId());
                                                    response.put("success", "Modification successful");
                                                    response.put("coordinates",coordinates);
                                                    System.out.println("失败发送");
                                                    String jsonMessage = null;
                                                    try {
                                                        jsonMessage = objectMapper.writeValueAsString(response);
                                                    } catch (JsonProcessingException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    // 发送消息
                                                    safeSendMessage(new TextMessage(jsonMessage));
                                                    cleanupTempResources(imageBytes, bis, image);
                                                    // 1. 转换输入特征
                                                    //byte[] featBuf1 = robustBase64Decode(map.get("baseFeature").toString());
                                                    System.out.println("成功发送");
                                                    streamType=0;
                                                    try {
                                                        Thread.sleep(3000);
                                                    } catch (InterruptedException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                }
                                            }else {
                                                /*textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" +"Please keep your palm steady" + "\"}");
                                                cleanupTempResources(imageBytes, bis, image);
                                                System.out.println("继续采集");
                                                safeSendMessage(textMessage);*/
                                                Map<String, Object> response = new HashMap<>();
                                                response.put("info", "Please keep your palm steady");
                                                response.put("frame", "data:image/jpeg;base64," + base64);
                                                response.put("coordinates",coordinates);
                                                System.out.println("继续采集");
                                                String jsonMessage = null;
                                                try {
                                                    jsonMessage = objectMapper.writeValueAsString(response);
                                                } catch (JsonProcessingException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                // 发送消息
                                                safeSendMessage(new TextMessage(jsonMessage));
                                                cleanupTempResources(imageBytes, bis, image);
                                                try {
                                                    processor.initEnrollEnv();
                                                    Thread.sleep(200);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            //break;
                                        }else {
                                           /* textMessage = new TextMessage("{\"frame\":\"data:image/jpeg;base64," + base64 + "\","+"\"info\":\"" + map.get("msg").toString().trim() + "\"}");
                                            cleanupTempResources(imageBytes, bis, image);
                                            System.out.println("失败发送");
                                            safeSendMessage(textMessage);*/
                                            Map<String, Object> response = new HashMap<>();
                                            response.put("info", map.get("msg").toString().trim());
                                            response.put("frame", "data:image/jpeg;base64," + base64);
                                            response.put("coordinates",coordinates);
                                            System.out.println("失败发送");
                                            String jsonMessage = null;
                                            try {
                                                jsonMessage = objectMapper.writeValueAsString(response);
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            }
                                            // 发送消息
                                            safeSendMessage(new TextMessage(jsonMessage));
                                            cleanupTempResources(imageBytes, bis, image);
                                        }
                                    }
                                } finally {
                                    processingLock.unlock();
                                }
                            } else {
                                System.out.println("正常");
                                safeSendMessage(new TextMessage(
                                        "{\"frame\":\"data:image/jpeg;base64," + base64 + "\"}"));
                            }
                        }
                        // 保存或处理合格图像
                    }catch (Exception e){
                        e.printStackTrace();
                    } finally {
                        // 6. 强制清理所有资源（无论是否成功）

                    }
                    //isCameraFlag = true;
                }
            }
        } catch (Exception e) {
            handleError(e);
        } finally {
            //stopVideoStream();
        }
    }
    // 图像转灰度
    private byte[] convertToGray(BufferedImage img) {
        int w = img.getWidth();
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
        return gray;
    }
    // 临时对象清理方法
    private void cleanupTempResources(byte[] bytes, InputStream is, BufferedImage img) {
        try {
            if (is != null) is.close();  // 显式关闭流（ByteArrayInputStream实际无需关闭）
        } catch (IOException e) {
            // 忽略关闭异常
        }

        // 清空大对象引用
        bytes = null;
        img = null;
    }

    // 关键资源释放方法
    private boolean needForceGC() {
        // 根据内存阈值判断是否需要GC（示例逻辑）
        long maxMemory = Runtime.getRuntime().maxMemory();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return (usedMemory > maxMemory * 0.75);
    }
    private synchronized void stopVideoStream() {
        System.out.println("释放资源");
        try {
            if (processor != null) {
                processor.release();  // 确保SDK内部资源释放
                //processor = null;     // 帮助GC回收
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (manager != null) {
                manager.closeCamera(); // 确保关闭硬件连接
                //manager = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 可选：触发GC（仅在必要时）
        if (needForceGC()) {
            //执行了gc
            System.out.println("需要GC");
            System.gc();
        }
        try {
            if (activeSession != null && activeSession.isOpen()) {
                activeSession.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isStreaming.set(false);
            activeSession = null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        stopVideoStream();
    }

    private void handleError(Exception e) {
        try {
            if (activeSession != null && activeSession.isOpen()) {
                safeSendMessage(new TextMessage(
                        "{\"error\":\"" + e.getMessage() + "\"}"
                ));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        stopVideoStream();
    }
    // 注册提示常量
    interface EnrollTip {
        int PV_TIP_INPUT_PALM = 1;
        int PV_TIP_MOVE_FARAWAY = 2;
        int PV_TIP_MOVE_CLOSER = 3;
        int PV_TIP_INVALID_BRIGHT = 4;
        int PV_TIP_KEEP_PALM_STABLE = 5;
        int PV_TIP_KEEP_PALM_DIRECTION = 6;
        int PV_TIP_MOVE_PALM_DOWN = 7;
        int PV_TIP_MOVE_PALM_UP = 8;
        int PV_TIP_MOVE_PALM_LEFT = 9;
        int PV_TIP_MOVE_PALM_RIGHT = 10;
        int PV_TIP_CAP_SUCCESS = 20;
        int PV_TIP_ENROLL_FINISH = 100;
    }
    public sPalmInfo getPalmInfoSafely(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        Object obj = map.get("sPalmInfo");
        if (obj instanceof sPalmInfo) {
            return (sPalmInfo) obj;
        }

        return null;
    }
    public String getPalmInfoJsonString(Map<String, Object> map) {
        sPalmInfo palmInfo = getPalmInfoSafely(map);
        if (palmInfo == null) {
            return null;
        }

        // 转换为无符号值
        int x = palmInfo.x & 0xFFFF;
        long y = palmInfo.y & 0xFFFFFFFFL;
        int width = palmInfo.width & 0xFFFF;
        int height = palmInfo.height & 0xFFFF;

        // 创建一个临时的Map或DTO来保存数据
        Map<String, Object> data = Map.of(
                "x", x,
                "y", y,
                "width", width,
                "height", height
        );

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

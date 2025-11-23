package org.example.javacamerserver.xrCamer;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;


//定义 SDK 返回码常量
interface VeinReturnCode {
    int PV_OK = 0;
    int PV_ERR = -1;
    int PV_ERR_NO_DEVICE = -2;
    int PV_ERR_NULL_PTR = -3;
    int PV_ERR_PARAM = -4;
    int PV_ERR_UNSUPPORT = -5;
    int PV_ERR_HANDLE = -6;
    int PV_ERR_NO_HAND = -7;
    int PV_ERR_MEM = -8;
    int PV_ERR_BROKEN_FEAT = -9;

    // 设备错误
    int PV_ERR_DEV_NOT_OPEN = -20;
    int PV_ERR_DEV_BAD_IMG = -21;
    int PV_ERR_DEV_TIMEOUT = -22;
    int PV_ERR_DEV_PARAM = -23;
    int PV_ERR_DEV_UNKNOWN = -24;
    int PV_ERR_NOT_SAME_FINGER = -25;
    int PV_ERR_FEAT_DUP = -26;

    // 算法错误
    int PV_ERR_LOAD_ALGO_LIB = -30;
    int PV_ERR_EXTRACT_FEAT = -31;
    int PV_ERR_QUALITY_LOW_LIGHT = -32;
    int PV_ERR_QUALITY_HIGH_LIGHT = -33;
    int PV_ERR_QUALITY_BAD_TEXTURE = -34;
    int PV_ERR_QUALITY_BAD_IMG = -35;
    int PV_ERR_QUALITY_LIVENESS = -36;
    int PV_ERR_QUALITY_PALM_MOVE = -37;
    int PV_ERR_LOW_QUALITY = -38;

    // USB 错误
    int PV_ERR_USB_INIT = -40;
    int PV_ERR_USB_PERMISION = -41;
    int PV_ERR_USB_TIMEOUT = -42;
    int PV_ERR_USB_BROKEN_PKT = -43;
    int PV_ERR_USB_TRANSFER = -44;
    int PV_ERR_OPEN_DEV = -45;
    int PV_ERR_USB_UNKNOWN = -48;

    // 数据库错误
    int PV_ERR_NO_USER_ID = -60;
    int PV_ERR_USER_ID_DUP = -61;
    int PV_ERR_USER_DB_FULL = -62;
    int PV_ERR_USER_FEAT_DUP = -63;
    int PV_ERR_FILE_NOT_FOUND = -65;

    // 网络错误
    int PV_ERR_RUN_NET = -70;

    // 文件错误
    int PV_ERR_OPEN_FILE = -81;

    // 授权错误
    int PV_ERR_LIC = -1000;

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

// 特征比对常量
interface FeatureConstants {
    int XR_VEIN_FEATURE_INFO_SIZE = 1036;
    int XR_Buf_FEATURE_INFO_SIZE = 4608;
    float XR_VEIN_THRESH = 0.95f;
}


//定义 JNA 接口
public interface XRCommonVeinAlgAPI extends Library {

    //加载 dll文件
    XRCommonVeinAlgAPI INSTANCE = Native.load("XRCommonVeinAlgAPI", XRCommonVeinAlgAPI.class);


    /**
     * 获取SDK的版本号
     * @param pVersionBuf 版本号缓冲区，最少64字节
     * @param pBufLen 缓冲区长度，最少64字节，成功返回字符串长度
     * @return 成功返回PV_OK，失败返回错误码
     * 示例：“xr_common_vein_v2.0.0"
     */
    /**
     * Get the SDK version number
     * @param pVersionBuf Version number buffer, at least 64 bytes
     * @param pBufLen Buffer length, at least 64 bytes. Returns the string length on success.
     * @return Returns PV_OK on success, an error code on failure.
     * Example: "xr_common_vein_v2.0.0"
     */
    int XR_Vein_GetVersion(byte[] pVersionBuf, IntByReference pBufLen);

    /**
     * 初始化SDK
     * @param ppAlgHandle SDK的句柄指针
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Initialize the SDK
     * @param ppAlgHandle SDK handle pointer
     * @return PV_OK on success, error code on failure
     */
    int XR_Vein_Init(PointerByReference ppAlgHandle);


    /**
     * 释放 SDK 句柄
     * @param pDevHandle SDK的句柄
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Release the SDK handle
     * @param pDevHandle SDK handle
     * @return Returns PV_OK on success, an error code on failure
     */
    int XR_Vein_DeInit(Pointer pDevHandle);

    /**
     * 获取当前鉴权码
     * @param pDevHandle SDK的句柄
     * @param pCodeBuf 鉴权码缓冲区，不少于16个字节
     * @param pLen 输入为鉴权码缓冲区长度，输出为鉴权码实际长度
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Get the current authentication code
     * @param pDevHandle SDK handle
     * @param pCodeBuf Authentication code buffer, at least 16 bytes
     * @param pLen Input: Authentication code buffer length; output: actual authentication code length
     * @return Returns PV_OK on success; returns an error code on failure
     */
    int XR_Vein_GetLicCode(Pointer pDevHandle, byte[] pCodeBuf, IntByReference pLen);


    /**
     * 使用 Licence 激活当前 SDK
     * @param pDevHandle SDK的句柄
     * @param pLicBuf 激活码缓冲区
     * @param len 激活码长度
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Activate the current SDK using a license
     * @param pDevHandle SDK handle
     * @param pLicBuf activation code buffer
     * @param len activation code length
     * @return PV_OK on success, error code on failure
     */
    int XR_Vein_ActivateVeinSDK(Pointer pDevHandle, byte[] pLicBuf, int len);


    /**
     * 初始化注册器
     * @param pDevHandle SDK的句柄
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Initialize the register
     * @param pDevHandle SDK handle
     * @return PV_OK on success, error code on failure
     */
    int XR_Vein_InitEnrollEnv(Pointer pDevHandle);


    /**
     * 录入掌静脉图像
     * @param pDevHandle SDK的句柄
     * @param pImgBuf 掌静脉灰度图片
     * @param imgRows 输入图像的高度
     * @param imgCols 输入图像的宽度
     * @param livenessFlag 活体判断标志位，0：不进行活体判断，1：检测带活体判断
     * @param pEnrollStep 注册进度指针，数值为0-5
     * @param pEnrollTip 注册提示
     * @param pPalmInfo 掌检测信息，status非0表示检测成功
     * @param pHighBright 图像高亮区域（80x80）平均亮度信息
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Record a palm vein image
     * @param pDevHandle SDK handle
     * @param pImgBuf Palm vein grayscale image
     * @param imgRows Input image height
     * @param imgCols Input image width
     * @param livenessFlag Liveness detection flag (0: no liveness detection, 1: detection with liveness detection)
     * @param pEnrollStep Enrollment progress pointer (value 0-5)
     * @param pEnrollTip Enrollment prompt
     * @param pPalmInfo Palm detection information (status non-0 indicates successful detection)
     * @param pHighBright Average brightness information of the image highlight area (80x80)
     * @return PV_OK on success, error code on failure
     */
    int XR_Vein_TryEnroll(Pointer pDevHandle, byte[] pImgBuf, int imgRows, int imgCols,
                          int livenessFlag, IntByReference pEnrollStep, IntByReference pEnrollTip,
                          sPalmInfo pPalmInfo, byte[] pHighBright);


    /**
     * 获取最终掌静脉特征
     * @param pDevHandle SDK的句柄
     * @param pRoiImgBuf ROI图像缓冲区,可以通过此图像进行特征值提取，进而获取到注册特征值,为NULL则不返回图像
     * @param pRoiImgBufLen ROI图像缓冲区长度，默认大小为160x160
     * @param pRegFeatBuf 注册特征缓冲区
     * @param pBufLen 特征缓冲区的长度，不小于1KB,成功返回特征值实际长度
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Get the final palm vein feature
     * @param pDevHandle SDK handle
     * @param pRoiImgBuf ROI image buffer, which can be used to extract feature values ​​and obtain registration feature values. If NULL, no image is returned.
     * @param pRoiImgBufLen ROI image buffer length, default size is 160x160
     * @param pRegFeatBuf Registration feature buffer
     * @param pBufLen Length of the feature buffer, minimum 1KB. Returns the actual feature value length on success.
     * @return PV_OK on success, error code on failure
     */
    // 修正后的 JNA 接口定义
    int XR_Vein_FinishEnroll(
            Pointer pDevHandle,
            Pointer pRoiImgBuf,  // 用 Pointer 替代 byte[]，传递原生内存指针
            IntByReference pRoiImgBufLen,
            Pointer pRegFeatBuf, // 特征缓冲区同样使用 Pointer
            IntByReference pBufLen
    );


    /**
     * 从原始图像检测并提取的掌静脉特征，仅用于识别，不建议用于注册
     * @param pDevHandle SDK的句柄
     * @param pImgBuf 掌静脉灰度图片
     * @param imgRows 输入图像的高度
     * @param imgCols 输入图像的宽度
     * @param livenessFlag 活体判断标志位，0：不进行活体判断，1：检测带活体判断
     * @param pFeatBuf 特征缓冲区
     * @param pBufLen 特征缓冲区的长度，不小于4KB,成功返回特征值实际长度
     * @param pCapTip 识别提示
     * @param pPalmInfo 手掌检测信息，status非0表示检测成功
     * @param pHighBright 像高亮区域（80x80）平均亮度信息
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Palm vein features detected and extracted from the original image. Used only for recognition and not for registration.
     * @param pDevHandle SDK handle
     * @param pImgBuf Palm vein grayscale image
     * @param imgRows Input image height
     * @param imgCols Input image width
     * @param livenessFlag Liveness detection flag. 0: Do not perform liveness detection; 1: Perform liveness detection.
     * @param pFeatBuf Feature buffer
     * @param pBufLen Feature buffer length (minimum 4KB). Returns the actual length of the feature value upon success.
     * @param pCapTip Recognition hint
     * @param pPalmInfo Palm detection information. A non-zero status indicates successful detection.
     * @param pHighBright Average brightness information of the highlighted area (80x80 pixels) of the image.
     * @return Returns PV_OK on success; returns an error code upon failure.
     */
    int XR_Vein_GrabFeatureFromFullImg(
            Pointer pDevHandle,
            Pointer pImgBuf,    // 原byte[]改为Pointer（支持Memory）
            int imgRows,
            int imgCols,
            int livenessFlag,
            Pointer pFeatBuf,   // 原byte[]改为Pointer（支持Memory）
            IntByReference pBufLen,
            IntByReference pCapTip,
            sPalmInfo pPalmInfo,
            Pointer pHighBright // 原byte[]改为Pointer（支持Memory）
    );

    /**
     * 从注册图像提取注册特征值； 一般用于远程同步；
     * @param pDevHandle SDK的句柄
     * @param pImgBuf 输入掌静脉ROI图像，灰度图，数据必须来源于XR_Vein_FinishEnroll的pRoiImgBuf
     * @param imgRows 输入图像的高度
     * @param imgCols 输入图像的宽度
     * @param pFeatBuf 注册特征缓冲区
     * @param pBufLen 特征缓冲区的长度，不小于4KB,成功返回特征值实际长度(1036字节)
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Extracts registered feature values ​​from the registered image; generally used for remote synchronization.
     * @param pDevHandle SDK handle
     * @param pImgBuf Input palm vein ROI image (grayscale image). The data must come from pRoiImgBuf in XR_Vein_FinishEnroll
     * @param imgRows Input image height
     * @param imgCols Input image width
     * @param pFeatBuf Registration feature buffer
     * @param pBufLen Feature buffer length (minimum 4KB). Success returns the actual feature value length (1036 bytes).
     * @return Returns PV_OK on success, an error code on failure
     */
    int XR_Vein_GrabFeatureFromEnrollRoiImg(Pointer pDevHandle, byte[] pImgBuf, int imgRows,
                                            int imgCols, byte[] pFeatBuf, IntByReference pBufLen);


    /**
     * 校验掌静脉特征的合法性
     * @param pFeatBuf
     * @param bufLen
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Verify the validity of the palm vein feature
     * @param pFeatBuf
     * @param bufLen
     * @return PV_OK on success, error code on failure
     */
    int XR_Vein_CheckFeat(byte[] pFeatBuf, int bufLen);


    /**
     * 比对掌静脉特征之间距离
     * @param pFeatBuf1 掌静脉特征缓冲区1
     * @param bufLen1 掌静脉特征缓冲区1长度
     * @param pFeatBuf2 掌静脉特征缓冲区2
     * @param bufLen2 掌静脉特征缓冲区2长度
     * @param pDist 掌静脉特征比较距离，阈值在0.9-1.0之间,建议阈值0.95f;
     * @return 成功返回PV_OK，失败返回错误码
     */
    /**
     * Compare the distances between palm vein features.
     * @param pFeatBuf1 Palm vein feature buffer 1
     * @param bufLen1 Palm vein feature buffer 1 length
     * @param pFeatBuf2 Palm vein feature buffer 2
     * @param bufLen2 Palm vein feature buffer 2 length
     * @param pDist Palm vein feature comparison distance. The threshold is between 0.9 and 1.0, with a recommended threshold of 0.95f.
     * @return Returns PV_OK on success, an error code on failure.
     */
    int XR_Vein_CalcFeatureDist(byte[] pFeatBuf1, int bufLen1, byte[] pFeatBuf2, int bufLen2,
                                FloatByReference pDist );// 使用指针引用类型

    /**
     * function: 比对掌静脉特征之间距离(安卓特征值)
     * pFeatBuf1: 掌静脉特征缓冲区1
     * bufLen1： 掌静脉特征缓冲区1长度
     * pFeatBuf2: 掌静脉特征缓冲区2
     * bufLen2: 掌静脉特征缓冲区2长度
     * pScore： 掌静脉特征比较距离，超过0.5可以认为识别通过
     */
    /**
     * Function: Compares the distance between palm vein features (Android feature value)
     * pFeatBuf1: Palm vein feature buffer 1
     * bufLen1: Palm vein feature buffer 1 length
     * pFeatBuf2: Palm vein feature buffer 2
     * bufLen2: Palm vein feature buffer 2 length
     * pScore: Palm vein feature comparison distance. A score exceeding 0.5 indicates a successful recognition.
     */
    int XR_Vein_CalcFeatureDistByFloat(float[] pFeatBuf1, int bufLen1, float[] pFeatBuf2, int bufLen2,
                                       FloatByReference pScore);

    /*
    以下接口为多模态掌静脉比对引擎接口，上述为单独掌静脉特征值，如果需要加入比对引擎，需要进行特征格式转换
    */

    /*
     * function: 初始化数据库SDK
     * ppDataHandle: 数据库句柄指针
     * maxUserNum： 本地数据库最大容量,数值必须大于0
     * return: 成功返回PV_OK，失败返回错误码
     * 补充说明：本地数据库为非持久化数据库，SDK释放后会自动释放数据库空间；
     */

    int XR_Palm_Data_Init(PointerByReference ppDataHandle,  int maxUserNum);


    /*
     * function: 释放数据库SDK
     * pDataHandle: 数据库句柄指针
     * return: 成功返回PV_OK，失败返回错误码
     */

    int XR_Palm_Data_DeInit(Pointer pDataHandle);


    /*
     * function: 特征格式转换接口，将安卓端输出的特征值转换为后台比对特征值
     * pFloatFeatBuf: 浮点型特征值
     * float_feat_len: 浮点型特征值长度, 数值只能选1024或512;单掌静脉固定512，掌纹掌静脉固定1024；且特征值组合为掌静脉（512） + 掌纹（512）
     * pIntFeatBuf: 定点型特征值
     * int_feat_len: 定点型特征值缓冲区长度，固定2096
     * return: 成功返回PV_OK，失败返回错误码
     */

    int XR_Palm_Data_CvtFloatFeatToInt16Feat(float[] pFloatFeatBuf, int float_feat_len, byte[] pIntFeatBuf, int int_feat_len);


    /*
     * function: 特征格式转换接口，将Windows输出的特征值转换为后台比对特征值
     * pWinFeatBuf: Windows特征值
     * feat_len: Windows特征值长度, 数值只能选1036
     * pIntFeatBuf: 定点型特征值
     * int_feat_len: 定点型特征值缓冲区长度，固定2096
     * return: 成功返回PV_OK，失败返回错误码
     */

    int XR_Palm_Data_CvtWinFeatToInt16Feat(byte[] pWinFeatBuf, int feat_len, byte[] pIntFeatBuf, int int_feat_len);


    /*
     * function： 添加新用户特征到数据库
     * pDataHandle: SDK的句柄
     * userId: 用户ID，数值不能为0
     * pFeatBuf: 注册特征缓冲区
     * bufLen: 注册特征缓冲区长度，不小于2096字节
     * return: 成功返回PV_OK，失败返回错误码
     */
    int XR_Palm_Data_AddUser(Pointer pDataHandle, int userId, byte[] pFeatBuf, int bufLen);

    /*
     * function: 从数据库中删除用户
     * pDataHandle: SDK的句柄
     * userId: 待删除用户的ID
     * return: 成功返回PV_OK，失败返回错误码
     */
    int XR_Palm_Data_DelUser(Pointer pDataHandle, int userId);

    /*
     * function: 清空数据库中的用户
     * pDataHandle: SDK的句柄
     * return: 成功返回PV_OK，失败返回错误码
     */
    int XR_Palm_Data_ClearUser(Pointer pDataHandle);

    /*
     * function: 获取数据库内用户数
     * pDataHandle: SDK的句柄
     * pNum: 用户数指针
     * return: 成功返回PV_OK， 失败返回错误码
     */
    int XR_Palm_Data_GetUserNum(Pointer pDataHandle,int pNum);

    /*
     * function: 获取数据库内用户的特征值
     * pDataHandle: SDK的句柄
     * userId: 待获取的用户ID
     * pFeatBuf: 注册特征缓冲区
     * bufLen： 注册特征缓冲区长度，不小于2096字节;
     */
    int XR_Palm_Data_QueryUser(Pointer pDataHandle,int userId,byte[] pFeatBuf,int bufLen);

    /*
     * function: 获取数据库内的用户列表
     * pDataHandle: SDK的句柄
     * pUserList： 用户ID列表
     * listLen: 用户ID列表长度（不是缓冲区长度，为用户ID的数量），不小于初始化的maxUserNum;
     * pRealLen: 实际用户ID的数量
     * return: 成功返回PV_OK，失败返回错误码
     */

    int XR_Palm_Data_GetUserList(Pointer pDataHandle, int[] pUserList, int listLen, int[] pRealLen);


    /*
     * function: 校验特征的合法性
     * pFeatBuf: 特征缓冲区
     * bufLen：特征缓冲区长度，不小于2096字节
     */
    int XR_Palm_CheckFeat(byte[] pFeatBuf, int bufLen);


    /*
     * function: 检索手掌特征
     * pDatHandle: 数据库SDK的句柄
     * pFeatBuf: 比对特征缓冲区
     * pRes: 比对结果指针, 0:比对失败， 非0：比对通过
     * pReUserId: 比对通过的用户ID
     * pScore: 比对通过的得分
     * return: 成功返回PV_OK，失败返回错误码
     */
    int XR_Palm_Identity(Pointer pDataHandle,byte[] pFeatBuf, IntByReference pRes, IntByReference pResUserId, FloatByReference pScore);


    /*
     * function: 比对手掌特征
     * pDatHandle: 数据库SDK的句柄
     * pFeatBuf1: 比对特征缓冲区1
     * pFeatBuf1: 比对特征缓冲区2
     * pRes: 比对结果指针, 0:比对失败， 非0：比对通过
     * pScore: 比对通过的得分
     * return: 成功返回PV_OK，失败返回错误码
     */
    int XR_Palm_Verify(byte[] pFeatBuf1, byte[] pFeatBuf2, int pRes, FloatByReference pScore);

}

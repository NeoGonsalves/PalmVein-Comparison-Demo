package org.example.javacamerserver.web.controller;

import org.example.javacamerserver.utils.AjaxResult;
import org.example.javacamerserver.utils.ThreadPoolService;
import org.example.javacamerserver.web.domain.PalmComparisonResult;
import org.example.javacamerserver.web.domain.TdxConfig;
import org.example.javacamerserver.web.domain.TdxPalm;
import org.example.javacamerserver.web.service.ITdxConfigService;
import org.example.javacamerserver.web.service.ITdxPalmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
@RestController
@RequestMapping("/api")
public class TdxPalmController {

    @Autowired
    private ITdxPalmService iTdxPalmService;
    @Autowired
    private ITdxConfigService tdxConfigService;

    @PostMapping("/saveFeature")
    public String saveFeature(@RequestParam("file") MultipartFile file, @RequestParam String name) {
        try {
            // 临时保存上传的 ZIP 文件
            File tempZipFile = File.createTempFile("uploaded_", ".zip");
            file.transferTo(tempZipFile);

            // 处理 ZIP 文件
            processZipFile(tempZipFile,name);

            // 删除临时文件
            tempZipFile.delete();

            return "文件上传并处理成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "文件上传失败: " + e.getMessage();
        }
    }
    private void processZipFile(File zipFile,String name) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int i=0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                // 只处理 bin 文件
                if (entry.getName().endsWith(".bin")) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        // 读取 bin 文件内容
                        byte[] bytes = is.readAllBytes();
                        // 将字节数组转换为字符串
                        String content = convertBytesToString(bytes);
                        // 保存字符串内容（这里简单打印，实际应用中可保存到文件或数据库）
                        System.out.println("处理文件: " + entry.getName());
                        System.out.println("内容: " + content);
                        TdxPalm tdxPalm = new TdxPalm();
                        tdxPalm.setPalmId(name+i);
                        tdxPalm.setFeature(content);
                        iTdxPalmService.saveTdxPalm(tdxPalm);
                        i++;
                        // 保存到文件示例
                        //saveToFile(content, entry.getName());
                    }
                }
            }
        }
    }
    private String convertBytesToString(byte[] bytes) {
        // 使用 Base64 编码将字节数组转换为字符串
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * @Description: 掌纹比对windows版
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:43
     */
    @PostMapping("/comparison")
    public AjaxResult palmComparison(@RequestBody Map<String, String> map)
    {
        String feature = null;
        if (map==null|| map.get("palmFeature")==null){
            return AjaxResult.error("Parameter error");
        }
        feature= map.get("palmFeature").toString();
        // 获取Bean并保存
        PalmComparisonResult palmComparisonResult=iTdxPalmService.palmComparison(feature);
        return AjaxResult.success(palmComparisonResult);
    }

    /**
     * @Description: 掌纹比对windows版
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:43
     */
    @GetMapping("/comparisonByPictureBase64")
    public AjaxResult palmComparisonByPictureBase64(@RequestBody Map<String, String> map)
    {
        String palmImg = null;
        if (map==null|| map.get("palmImg")==null){
            return AjaxResult.error("Parameter error");
        }
        palmImg= map.get("palmImg").toString();
        Map<String,Object> objectMap =iTdxPalmService.getPalmFeature(palmImg);
        //分数值
        Float recognitionScore = 0F;
        if (objectMap.get("baseFeature")!=null&& objectMap.get("index")!=null&&Integer.valueOf(objectMap.get("index").toString())==1){
            PalmComparisonResult palmComparisonResult=iTdxPalmService.palmComparison(objectMap.get("baseFeature").toString());
            TdxConfig tdxConfig = tdxConfigService.selectTdxConfigByConfig("recognition_score");
            if (null==tdxConfig){
                recognitionScore = 0.95F;
            } else {
                recognitionScore = tdxConfig.getValue();
            }
            if (palmComparisonResult.getScore()<=recognitionScore){
                return AjaxResult.success(palmComparisonResult);
            }else {
                return AjaxResult.error("比对失败",palmComparisonResult);
            }
        }else {
            return AjaxResult.error("照片无法获取特征值");
        }
    }
    /**
     * @Description: 保存掌静脉数据
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:44
     */
    @PostMapping("/merge")
    public AjaxResult merge(@RequestBody Map<String, String> map){
        if (map==null|| map.get("palmFeature")==null|| map.get("palmId")==null){
            return AjaxResult.error("Parameter error");
        }
        String palmId =map.get("palmId");
        String palmFeature =map.get("palmFeature");
        String palmImg =map.get("palmImg");
        TdxPalm tdxPalm = iTdxPalmService.selectTdxPalmByPalmId(palmId);
        if (tdxPalm==null){
            tdxPalm = new TdxPalm();
            tdxPalm.setPalmId(palmId);
            tdxPalm.setPalmType(1);
            tdxPalm.setFeature(palmFeature);
            tdxPalm.setPalmImg(palmImg);
            return AjaxResult.toAjax(iTdxPalmService.saveTdxPalm(tdxPalm));
        }else {
            return AjaxResult.error("This palmprint id already exists");
        }
    }
    /**
     * @Description: 保存掌静脉数据
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:44
     */
    @PostMapping("/mergeAndroid")
    public AjaxResult mergeAndroid(@RequestBody Map<String, String> map){
        if (map==null|| map.get("palmFeature")==null|| map.get("palmId")==null){
            return AjaxResult.error("Parameter error");
        }
        String palmId =map.get("palmId");
        String palmFeature =map.get("palmFeature");
        String palmImg =map.get("palmImg");
        TdxPalm tdxPalm = iTdxPalmService.selectTdxPalmByPalmId(palmId);
        if (tdxPalm==null){
            tdxPalm = new TdxPalm();
            tdxPalm.setPalmId(palmId);
            tdxPalm.setPalmType(2);
            tdxPalm.setFeature(palmFeature);
            tdxPalm.setPalmImg(palmImg);
            return AjaxResult.toAjax(iTdxPalmService.saveTdxPalm(tdxPalm));
        }else {
            return AjaxResult.error("This palmprint id already exists");
        }
    }
    /*
     * @Description: 查找掌静脉数据
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:37
     */
    @GetMapping("/findPalm")
    public AjaxResult findPalm(String palmId)
    {
        if (palmId==null|| palmId.trim().equals("")){
            return AjaxResult.error("Parameter error");
        }
        TdxPalm tdxPalm = iTdxPalmService.selectTdxPalmByPalmId(palmId);
        if (tdxPalm==null){
            return AjaxResult.error("No palm print information was found");
        }else {
            return AjaxResult.success(tdxPalm);
        }
    }
    /**
     * @Description: 保存掌静脉数据
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:44
     */
    @PostMapping("/updatePalm")
    public AjaxResult updatePalm(@RequestBody Map<String, String> map){
        if (map==null|| map.get("palmFeature")==null|| map.get("palmId")==null){
            return AjaxResult.error("Parameter error");
        }
        String palmId =map.get("palmId");
        String palmFeature =map.get("palmFeature");
        String palmImg =map.get("palmImg");
        TdxPalm tdxPalm = iTdxPalmService.selectTdxPalmByPalmId(palmId);
        if (tdxPalm!=null){
            tdxPalm = new TdxPalm();
            tdxPalm.setPalmId(palmId);
            tdxPalm.setFeature(palmFeature);
            tdxPalm.setPalmImg(palmImg);
            return AjaxResult.toAjax(iTdxPalmService.updateTdxPalm(tdxPalm));
        }else {
            return AjaxResult.error("Palmprint data does not exist");
        }
    }

    /**
     * @Description: 删除掌静脉数据接口
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:37
     */
    @GetMapping("/deletePalm")
    public AjaxResult deletePalm(String palmId){
        if (palmId==null|| palmId.trim().equals("")){
            return AjaxResult.error("Parameter error");
        }
        TdxPalm tdxPalm = iTdxPalmService.selectTdxPalmByPalmId(palmId);
        if (tdxPalm==null){
            return AjaxResult.error("No palm print information was found");
        }else {
            iTdxPalmService.removeFeatureFromRedis(tdxPalm.getId());
            return AjaxResult.toAjax(iTdxPalmService.deleteTdxPalm(tdxPalm));
        }
    }
    /**
     * @Description: 刷新掌静脉缓存接口
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:37
     */
    @GetMapping("/refreshPalmFeatures")
    public AjaxResult refreshPalmFeatures(){
        ThreadPoolService.getInstance().execute(() -> {
            iTdxPalmService.clearPalmFeaturesCache();
        });
        return AjaxResult.success("The cache is being refreshed");
    }

    /*
     * @Description: 查找配置文件
     * @Param:
     * @Return:
     * @Author: fangzhaoyang
     * @Date: 2025/9/9 9:37
     */
    @GetMapping("/findConfig")
    public AjaxResult findConfig(String config)
    {
        if (config==null|| config.trim().equals("")){
            return AjaxResult.error("Parameter error");
        }
        TdxConfig tdxConfig = tdxConfigService.selectTdxConfigByConfig("recognition_score");
        if (tdxConfig==null){
            return AjaxResult.error("No palm print information was found");
        }else {
            return AjaxResult.success(tdxConfig);
        }
    }
}

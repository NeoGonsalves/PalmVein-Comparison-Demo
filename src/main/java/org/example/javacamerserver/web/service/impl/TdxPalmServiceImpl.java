package org.example.javacamerserver.web.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.javacamerserver.utils.AjaxResult;
import org.example.javacamerserver.web.domain.PalmComparisonResult;
import org.example.javacamerserver.web.domain.TdxConfig;
import org.example.javacamerserver.web.domain.TdxPalm;
import org.example.javacamerserver.web.mapper.TdxPalmMapper;
import org.example.javacamerserver.web.service.ITdxPalmService;
import org.example.javacamerserver.xrCamer.VeinProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.javacamerserver.utils.ByteBufferUtil.*;

@Service
public class TdxPalmServiceImpl extends ServiceImpl<TdxPalmMapper, TdxPalm> implements ITdxPalmService {

    @Autowired
    private TdxPalmMapper tdxPalmMapper;
    @Autowired  // 注入VeinProcessor
    private VeinProcessor veinProcessor;
    @Override
    public TdxPalm selectTdxPalmByPalmId(String palmId) {
        QueryWrapper<TdxPalm> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("palm_id", palmId);  // 指定查询条件
        return tdxPalmMapper.selectOne(queryWrapper);  // 返回单条结果
    }

    @Override
    public TdxPalm selectTdxPalmById(Integer id) {
        return tdxPalmMapper.selectById(id);
    }

    @Override
    public Integer saveTdxPalm(TdxPalm tdxPalm) {
        Integer sav =tdxPalmMapper.insert(tdxPalm);
        if (sav>0){
            saveFeatureToRedis(tdxPalm.getId(),tdxPalm.getPalmType(), tdxPalm.getFeature());
        }
        return sav;
    }

    @Override
    public Integer updateTdxPalm(TdxPalm tdxPalm) {
        veinProcessor.deleteUser(tdxPalm.getId());
        saveFeatureToRedis(tdxPalm.getId(),tdxPalm.getPalmType(), tdxPalm.getFeature());
        return tdxPalmMapper.updateById(tdxPalm);
    }

    @Override
    public List<TdxPalm> selectTdxPalmList() {
        // 正确查询所有数据的写法
        return tdxPalmMapper.selectList(new LambdaQueryWrapper<>());// tdxPalmMapper.selectList();
    }

    @Override
    public Integer deleteTdxPalm(TdxPalm tdxPalm) {
        removeFeatureFromRedis(tdxPalm.getId());
        return tdxPalmMapper.deleteById(tdxPalm);
    }

    @Override
    public PalmComparisonResult palmComparison(String baseFeature) {

        // 1. 转换输入特征
        byte[] featBuf1 = robustBase64Decode(baseFeature);
        try {
            Map<String, Object> data = veinProcessor.comparisonPalmWeb(featBuf1);
            String palmId=null;
            float pScore=4.0F;
            if (null!=data){
                /*objectMap.put("pScore",value);
            objectMap.put("pResUserId",pResUserId);*/
                if (data.get("pResUserId")!=null){
                    TdxPalm tdxPalm = selectTdxPalmById(Integer.valueOf(data.get("pResUserId").toString()));
                    if (null!=tdxPalm){
                        palmId=tdxPalm.getPalmId();
                    }
                }
                if (data.get("pScore")!=null){
                    pScore=Float.valueOf(data.get("pScore").toString());
                }
            }
            PalmComparisonResult result = new PalmComparisonResult(palmId,pScore);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return new PalmComparisonResult(null, 4.0f);
        }
        // 4. 返回结果（如果没有数据则返回默认值）
    }

    @Override
    public Map<String,Object> getPalmFeature(String baseImg) {
        byte[] imageBytes = Base64.getDecoder().decode(baseImg);
        // 3. 将 byte[] 转换为 BufferedImage
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        BufferedImage image = null;
        try {
            image = ImageIO.read(bis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String,Object> objectMap = new HashMap<>();
        // 处理图片

        //objectMap = processor.scanningComparison(image);
        //分数值
        return objectMap;
    }

    @Override
    public List<TdxPalm> listByBatch(int batchSize, int offset) {
        return baseMapper.selectList(null)
                .stream()
                .skip(offset)
                .limit(batchSize)
                .toList();
    }
    @Override
    public void saveFeatureToRedis(Integer id,Integer type, String feature) {
        //byte[] bytes = robustBase64Decode(feature);
        veinProcessor.addUser(id,type,feature);
        //redisTemplate.opsForHash().put("palm_features", palmId, bytes);
        //redisTemplate.opsForValue().set("palm_feature:" +palmId, bytes);
    }
    @Override
    public void removeFeatureFromRedis(Integer id) {
        // 从 Hash 结构中移除指定 palmId 的数据
        //redisTemplate.opsForHash().delete("palm_features", palmId);
        veinProcessor.deleteUser(id);
        // 如果需要同时清除 String 结构的数据（如果之前使用过）
        // redisTemplate.delete("palm_feature:" + palmId);
    }
    @Override
    public void batchSaveFeaturesToRedis(Map<String, String> featureMap) {
    }
    @Override
    public float[] getFeatureFromRedis(String palmId) {
        return null;
    }

    @Override
    public Long countAll() {
        return count();
    }

    @Override
    public void clearPalmFeaturesCache() {
        // 删除 "palm_features" 缓存
        veinProcessor.deleteAllUser();
        // 动态获取总记录数
        int totalCount = Integer.valueOf(countAll().toString());
        int batchSize = 40000;
        int threadCount = Math.min(20, (totalCount + batchSize - 1) / batchSize)==0?1:Math.min(20, (totalCount + batchSize - 1) / batchSize); // 最大10个线程，避免空线程
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        AtomicInteger offset = new AtomicInteger(0);
        AtomicInteger totalProcessed = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();
        System.out.println("开始导入数据，总数: " + totalCount + " 条");

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                int currentOffset;
                while ((currentOffset = offset.getAndAdd(batchSize)) < totalCount) {
                    try {
                        System.out.println("开始查询"+new Date());
                        List<TdxPalm> list = listByBatch(batchSize, currentOffset);
                        System.out.println("结束查询"+new Date());
                        if (list.isEmpty()) {
                            continue; // 防止分页边界问题
                        }
                        // 批量处理数据
                        for (TdxPalm palm : list) {
                            saveFeatureToRedis(palm.getId(),palm.getPalmType(), palm.getFeature());
                        }
                        int processed = list.size();
                        int total = totalProcessed.addAndGet(processed);
                        // 每处理10%数据打印进度
                        if (total % ((totalCount / 10)==0?1:(totalCount / 10)) < processed) {
                            double progress = (double) total / totalCount * 100;
                            System.out.printf("进度: %.1f%%，已处理: %d/%d 条%n", progress, total, totalCount);
                        }
                    } catch (Exception e) {
                        System.err.println("处理偏移量 " + currentOffset + " 时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        }
        executorService.shutdown();
        long endTime = System.currentTimeMillis();
        System.out.printf("所有数据已处理完成，共导入: %d 条，耗时: %.2f 秒%n",
                totalProcessed.get(), (endTime - startTime) / 1000.0);
    }

}

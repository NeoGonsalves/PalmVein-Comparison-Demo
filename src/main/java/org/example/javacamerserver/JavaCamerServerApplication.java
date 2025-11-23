package org.example.javacamerserver;

import org.example.javacamerserver.web.domain.TdxPalm;
import org.example.javacamerserver.web.service.ITdxPalmService;
import org.example.javacamerserver.xrCamer.VeinProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class JavaCamerServerApplication implements CommandLineRunner {

    @Autowired
    private ITdxPalmService tdxPalmService;
    @Autowired // 直接注入，Spring会管理单例
    private VeinProcessor veinProcessor;
    public static void main(String[] args) {
        SpringApplication.run(JavaCamerServerApplication.class, args);
    }
    @Override
    public void run(String... args) throws Exception {
        // 初始化VeinProcessor
        try {
            veinProcessor.init();
        } catch (Exception e) {
            System.err.println("VeinProcessor初始化失败: " + e.getMessage());
            e.printStackTrace();
            return; // 初始化失败，停止执行
        }
        // 动态获取总记录数
        int totalCount = Integer.valueOf(tdxPalmService.countAll().toString());
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
                        List<TdxPalm> list = tdxPalmService.listByBatch(batchSize, currentOffset);
                        System.out.println("结束查询"+new Date());
                        if (list.isEmpty()) {
                            continue; // 防止分页边界问题
                        }

                        // 批量处理数据
                        for (TdxPalm palm : list) {
                            tdxPalmService.saveFeatureToRedis(palm.getId(),palm.getPalmType(), palm.getFeature());
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
        while (!executorService.isTerminated()) {
            Thread.sleep(1000);
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("所有数据已处理完成，共导入: %d 条，耗时: %.2f 秒%n",
                totalProcessed.get(), (endTime - startTime) / 1000.0);
    }

}

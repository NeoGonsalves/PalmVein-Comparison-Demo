package org.example.javacamerserver.utils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
public class ThreadPoolSingleton {
    // 线程池单例实例
    private static ThreadPoolSingleton instance;

    // 线程池对象
    private ThreadPoolExecutor threadPool;

    // 最大线程数
    private static final int MAX_THREADS = Math.min(5000, Runtime.getRuntime().availableProcessors() * 8);
    // 私有构造函数，防止外部实例化
    private ThreadPoolSingleton() {
        // 创建线程池，核心线程数为处理器核心数的2倍，最大线程数为1000
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        threadPool = new ThreadPoolExecutor(
                corePoolSize,
                MAX_THREADS,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5000), // 使用有界队列
                new ThreadFactory() {
                    private int count = 0;

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("palm-comparison-thread-" + count++);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    // 获取单例实例
    public static synchronized ThreadPoolSingleton getInstance() {
        if (instance == null) {
            instance = new ThreadPoolSingleton();
        }
        return instance;
    }

    // 提交任务并返回Future
    public <T> Future<T> submit(Callable<T> task) {
        return threadPool.submit(task);
    }

    // 关闭线程池
    public void shutdown() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
    }

    // 获取线程池状态
    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }
}

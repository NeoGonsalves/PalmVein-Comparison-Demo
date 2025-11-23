package org.example.javacamerserver.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池服务
 *
 * @author Cosmo(wechat : wcosmo)
 * @date 2020-07-29
 */
public class ThreadPoolService {

    /**
     * 核心线程数
     */
    private static final int DEFAULT_CORE_SIZE = 10;
    /**
     * 最大线程数
     */
    private static final int MAX_QUEUE_SIZE = 20;
    private static final int KEEP_ALIVE_TIME = 60;
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MINUTES;

    private static ThreadPoolExecutor executor;

    /**
     * 获取单例线程池对象
     */
    public static ThreadPoolExecutor getInstance() {
        if (null == executor) {
            synchronized (ThreadPoolService.class) {
                if (null == executor) {
                    executor = new ThreadPoolExecutor(DEFAULT_CORE_SIZE, MAX_QUEUE_SIZE, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, new LinkedBlockingQueue<>(), Executors
                            .defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
                }
            }
        }
        return executor;
    }

    /**
     * 执行线程
     *
     * @param runnable 线程对象
     */
    public void execute(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        executor.execute(runnable);
    }

    /**
     * 从线程队列中移除对象
     *
     * @param runnable 线程对象
     */
    public void cancel(Runnable runnable) {
        if (executor != null) {
            executor.getQueue().remove(runnable);
        }
    }
}

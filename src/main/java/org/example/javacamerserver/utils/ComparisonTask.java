package org.example.javacamerserver.utils;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/

import org.example.javacamerserver.xrCamer.VeinProcessor;

import java.util.concurrent.Callable;

/**
 * 掌静脉特征比较任务
 */
public class ComparisonTask implements Callable<Float> {
    private final VeinProcessor processor;
    private final float[] featBuf1;
    private final byte[] featBuf2;

    public ComparisonTask(VeinProcessor processor, float[] featBuf1, byte[] featBuf2) {
        this.processor = processor;
        this.featBuf1 = featBuf1;
        this.featBuf2 = featBuf2;
    }

    @Override
    public Float call() throws Exception {
        return processor.comparisonPalm(featBuf1, featBuf2);
    }
}

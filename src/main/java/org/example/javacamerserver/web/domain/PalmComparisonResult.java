package org.example.javacamerserver.web.domain;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
public class PalmComparisonResult {
    private String palmId;
    private Float score;

    public PalmComparisonResult(String palmId, Float score) {
        this.palmId = palmId;
        this.score = score;
    }

    // getter方法
    public String getPalmId() {
        return palmId;
    }

    public Float getScore() {
        return score;
    }
}

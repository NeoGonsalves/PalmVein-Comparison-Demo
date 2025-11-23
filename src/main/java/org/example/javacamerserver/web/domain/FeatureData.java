package org.example.javacamerserver.web.domain;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
public class FeatureData {
    private String palmId;
    private float[] features;

    public FeatureData(String palmId, float[] features) {
        this.palmId = palmId;
        this.features = features;
    }

    public String getPalmId() {
        return palmId;
    }

    public float[] getFeatures() {
        return features;
    }
}

package org.example.javacamerserver.web.service;

import org.example.javacamerserver.web.domain.PalmComparisonResult;
import org.example.javacamerserver.web.domain.TdxPalm;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface ITdxPalmService {
    /**
     * 查询掌纹
     *
     * @param palmId 掌纹id
     * @return 掌纹信息
     */
    public TdxPalm selectTdxPalmByPalmId(String palmId);

    /**
     * 查询掌纹
     *
     * @param id 主键
     * @return 掌纹信息
     */
    public TdxPalm selectTdxPalmById(Integer id);

    /**
     * 保存掌纹
     *
     * @param tdxPalm 掌纹对象
     * @return 掌纹信息
     */
    public Integer saveTdxPalm(TdxPalm tdxPalm);

    /**
     * 修改掌纹
     *
     * @param tdxPalm 掌纹对象
     * @return 掌纹信息
     */
    public Integer updateTdxPalm(TdxPalm tdxPalm);

    /**
     * 查询所有掌纹
     *
     * @return 掌纹信息
     */
    public List<TdxPalm> selectTdxPalmList();

    /**
     * 删除掌纹
     *
     * @param tdxPalm 掌纹对象
     * @return 掌纹信息
     */
    public Integer deleteTdxPalm(TdxPalm tdxPalm);

    /**
     * 掌纹比对
     *
     * @param baseFeature 特征值
     * @return 掌纹信息
     */
    public PalmComparisonResult palmComparison(String baseFeature);

    /**
     * 通过图片获取特征值
     *
     * @param baseImg 掌纹对象
     * @return 掌纹信息
     */
    public Map<String,Object> getPalmFeature(String baseImg);

    /**
     * 同步掌纹数据
     *
     * @return 掌纹信息
     */
    /**
     * 同步掌纹特征数据到Redis
     */
    List<TdxPalm> listByBatch(int batchSize, int offset);
    void saveFeatureToRedis(Integer id, Integer type, String feature);

    void removeFeatureFromRedis(Integer id);

    void batchSaveFeaturesToRedis(Map<String, String> featureMap);

    float[] getFeatureFromRedis(String palmId);

    Long countAll();

    void clearPalmFeaturesCache();
}

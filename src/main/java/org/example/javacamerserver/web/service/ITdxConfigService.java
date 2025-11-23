package org.example.javacamerserver.web.service;


import org.example.javacamerserver.web.domain.TdxConfig;

public interface ITdxConfigService {
    /**
     * 查询掌纹
     *
     * @param id 配置id
     * @return 掌纹信息
     */
    public TdxConfig selectTdxConfigById(Integer id);

    /**
     * 查询掌纹
     *
     * @param config 配置id
     * @return 掌纹信息
     */
    public TdxConfig selectTdxConfigByConfig(String config);
}

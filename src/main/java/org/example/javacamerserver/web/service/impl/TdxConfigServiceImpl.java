package org.example.javacamerserver.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.javacamerserver.web.domain.TdxConfig;
import org.example.javacamerserver.web.domain.TdxPalm;
import org.example.javacamerserver.web.mapper.TdxConfigMapper;
import org.example.javacamerserver.web.mapper.TdxPalmMapper;
import org.example.javacamerserver.web.service.ITdxConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
@Service
public class TdxConfigServiceImpl  extends ServiceImpl<TdxPalmMapper, TdxPalm> implements ITdxConfigService {

    @Autowired
    private TdxConfigMapper tdxConfigMapper;
    @Override
    public TdxConfig selectTdxConfigById(Integer id) {
        TdxConfig tdxConfig = tdxConfigMapper.selectById(id);
        return tdxConfig;
    }

    @Override
    public TdxConfig selectTdxConfigByConfig(String config) {
        QueryWrapper<TdxConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("config", config);  // 指定查询条件
        return tdxConfigMapper.selectOne(queryWrapper);  // 返回单条结果
    }
}

package org.example.javacamerserver.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.javacamerserver.web.domain.TdxPalm;

@Mapper
public interface TdxPalmMapper extends BaseMapper<TdxPalm> {
    // 无需编写 XML，基础 CRUD 已自动实现
}

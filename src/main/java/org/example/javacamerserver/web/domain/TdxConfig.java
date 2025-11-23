package org.example.javacamerserver.web.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
@Data
@TableName("tdx_config") // 对应数据库表名
public class TdxConfig {
    //配置ID
    @TableId(type = IdType.ASSIGN_ID)
    private Integer id;
    //配置参数
    private String config;
    //配置参数值
    private Float value;
}

package org.example.javacamerserver.web.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @Author fangzhaoyang
 * @Description
 * @Date $ $
 * @Param $
 * @$
 **/
@Data
@TableName("tdx_palm") // 对应数据库表名
public class TdxPalm {
    //主键
    @TableId(type = IdType.ASSIGN_ID)
    private Integer id;
    //掌纹ID
    private String palmId;
    //掌纹特征值
    private String feature;
    //掌纹特征值
    private String palmImg;
    //掌纹特征值类型
    private Integer palmType;
    //手掌类型
    private Integer palmNum;

}
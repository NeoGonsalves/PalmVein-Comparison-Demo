package org.example.javacamerserver.xrCamer;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

//定义手掌信息结构体
public class sPalmInfo extends Structure {
    public byte status;       // uint8_t
    public byte palm_bright;  // uint8_t
    public short x;           // uint16_t
    public int y;             // uint32_t
    public short width;       // uint16_t
    public short height;      // uint16_t

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("status", "palm_bright", "x", "y", "width", "height");
    }

    public byte getStatus() {
        return status;
    }

    public void setStatus(byte status) {
        this.status = status;
    }

    public byte getPalm_bright() {
        return palm_bright;
    }

    public void setPalm_bright(byte palm_bright) {
        this.palm_bright = palm_bright;
    }

    public short getX() {
        return x;
    }

    public void setX(short x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public short getWidth() {
        return width;
    }

    public void setWidth(short width) {
        this.width = width;
    }

    public short getHeight() {
        return height;
    }

    public void setHeight(short height) {
        this.height = height;
    }
}

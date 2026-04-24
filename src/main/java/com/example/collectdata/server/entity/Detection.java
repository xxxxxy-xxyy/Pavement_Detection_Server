package com.example.collectdata.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "detections")
public class Detection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // GPS坐标
    private Double latitude;   // 纬度
    private Double longitude;  // 经度

    // 病害信息
    private String defectType;   // 病害类型，如 "crack", "pothole"
    private Float confidence;    // 置信度 0~1

    // 检测框坐标（归一化 0~1）
    private Float bboxX1;
    private Float bboxY1;
    private Float bboxX2;
    private Float bboxY2;

    // 图片文件名（存在服务器本地）
    private String imageName;

    // 触发通道：A=IMU触发, B=实时检测
    private String channel;

    // 上传时间
    private LocalDateTime uploadTime;

    // 设备ID（手机标识）
    private String deviceId;

    @PrePersist
    public void prePersist() {
        this.uploadTime = LocalDateTime.now();
    }
}
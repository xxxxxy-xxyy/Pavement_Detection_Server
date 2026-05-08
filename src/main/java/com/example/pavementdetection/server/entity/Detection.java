package com.example.pavementdetection.server.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadTime;

    // 设备ID（手机标识）
    private String deviceId;

    // 置信度状态：normal=正常, low=低置信度
    private String confidenceStatus;

    // 处理状态：pending=待处理, processing=处理中, resolved=已解决, ignored=已忽略
    private String handleStatus;

    // 处理人（登录用户名）
    private String handleBy;

    // 处理时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    @PrePersist
    public void prePersist() {
        this.uploadTime = LocalDateTime.now();
        this.handleStatus = "pending"; // 新记录默认待处理
    }
}
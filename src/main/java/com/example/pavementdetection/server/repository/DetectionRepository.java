package com.example.pavementdetection.server.repository;

import com.example.pavementdetection.server.entity.Detection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetectionRepository extends JpaRepository<Detection, Long> {
    // 按设备ID查询
    List<Detection> findByDeviceId(String deviceId);
    // 按病害类型查询
    List<Detection> findByDefectType(String defectType);

    // 按病害类型分组统计数量
    @Query("SELECT d.defectType, COUNT(d) FROM Detection d GROUP BY d.defectType")
    List<Object[]> countByDefectType();

    // 按日期分组统计数量（最近30天趋势）
    @Query(value = "SELECT DATE(upload_time) as day, COUNT(*) as cnt " +
            "FROM detections " +
            "WHERE upload_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY DATE(upload_time) " +
            "ORDER BY day ASC",
            nativeQuery = true)
    List<Object[]> countByDay();

    // 按处理状态查询
    List<Detection> findByHandleStatus(String handleStatus);

    // 统计各处理状态数量（用于统计面板）
    @Query("SELECT d.handleStatus, COUNT(d) FROM Detection d GROUP BY d.handleStatus")
    List<Object[]> countByHandleStatus();

    // 按严重程度等级分组统计
    @Query("SELECT d.severityLevel, COUNT(d) FROM Detection d GROUP BY d.severityLevel")
    List<Object[]> countBySeverityLevel();
}
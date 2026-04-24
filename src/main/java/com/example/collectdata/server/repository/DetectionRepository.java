package com.example.collectdata.server.repository;

import com.example.collectdata.server.entity.Detection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DetectionRepository extends JpaRepository<Detection, Long> {
    // 按设备ID查询
    List<Detection> findByDeviceId(String deviceId);
    // 按病害类型查询
    List<Detection> findByDefectType(String defectType);
}
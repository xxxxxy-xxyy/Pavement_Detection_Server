package com.example.pavementdetection.server.service;

import com.example.pavementdetection.server.entity.Detection;
import com.example.pavementdetection.server.repository.DetectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DetectionService {

    @Autowired
    private DetectionRepository detectionRepository;

    @Value("${upload.path}")
    private String uploadPath;

    // 保存一条检测记录（含图片）
    public Detection saveDetection(
            Double latitude, Double longitude,
            String defectType, Float confidence,
            Float bboxX1, Float bboxY1, Float bboxX2, Float bboxY2,
            String channel, String deviceId,
            MultipartFile imageFile) throws IOException {

        // 保存图片到本地
        String imageName = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            // 创建目录
            File dir = new File(uploadPath);
            if (!dir.exists()) dir.mkdirs();

            // 用UUID避免文件名冲突
            String ext = getExtension(imageFile.getOriginalFilename());
            imageName = UUID.randomUUID().toString() + "." + ext;
            imageFile.transferTo(new File(uploadPath + imageName));
        }

        // 构建数据库记录
        Detection detection = new Detection();
        detection.setLatitude(latitude);
        detection.setLongitude(longitude);
        detection.setDefectType(defectType);
        detection.setConfidence(confidence);
        detection.setBboxX1(bboxX1);
        detection.setBboxY1(bboxY1);
        detection.setBboxX2(bboxX2);
        detection.setBboxY2(bboxY2);
        detection.setChannel(channel);
        detection.setDeviceId(deviceId);
        detection.setImageName(imageName);

        return detectionRepository.save(detection);
    }

    // 查询所有记录
    public List<Detection> getAllDetections() {
        return detectionRepository.findAll();
    }

    // 按设备查询
    public List<Detection> getByDevice(String deviceId) {
        return detectionRepository.findByDeviceId(deviceId);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    // ===== 新增：分页查询 =====
    public Page<Detection> getDetectionPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return detectionRepository.findAll(pageable);
    }

    // ===== 新增：统计数据 =====
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 病害类型分布
        List<Object[]> typeData = detectionRepository.countByDefectType();
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (Object[] row : typeData) {
            typeCount.put((String) row[0], (Long) row[1]);
        }
        stats.put("typeDistribution", typeCount);

        // 每日趋势（最近30天）
        List<Object[]> dayData = detectionRepository.countByDay();
        Map<String, Long> dailyTrend = new LinkedHashMap<>();
        for (Object[] row : dayData) {
            dailyTrend.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        stats.put("dailyTrend", dailyTrend);

        // 总数
        stats.put("total", detectionRepository.count());

        return stats;
    }
}
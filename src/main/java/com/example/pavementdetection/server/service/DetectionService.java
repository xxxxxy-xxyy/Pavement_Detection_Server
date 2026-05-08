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
import java.time.LocalDateTime;
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


    // 各类型置信度阈值注入
    @Value("${confidence.threshold.crack:0.50}")
    private float thresholdCrack;

    @Value("${confidence.threshold.patched_crack:0.50}")
    private float thresholdPatchedCrack;

    @Value("${confidence.threshold.pothole:0.50}")
    private float thresholdPothole;

    @Value("${confidence.threshold.patched_pothole:0.50}")
    private float thresholdPatchedPothole;

    @Value("${confidence.threshold.alligator_crack:0.50}")
    private float thresholdAlligatorCrack;

    @Value("${confidence.threshold.patched_alligator_crack:0.50}")
    private float thresholdPatchedAlligatorCrack;

    @Value("${confidence.threshold.manhole:0.45}")
    private float thresholdManhole;

    @Value("${confidence.threshold.street_waste:0.35}")
    private float thresholdStreetWaste;

    @Value("${confidence.threshold.default:0.50}")
    private float thresholdDefault;

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

        // 置信度状态判断
        float threshold = getThreshold(defectType);
        String status = (confidence != null && confidence >= threshold) ? "normal" : "low";
        detection.setConfidenceStatus(status);

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

    /**
     * 根据病害类型获取对应置信度阈值
     */
    private float getThreshold(String defectType) {
        if (defectType == null) return thresholdDefault;
        return switch (defectType) {
            case "crack"                    -> thresholdCrack;
            case "patched_crack"            -> thresholdPatchedCrack;
            case "pothole"                  -> thresholdPothole;
            case "patched_pothole"          -> thresholdPatchedPothole;
            case "alligator_crack"          -> thresholdAlligatorCrack;
            case "patched_alligator_crack"  -> thresholdPatchedAlligatorCrack;
            case "manhole"                  -> thresholdManhole;
            case "street_waste"             -> thresholdStreetWaste;
            default                         -> thresholdDefault;
        };
    }

    // 分页查询
    public Page<Detection> getDetectionPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return detectionRepository.findAll(pageable);
    }

    // 统计数据
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

    /**
     * 运行时刷新阈值（由 ThresholdController 调用）
     * 从 Environment 重新读取最新值写回字段
     */
    @Autowired
    private org.springframework.core.env.Environment environment;


    /**
     * 更新处理状态
     * @param id           记录ID
     * @param handleStatus 新状态：pending / processing / resolved / ignored
     * @param operator     操作人（当前登录用户名）
     * @return 更新后的记录，不存在返回 null
     */
    public Detection updateHandleStatus(Long id, String handleStatus, String operator) {
        return detectionRepository.findById(id).map(d -> {
            d.setHandleStatus(handleStatus);
            d.setHandleBy(operator);
            d.setHandleTime(LocalDateTime.now());
            return detectionRepository.save(d);
        }).orElse(null);
    }

    public void refreshThresholds() {
        thresholdCrack                  = parseThreshold("confidence.threshold.crack",                   0.50f);
        thresholdPatchedCrack           = parseThreshold("confidence.threshold.patched_crack",            0.50f);
        thresholdPothole                = parseThreshold("confidence.threshold.pothole",                  0.50f);
        thresholdPatchedPothole         = parseThreshold("confidence.threshold.patched_pothole",          0.50f);
        thresholdAlligatorCrack         = parseThreshold("confidence.threshold.alligator_crack",          0.50f);
        thresholdPatchedAlligatorCrack  = parseThreshold("confidence.threshold.patched_alligator_crack",  0.50f);
        thresholdManhole                = parseThreshold("confidence.threshold.manhole",                  0.45f);
        thresholdStreetWaste            = parseThreshold("confidence.threshold.street_waste",             0.35f);
        thresholdDefault                = parseThreshold("confidence.threshold.default",                  0.50f);
    }

    private float parseThreshold(String key, float fallback) {
        String val = environment.getProperty(key);
        if (val == null) return fallback;
        try { return Float.parseFloat(val); } catch (NumberFormatException e) { return fallback; }
    }
}
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

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Value("${upload.path}")
    private String uploadPath;

    // ───────────── 置信度阈值 ─────────────
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

    // ═══════════════════════════════════════════════
    //  保存检测记录（含图片 + 置信度过滤 + 严重程度评级）
    // ═══════════════════════════════════════════════
    public Detection saveDetection(
            Double latitude, Double longitude,
            String defectType, Float confidence,
            Float bboxX1, Float bboxY1, Float bboxX2, Float bboxY2,
            String channel, String deviceId,
            MultipartFile imageFile) throws IOException {

        // 1. 保存图片
        String imageName = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            File dir = new File(uploadPath);
            if (!dir.exists()) dir.mkdirs();
            String ext = getExtension(imageFile.getOriginalFilename());
            imageName = UUID.randomUUID().toString() + "." + ext;
            imageFile.transferTo(new File(uploadPath + imageName));
        }

        // 2. 构建实体
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

        // 3. 置信度状态
        float threshold = getThreshold(defectType);
        detection.setConfidenceStatus(
                (confidence != null && confidence >= threshold) ? "normal" : "low"
        );

        // 4. 严重程度评级 ← 新增
        float score = calcSeverityScore(defectType, confidence, bboxX1, bboxY1, bboxX2, bboxY2);
        detection.setSeverityScore(score);
        detection.setSeverityLevel(scoreToLevel(score));

        return detectionRepository.save(detection);
    }

    // ═══════════════════════════════════════════════
    //  严重程度计算
    // ═══════════════════════════════════════════════

    /**
     * 计算严重程度评分
     * score = 类型基础分 × confidence × bbox面积系数
     */
    private float calcSeverityScore(String defectType, Float confidence,
                                    Float x1, Float y1, Float x2, Float y2) {
        if (confidence == null) confidence = 0f;

        // 类型基础分
        float typeBase = getTypeBaseScore(defectType);

        // bbox 面积系数（归一化坐标，面积范围 0~1）
        float areaFactor = 1.0f;
        if (x1 != null && y1 != null && x2 != null && y2 != null) {
            float area = Math.abs(x2 - x1) * Math.abs(y2 - y1);
            if      (area > 0.10f) areaFactor = 1.5f;
            else if (area > 0.04f) areaFactor = 1.2f;
            // 否则保持 1.0
        }

        return typeBase * confidence * areaFactor;
    }

    /**
     * 各病害类型基础分
     *   10 — 直接影响行车安全（坑槽、网裂）
     *    7 — 中高风险（裂缝、修补后坑槽）
     *    6 — 基础设施隐患（检查井）
     *    4 — 低风险修补类
     *    3 — 环境类
     */
    private float getTypeBaseScore(String defectType) {
        if (defectType == null) return 5f;
        return switch (defectType) {
            case "pothole"                  -> 10f;
            case "alligator_crack"          -> 10f;
            case "crack"                    -> 7f;
            case "patched_pothole"          -> 7f;
            case "manhole"                  -> 6f;
            case "patched_crack"            -> 4f;
            case "patched_alligator_crack"  -> 4f;
            case "street_waste"             -> 3f;
            default                         -> 5f;
        };
    }

    /**
     * 评分 → 等级
     *   >= 7.0  critical（紧急）
     *   >= 4.5  high    （高危）
     *   >= 2.5  medium  （中危）
     *   其他    low     （低危）
     */
    private String scoreToLevel(float score) {
        if      (score >= 7.0f) return "critical";
        else if (score >= 4.5f) return "high";
        else if (score >= 2.5f) return "medium";
        else                    return "low";
    }

    // ═══════════════════════════════════════════════
    //  查询 / 统计
    // ═══════════════════════════════════════════════

    public List<Detection> getAllDetections() {
        return detectionRepository.findAll();
    }

    public List<Detection> getByDevice(String deviceId) {
        return detectionRepository.findByDeviceId(deviceId);
    }

    /** 分页查询 — 按严重程度降序，同级别按 id DESC */
    public Page<Detection> getDetectionPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(
                        Sort.Order.desc("severityScore"),  // 紧急在前
                        Sort.Order.desc("id")              // 同分按最新
                ));
        return detectionRepository.findAll(pageable);
    }

    /** 统计数据（类型分布 / 30天趋势 / 总数 / 严重程度分布） */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 病害类型分布
        List<Object[]> typeData = detectionRepository.countByDefectType();
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (Object[] row : typeData) {
            typeCount.put((String) row[0], (Long) row[1]);
        }
        stats.put("typeDistribution", typeCount);

        // 30天趋势
        List<Object[]> dayData = detectionRepository.countByDay();
        Map<String, Long> dailyTrend = new LinkedHashMap<>();
        for (Object[] row : dayData) {
            dailyTrend.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        stats.put("dailyTrend", dailyTrend);

        // 总数
        stats.put("total", detectionRepository.count());

        // 严重程度分布 ← 新增
        List<Object[]> severityData = detectionRepository.countBySeverityLevel();
        Map<String, Long> severityCount = new LinkedHashMap<>();
        // 保证顺序：critical > high > medium > low
        for (String level : new String[]{"critical", "high", "medium", "low"}) {
            severityCount.put(level, 0L);
        }
        for (Object[] row : severityData) {
            if (row[0] != null) severityCount.put((String) row[0], (Long) row[1]);
        }
        stats.put("severityDistribution", severityCount);

        return stats;
    }

    /** 更新处理状态 */
    public Detection updateHandleStatus(Long id, String handleStatus, String operator) {
        return detectionRepository.findById(id).map(d -> {
            d.setHandleStatus(handleStatus);
            d.setHandleBy(operator);
            d.setHandleTime(LocalDateTime.now());
            return detectionRepository.save(d);
        }).orElse(null);
    }


    /**
     * 上传处理后图片，同步将状态设为 resolved
     */
    public Detection resolveWithAfterImage(Long id, MultipartFile afterImage, String operator) throws IOException {
        return detectionRepository.findById(id).map(d -> {
            // 保存图片
            if (afterImage != null && !afterImage.isEmpty()) {
                try {
                    File dir = new File(uploadPath);
                    if (!dir.exists()) dir.mkdirs();
                    String ext = getExtension(afterImage.getOriginalFilename());
                    String fileName = "after_" + UUID.randomUUID() + "." + ext;
                    afterImage.transferTo(new File(uploadPath + fileName));
                    d.setAfterImageName(fileName);
                } catch (IOException e) {
                    throw new RuntimeException("图片保存失败: " + e.getMessage());
                }
            }
            d.setHandleStatus("resolved");
            d.setHandleBy(operator);
            d.setHandleTime(LocalDateTime.now());
            return detectionRepository.save(d);
        }).orElse(null);
    }

    /**
     * 撤回已解决状态时，清除处理后图片
     */
    public Detection clearAfterImage(Long id, String handleStatus, String operator) {
        return detectionRepository.findById(id).map(d -> {
            // 删除磁盘上的文件
            if (d.getAfterImageName() != null) {
                File f = new File(uploadPath + d.getAfterImageName());
                if (f.exists()) f.delete();
                d.setAfterImageName(null);
            }
            d.setHandleStatus(handleStatus);
            d.setHandleBy(operator);
            d.setHandleTime(LocalDateTime.now());
            return detectionRepository.save(d);
        }).orElse(null);
    }


    // ═══════════════════════════════════════════════
    //  阈值相关
    // ═══════════════════════════════════════════════

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

    public void refreshThresholds() {
        thresholdCrack                 = parseThreshold("confidence.threshold.crack",                  0.50f);
        thresholdPatchedCrack          = parseThreshold("confidence.threshold.patched_crack",           0.50f);
        thresholdPothole               = parseThreshold("confidence.threshold.pothole",                 0.50f);
        thresholdPatchedPothole        = parseThreshold("confidence.threshold.patched_pothole",         0.50f);
        thresholdAlligatorCrack        = parseThreshold("confidence.threshold.alligator_crack",         0.50f);
        thresholdPatchedAlligatorCrack = parseThreshold("confidence.threshold.patched_alligator_crack", 0.50f);
        thresholdManhole               = parseThreshold("confidence.threshold.manhole",                 0.45f);
        thresholdStreetWaste           = parseThreshold("confidence.threshold.street_waste",            0.35f);
        thresholdDefault               = parseThreshold("confidence.threshold.default",                 0.50f);
    }

    private float parseThreshold(String key, float fallback) {
        String val = environment.getProperty(key);
        if (val == null) return fallback;
        try { return Float.parseFloat(val); } catch (NumberFormatException e) { return fallback; }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
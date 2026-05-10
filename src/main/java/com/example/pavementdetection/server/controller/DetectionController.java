package com.example.pavementdetection.server.controller;

import com.example.pavementdetection.server.entity.Detection;
import com.example.pavementdetection.server.repository.DetectionRepository;
import com.example.pavementdetection.server.service.DetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/detection")
public class DetectionController {

    @Autowired
    private DetectionService detectionService;

    @Autowired
    private DetectionRepository detectionRepository;

    /** APP上传检测结果 POST /api/detection/upload */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("latitude")   Double latitude,
            @RequestParam("longitude")  Double longitude,
            @RequestParam("defectType") String defectType,
            @RequestParam("confidence") Float  confidence,
            @RequestParam("bboxX1") Float bboxX1, @RequestParam("bboxY1") Float bboxY1,
            @RequestParam("bboxX2") Float bboxX2, @RequestParam("bboxY2") Float bboxY2,
            @RequestParam("channel")  String channel,
            @RequestParam("deviceId") String deviceId,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        Map<String, Object> result = new HashMap<>();
        try {
            Detection saved = detectionService.saveDetection(
                    latitude, longitude, defectType, confidence,
                    bboxX1, bboxY1, bboxX2, bboxY2, channel, deviceId, image);
            result.put("success",         true);
            result.put("id",              saved.getId());
            result.put("message",         "上传成功");
            result.put("confidenceStatus",saved.getConfidenceStatus());
            result.put("severityLevel",   saved.getSeverityLevel());
            result.put("severityScore",   saved.getSeverityScore());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /** 分页列表 GET /api/detection/list */
    @GetMapping("/list")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Detection> result = detectionService.getDetectionPage(page, size);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content",       result.getContent());
        resp.put("totalPages",    result.getTotalPages());
        resp.put("totalElements", result.getTotalElements());
        resp.put("currentPage",   result.getNumber());
        return ResponseEntity.ok(resp);
    }

    /** 统计 GET /api/detection/stats */
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(detectionService.getStats());
    }

    /** 删除 DELETE /api/detection/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!detectionRepository.existsById(id)) return ResponseEntity.notFound().build();
        detectionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "id", id));
    }

    /**
     * 更新处理状态 PATCH /api/detection/{id}/status
     * 若目标状态是 resolved，前端应改用 /resolve-with-image 接口
     * 此接口保留用于 pending / processing / ignored 三种状态
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpSession session) {

        String handleStatus = body.get("handleStatus");
        if (!List.of("pending", "processing", "ignored").contains(handleStatus)) {
            if ("resolved".equals(handleStatus)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false, "message", "标记已解决请使用上传图片接口", "needImage", true));
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "无效的状态值"));
        }
        String operator = (String) session.getAttribute("loginUser");

        // 切换回非resolved状态时，清除处理后图片
        Detection current = detectionRepository.findById(id).orElse(null);
        if (current == null) return ResponseEntity.notFound().build();

        Detection updated;
        if ("resolved".equals(current.getHandleStatus()) && current.getAfterImageName() != null) {
            updated = detectionService.clearAfterImage(id, handleStatus, operator);
        } else {
            updated = detectionService.updateHandleStatus(id, handleStatus, operator);
        }
        if (updated == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of(
                "success",        true,
                "handleStatus",   updated.getHandleStatus(),
                "handleBy",       updated.getHandleBy(),
                "handleTime",     updated.getHandleTime().toString(),
                "afterImageName", updated.getAfterImageName() == null ? "" : updated.getAfterImageName()
        ));
    }

    /**
     * 上传处理后图片 + 标记已解决
     * POST /api/detection/{id}/resolve-with-image
     * multipart: afterImage (file, 必填)
     */
    @PostMapping("/{id}/resolve-with-image")
    public ResponseEntity<?> resolveWithImage(
            @PathVariable Long id,
            @RequestParam("afterImage") MultipartFile afterImage,
            jakarta.servlet.http.HttpSession session) {

        if (afterImage == null || afterImage.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "请上传处理后图片"));
        }
        String operator = (String) session.getAttribute("loginUser");
        try {
            Detection updated = detectionService.resolveWithAfterImage(id, afterImage, operator);
            if (updated == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of(
                    "success",        true,
                    "handleStatus",   updated.getHandleStatus(),
                    "handleBy",       updated.getHandleBy(),
                    "handleTime",     updated.getHandleTime().toString(),
                    "afterImageName", updated.getAfterImageName()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** 导出 CSV GET /api/detection/export */
    @GetMapping("/export")
    public ResponseEntity<?> export(
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) String channel) {
        List<Detection> all = detectionRepository.findAll();
        List<Detection> filtered = all.stream()
                .filter(d -> (defectType == null || defectType.isEmpty() || defectType.equals(d.getDefectType())))
                .filter(d -> (channel    == null || channel.isEmpty()    || channel.equals(d.getChannel())))
                .toList();
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/heatmap")
    @ResponseBody
    public List<Map<String, Object>> getHeatmapData() {
        return detectionService.getHeatmapPoints();
    }
}
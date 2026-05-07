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

    /**
     * APP上传检测结果接口
     * POST /api/detection/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("latitude")  Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam("defectType") String defectType,
            @RequestParam("confidence") Float confidence,
            @RequestParam("bboxX1") Float bboxX1,
            @RequestParam("bboxY1") Float bboxY1,
            @RequestParam("bboxX2") Float bboxX2,
            @RequestParam("bboxY2") Float bboxY2,
            @RequestParam("channel")  String channel,
            @RequestParam("deviceId") String deviceId,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        Map<String, Object> result = new HashMap<>();
        try {
            Detection saved = detectionService.saveDetection(
                    latitude, longitude, defectType, confidence,
                    bboxX1, bboxY1, bboxX2, bboxY2,
                    channel, deviceId, image);

            result.put("success", true);
            result.put("id", saved.getId());
            result.put("message", "上传成功");
            // 返回置信度状态，APP 可选择性提示用户
            result.put("confidenceStatus", saved.getConfidenceStatus());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    // 分页列表接口（替代原来的 /list）
// GET /api/detection/list?page=0&size=20
    @GetMapping("/list")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Detection> result = detectionService.getDetectionPage(page, size);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("totalPages", result.getTotalPages());
        resp.put("totalElements", result.getTotalElements());
        resp.put("currentPage", result.getNumber());
        return ResponseEntity.ok(resp);
    }

    // 统计接口
// GET /api/detection/stats
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(detectionService.getStats());
    }


    // 删除单条记录
// DELETE /api/detection/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!detectionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        detectionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "id", id));
    }

    // 导出全量数据（支持筛选）
// GET /api/detection/export?defectType=crack&channel=A
    @GetMapping("/export")
    public ResponseEntity<?> export(
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) String channel) {
        List<Detection> all = detectionRepository.findAll();
        // 前端筛选条件在后端过滤
        List<Detection> filtered = all.stream()
                .filter(d -> (defectType == null || defectType.isEmpty() || defectType.equals(d.getDefectType())))
                .filter(d -> (channel    == null || channel.isEmpty()    || channel.equals(d.getChannel())))
                .toList();
        return ResponseEntity.ok(filtered);
    }
}


package com.example.collectdata.server.controller;

import com.example.collectdata.server.entity.Detection;
import com.example.collectdata.server.service.DetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/detection")
public class DetectionController {

    @Autowired
    private DetectionService detectionService;

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
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 查询所有检测记录（Web页面用）
     * GET /api/detection/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<Detection>> list() {
        return ResponseEntity.ok(detectionService.getAllDetections());
    }
}
package com.example.pavementdetection.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/threshold")
public class ThresholdController {

    // 运行时动态属性源的名称
    private static final String SOURCE_NAME = "dynamicThresholds";

    // 8种病害类型 key
    private static final String[] TYPES = {
            "crack", "patched_crack", "pothole", "patched_pothole",
            "alligator_crack", "patched_alligator_crack", "manhole", "street_waste"
    };

    @Autowired
    private ConfigurableEnvironment env;

    @Autowired
    private com.example.pavementdetection.server.service.DetectionService detectionService;

    /**
     * 获取当前各类型阈值
     * GET /api/threshold/list
     */
    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String type : TYPES) {
            String key = "confidence.threshold." + type;
            String val = env.getProperty(key, "0.50");
            result.put(type, Float.parseFloat(val));
        }
        result.put("default", Float.parseFloat(env.getProperty("confidence.threshold.default", "0.50")));
        return result;
    }

    /**
     * 更新阈值（运行时生效，重启后恢复 properties 原值）
     * POST /api/threshold/update
     * Body: { "crack": 0.5, "street_waste": 0.35, ... }
     */
    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody Map<String, Float> newValues) {
        MutablePropertySources sources = env.getPropertySources();

        // 取出或新建动态属性源
        MapPropertySource dynamicSource;
        if (sources.contains(SOURCE_NAME)) {
            dynamicSource = (MapPropertySource) sources.get(SOURCE_NAME);
        } else {
            dynamicSource = new MapPropertySource(SOURCE_NAME, new java.util.HashMap<>());
            sources.addFirst(dynamicSource); // 最高优先级，覆盖 properties 文件
        }

        // 写入新阈值
        for (Map.Entry<String, Float> entry : newValues.entrySet()) {
            float val = entry.getValue();
            if (val < 0 || val > 1) continue; // 合法性校验
            String propKey = "confidence.threshold." + entry.getKey();
            dynamicSource.getSource().put(propKey, String.valueOf(val));
        }

        // 通知 DetectionService 刷新阈值缓存
        detectionService.refreshThresholds();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "阈值已更新，立即生效");
        resp.put("current", list()); // 返回更新后的完整阈值
        return resp;
    }
}
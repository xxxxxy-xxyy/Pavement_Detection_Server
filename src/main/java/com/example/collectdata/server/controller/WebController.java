package com.example.collectdata.server.controller;

import com.example.collectdata.server.entity.Detection;
import com.example.collectdata.server.service.DetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Controller
public class WebController {

    @Autowired
    private DetectionService detectionService;

    @Value("${upload.path}")
    private String uploadPath;

    // 主页：地图+列表
    @GetMapping("/")
    public String index(Model model) {
        List<Detection> detections = detectionService.getAllDetections();
        model.addAttribute("detections", detections);
        model.addAttribute("total", detections.size());
        return "index";
    }

    // 提供图片访问
    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadPath).resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (MalformedURLException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
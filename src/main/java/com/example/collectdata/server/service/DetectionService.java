package com.example.collectdata.server.service;

import com.example.collectdata.server.entity.Detection;
import com.example.collectdata.server.repository.DetectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
}
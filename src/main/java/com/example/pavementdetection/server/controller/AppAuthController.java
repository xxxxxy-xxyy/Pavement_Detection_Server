package com.example.pavementdetection.server.controller;

import com.example.pavementdetection.server.entity.User;
import com.example.pavementdetection.server.repository.UserRepository;
import com.example.pavementdetection.server.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AppAuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** APP 注册 POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.length() < 3)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户名至少3位"));
        if (password == null || password.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "密码至少6位"));
        if (userRepository.findByUsername(username) != null)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户名已存在"));

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generate(username);
        return ResponseEntity.ok(Map.of(
                "success",  true,
                "message",  "注册成功",
                "username", username,
                "token",    token
        ));
    }

    /** APP 登录 POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        User user = userRepository.findByUsername(username).orElse(null);;
        if (userRepository.findByUsername(username).orElse(null) != null)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户名已存在"));

        String token = jwtUtil.generate(username);
        return ResponseEntity.ok(Map.of(
                "success",  true,
                "username", username,
                "token",    token
        ));
    }
}
package com.example.pavementdetection.server.controller;

import com.example.pavementdetection.server.entity.User;
import com.example.pavementdetection.server.repository.UserRepository;
import com.example.pavementdetection.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
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
        if (userRepository.findByUsername(username).isPresent())
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

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !encoder.matches(password, user.getPassword()))
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "用户名或密码错误"));

        String token = jwtUtil.generate(username);
        return ResponseEntity.ok(Map.of(
                "success",  true,
                "username", username,
                "token",    token
        ));
    }

    /** 修改密码 POST /api/auth/changePassword */
    @PostMapping("/changePassword")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        // 从 Token 里取当前用户名（AppTokenInterceptor 已验证并存入 attribute）
        String username = (String) request.getAttribute("appUser");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (newPassword == null || newPassword.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("message", "新密码至少6位"));

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
        if (!encoder.matches(oldPassword, user.getPassword()))
            return ResponseEntity.status(401).body(Map.of("message", "旧密码错误"));

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "success"));
    }

    /** 注销账号 DELETE /api/auth/account */
    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(HttpServletRequest request) {

        String username = (String) request.getAttribute("appUser");
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "success"));
    }
}
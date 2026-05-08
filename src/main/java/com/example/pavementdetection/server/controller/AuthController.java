package com.example.pavementdetection.server.controller;

import com.example.pavementdetection.server.entity.User;
import com.example.pavementdetection.server.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    /** 登录表单提交 */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session,
                          RedirectAttributes ra) {
        User user = userService.login(username, password);
        if (user == null) {
            ra.addFlashAttribute("error", "用户名或密码错误");
            return "redirect:/login";
        }
        session.setAttribute("loginUser", user.getUsername());
        session.setMaxInactiveInterval(60 * 60 * 8); // 8小时免登录
        return "redirect:/";
    }

    /** 注册表单提交 */
    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String confirmPassword,
                             RedirectAttributes ra) {
        if (username.isBlank() || username.length() < 3) {
            ra.addFlashAttribute("regError", "用户名至少3位");
            ra.addFlashAttribute("tab", "register");
            return "redirect:/login";
        }
        if (!password.equals(confirmPassword)) {
            ra.addFlashAttribute("regError", "两次密码不一致");
            ra.addFlashAttribute("tab", "register");
            return "redirect:/login";
        }
        if (password.length() < 6) {
            ra.addFlashAttribute("regError", "密码至少6位");
            ra.addFlashAttribute("tab", "register");
            return "redirect:/login";
        }
        boolean ok = userService.register(username, password);
        if (!ok) {
            ra.addFlashAttribute("regError", "用户名已被注册");
            ra.addFlashAttribute("tab", "register");
            return "redirect:/login";
        }
        ra.addFlashAttribute("regSuccess", "注册成功，请登录");
        return "redirect:/login";
    }

    /** 登出 */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
package br.com.agendafacilpro.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String home() {
        return "redirect:/login";
    }
}

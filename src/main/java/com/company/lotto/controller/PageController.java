package com.company.lotto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/events")
    public String events() {
        return "events";
    }

    @GetMapping("/participate")
    public String participate() {
        return "participate";
    }

    @GetMapping("/result")
    public String result() {
        return "result";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}

package com.cricpulse.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * When the React build is bundled into {@code src/main/resources/static}, client-side routes
 * (e.g. /match/3) must return index.html so the router can take over on a page refresh.
 */
@Controller
public class SpaController {

    @GetMapping({"/match/{id}", "/stats", "/players/{id}", "/control"})
    public String forward() {
        return "forward:/index.html";
    }
}

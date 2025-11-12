package net.iotku.subdonic.panel;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PanelController {
    @GetMapping("/")
    public String index() {
        return "index";
    }
}

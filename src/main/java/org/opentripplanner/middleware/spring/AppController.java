package org.opentripplanner.middleware.spring;

import org.opentripplanner.middleware.BasicOtpDispatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {
    // available at http://localhost:8080/hello
    @RequestMapping("/hello")
    public String hello() {
        return "(Spring) OTP Middleware says Hi!";
    }

    // available at http://localhost:8080/sync
    @RequestMapping("/sync")
    public String sync() {return BasicOtpDispatcher.executeRequestsInSequence();}

    // available at http://localhost:8080/async
    @RequestMapping("/async")
    public String async() {return BasicOtpDispatcher.executeRequestsAsync();}
}

package com.hopeful117.devlogai.shared.controller;

import com.hopeful117.devlogai.shared.exception.handler.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public abstract class ControllerWebMvcTestSupport {

    protected MockMvc mockMvc(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}

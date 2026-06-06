package com.systeam.dashboard.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ModulosController.class)
class ModulosControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser
    void getModulosStatus_retorna200() throws Exception {
        mockMvc.perform(get("/api/modules/status"))
            .andExpect(status().isOk());
    }
}

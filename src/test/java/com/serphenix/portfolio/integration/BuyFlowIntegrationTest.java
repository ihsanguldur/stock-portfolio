package com.serphenix.portfolio.integration;

import tools.jackson.databind.json.JsonMapper;
import com.serphenix.portfolio.config.TestcontainersConfig;
import com.serphenix.portfolio.dto.request.BuyRequestDto;
import com.serphenix.portfolio.dto.request.RegisterRequestDto;
import com.serphenix.portfolio.external.StockPriceClient;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Optional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class BuyFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private StockPriceClient stockPriceClient;

    @Test
    void registerThenBuyUpdatesWalletAndCreatesHolding() throws Exception {
        when(stockPriceClient.getPrice("AAPL")).thenReturn(Optional.of(new BigDecimal("150.00")));

        RegisterRequestDto registerRequest = new RegisterRequestDto("buy@example.com", "12345678");

        String registerResponse = mockMvc.perform(post("/auth/register")
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accessToken = jsonMapper.readTree(registerResponse).get("accessToken").asString();

        BuyRequestDto buyRequest = new BuyRequestDto("AAPL", 10L);

        mockMvc.perform(post("/transactions/buy")
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(buyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quantity").value(10L))
                .andExpect(jsonPath("$.price").value(new BigDecimal("150.0")));

        mockMvc.perform(get("/wallet")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(new BigDecimal("98500.0")));
    }

    @Test
    void buyWithInsufficientBalanceReturnsProblemDetail()  throws Exception {
        when(stockPriceClient.getPrice("AAPL")).thenReturn(Optional.of(new BigDecimal("150.00")));

        RegisterRequestDto registerRequest = new RegisterRequestDto("poorbuy@example.com", "12345678");

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accessToken = jsonMapper.readTree(registerResponse).get("accessToken").asText();

        BuyRequestDto buyRequest = new BuyRequestDto("AAPL", 10000L);

        mockMvc.perform(post("/transactions/buy")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(buyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Insufficient balance to buy 10000 AAPL"));
    }
}

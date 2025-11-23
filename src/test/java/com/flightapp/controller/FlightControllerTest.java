package com.flightapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FlightControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @Test
  public void contextLoads() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().is2xxSuccessful();
  }
}
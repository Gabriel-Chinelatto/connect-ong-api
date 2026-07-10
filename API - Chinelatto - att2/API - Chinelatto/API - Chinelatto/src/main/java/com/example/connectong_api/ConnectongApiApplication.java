package com.example.connectong_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling liga os jobs agendados (@Scheduled). Hoje: o aviso diario
// para a ONG de doadores esperando ha muito tempo (EsperaMatchScheduler).
@SpringBootApplication
@EnableScheduling
public class ConnectongApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConnectongApiApplication.class, args);
	}

}

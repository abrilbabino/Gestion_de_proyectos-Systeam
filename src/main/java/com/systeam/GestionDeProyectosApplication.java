package com.systeam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class GestionDeProyectosApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionDeProyectosApplication.class, args);
	}

}

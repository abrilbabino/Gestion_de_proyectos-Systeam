package com.systeam.GestionDeProyectos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

import com.systeam.shared.model.Proyecto;

@SpringBootApplication
@EntityScan(basePackages = "com.systeam.shared.model")
public class GestionDeProyectosApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionDeProyectosApplication.class, args);
	}

}

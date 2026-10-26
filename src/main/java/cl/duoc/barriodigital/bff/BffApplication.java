package cl.duoc.barriodigital.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del BFF.
 * Spring Boot arranca desde aquí la API que recibe el navegador.
 */
@SpringBootApplication
public class BffApplication {

    /** Arranca la aplicación. */
    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}

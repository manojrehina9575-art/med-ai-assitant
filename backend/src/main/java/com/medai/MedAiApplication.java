package com.medai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.imageio.ImageIO;

@SpringBootApplication
public class MedAiApplication {

    public static void main(String[] args) {
        // Register ImageIO SPI plugins (e.g. the dcm4che DICOM reader) bundled inside the
        // Spring Boot fat jar. Nested-jar META-INF/services providers are not always picked
        // up automatically, so trigger an explicit scan on the launched classloader.
        ImageIO.scanForPlugins();
        SpringApplication.run(MedAiApplication.class, args);
    }
}

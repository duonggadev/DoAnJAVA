package vn.edu.solop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SoLopApplication {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(SoLopApplication.class, args);
    }

    @Bean
    Clock schoolClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}

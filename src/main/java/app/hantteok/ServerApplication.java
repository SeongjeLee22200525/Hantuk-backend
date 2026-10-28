package app.hantteok;

import app.hantteok.infrastructure.h2.WritableFilePath;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        if (Boolean.getBoolean("hantteok.h2.writeProbe")) {
            org.h2.store.fs.FilePath.register(new WritableFilePath());
        }
        SpringApplication.run(ServerApplication.class, args);
    }
}

package app.hantteok;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        if (Boolean.getBoolean("hantteok.h2.writeProbe")) {
            org.h2.store.fs.FilePath.register(new WritableFilePath());
        }
        SpringApplication.run(ServerApplication.class, args);
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}

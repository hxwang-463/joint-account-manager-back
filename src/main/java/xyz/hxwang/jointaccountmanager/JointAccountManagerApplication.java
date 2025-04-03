package xyz.hxwang.jointaccountmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class JointAccountManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JointAccountManagerApplication.class, args);
    }

}

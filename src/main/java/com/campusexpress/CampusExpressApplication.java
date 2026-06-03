package com.campusexpress;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.campusexpress.mapper")
public class CampusExpressApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusExpressApplication.class, args);
    }

}

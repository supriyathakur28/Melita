package com.melita.orders.approval;

import com.melita.orders.platform.security.SecuritySupportConfiguration;
import com.melita.orders.platform.web.WebSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@Import({WebSupportConfiguration.class, SecuritySupportConfiguration.class})
public class OrderApprovalApplication {

    public static void main(final String[] args) {
        SpringApplication.run(OrderApprovalApplication.class, args);
    }
}

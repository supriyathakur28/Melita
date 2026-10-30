package com.melita.orders.fulfilment;

import com.melita.orders.platform.security.SecuritySupportConfiguration;
import com.melita.orders.platform.web.WebSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({WebSupportConfiguration.class, SecuritySupportConfiguration.class})
public class FulfilmentServiceApplication {

    public static void main(final String[] args) {
        SpringApplication.run(FulfilmentServiceApplication.class, args);
    }
}

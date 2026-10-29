package com.melita.orders.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Who receives the emails (a shared agents' mailbox) and who sends them, from Spring Cloud Config. */
@Validated
@ConfigurationProperties("notification")
public record NotificationProperties(@NotBlank @Email String agentsMailbox, @NotBlank @Email String from) {
}

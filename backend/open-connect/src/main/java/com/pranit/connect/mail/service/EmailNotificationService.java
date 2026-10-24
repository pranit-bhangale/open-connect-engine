package com.pranit.connect.mail.service;

import com.pranit.connect.mail.event.RoomCreatedEvent;
import com.pranit.connect.mail.properties.HostProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final HostProperties properties;

    @Async("emailExecutor")
    @EventListener
    public void onRoomCreated(final RoomCreatedEvent event) {
        final String configuredEmail = properties.email() != null && !properties.email().isBlank()
                ? properties.email().trim().toLowerCase() : "pranitbhangale14@gmail.com";
        final boolean emailMatches = event.creatorEmail() != null &&
                (event.creatorEmail().trim().equalsIgnoreCase("pranitbhangale14@gmail.com")
                        || event.creatorEmail().trim().equalsIgnoreCase(configuredEmail));
        final String cleanName = event.creatorName() != null ? event.creatorName().trim().toLowerCase() : "";
        final boolean nameMatches = cleanName.equals("pranit")
                || cleanName.startsWith("pranit ") || cleanName.equals("pranit (host)");
        if (emailMatches && nameMatches) {
            log.debug("Skipping room creation email alert: room created by host.");
            return;
        }
        final String baseUrl = properties.portfolioUrl() != null ? properties.portfolioUrl() : "";
        final String joinUrl = baseUrl.endsWith("/")
                ? baseUrl + "#connect?code=" + event.roomCode() : baseUrl + "/#connect?code=" + event.roomCode();
        SimpleMailMessage message = getMessage(event, joinUrl);
        try {
            mailSender.send(message);
            log.info("Room creation notification sent to host for room {}", event.roomCode());
        } catch (Exception ex) {
            log.error("Failed to send room creation email: {}", ex.getMessage());
        }
    }

    private @NonNull SimpleMailMessage getMessage(final RoomCreatedEvent event, final String joinUrl) {
        final String text = """
                Hello Pranit,
                
                A new live chat room has been created on your portfolio!
                
                👤 Created by: %s (%s)
                💬 Room: %s
                🔑 Room Code: %s
                
                👉 Tap here to join the live room:
                %s
                
                — Open-Connect Live Engine
                """.formatted(
                event.creatorName(),
                event.creatorEmail(),
                event.roomName(),
                event.roomCode(),
                joinUrl
        ).stripIndent().trim();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.email());
        message.setTo(properties.email());
        message.setSubject("🔔 New Live Chat Room Created: " + event.creatorName());
        message.setText(text);
        return message;
    }
}

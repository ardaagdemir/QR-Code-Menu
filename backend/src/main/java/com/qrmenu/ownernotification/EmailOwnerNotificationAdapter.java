package com.qrmenu.ownernotification;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Section 15: plain SMTP adapter via {@link JavaMailSender} - dev points at Mailhog, prod at a real SMTP provider. */
@Component
class EmailOwnerNotificationAdapter implements OwnerNotificationPort {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    EmailOwnerNotificationAdapter(
            JavaMailSender mailSender, @Value("${owner-notification.email.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public OwnerNotificationChannel channel() {
        return OwnerNotificationChannel.EMAIL;
    }

    @Override
    public void send(OwnerNotificationMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.recipientEmail());
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new OwnerNotificationDeliveryException("Email gönderilemedi: " + e.getMessage(), e);
        }
    }
}

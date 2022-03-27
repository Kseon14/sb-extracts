package com.am.sbextracts.service.integration;

import com.am.sbextracts.service.ResponderService;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import static com.am.sbextracts.service.integration.GAuthService.APPLICATION_NAME;
import static com.am.sbextracts.service.integration.GAuthService.JSON_FACTORY;
import static com.am.sbextracts.service.integration.GAuthService.getNetHttpTransport;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailService {

    @Value("${app.fromMail}")
    String from;

    private final GAuthService gAuthService;
    private final ResponderService slackResponderService;

    public void sendMessage(Set<String> toEmails, String subject, String text, String initiatorSlackId)
            throws IOException, MessagingException {
        Gmail service = getService(initiatorSlackId);
        for (String to : toEmails) {
            Message message = createMessageWithEmail(createEmail(to, from, subject, text));
            service.users().messages().send("me", message).execute();
            log.info("Message sent to: {}", to);
            slackResponderService.log(initiatorSlackId, "Message sent to:" + to);
        }
    }

    private Gmail getService(String initiatorSlackId) {
        return new Gmail.Builder(getNetHttpTransport(), JSON_FACTORY,
                gAuthService.getCredentials(initiatorSlackId, true))
                .setApplicationName(APPLICATION_NAME).build();
    }

    public static MimeMessage createEmail(String to,
                                          String from,
                                          String subject,
                                          String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(bodyText, "UTF-8", "html");
        //email.setContent(bodyText, "text/html");
        return email;
    }

    public static Message createMessageWithEmail(MimeMessage emailContent)
            throws IOException, MessagingException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

}

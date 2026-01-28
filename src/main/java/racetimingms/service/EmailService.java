package racetimingms.service;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Primary
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    private String env = Optional.ofNullable(System.getenv("ENV")).orElse(Optional.ofNullable(System.getProperty("ENV")).orElse("DEV"));
    private String hostName = "PROD".equals(env) ? "https://www.actionInThai.com" : "UAT".equals(env) ? "https://dioneuatsingle-env.eba-2emicsde.ap-southeast-1.elasticbeanstalk.com" : "http://localhost:3000";

    public void sendResetPasswordMail(String to, String username, String uuid) {
        try {
            String emailContent = buildResetPasswordMailEmailContent(to, username, uuid);
            sendEmail(to, "Reset your password", emailContent);
        } catch (Exception e) {
            log.error("Error occurred:", e);
        }
    }

    public void sendSyncErrorEmail(String[] to, String subject, String body) {
        try {
            MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            javaMailSender.send(helper.getMimeMessage());
        } catch (Exception e) {
            log.error("Error occurred while sending sync error email:", e);
        }
    }

    public void sendSyncErrorEmail(String to, String subject, String body) {
        String[] targets=to.split(",");
        sendSyncErrorEmail(targets,subject,body);
    }

    public void sendSyncErrorEmail(String to, String body) {
        
        sendSyncErrorEmail(to,"Synchronization Error ",body);
    }

    private void sendEmail(String to, String subject, String content) {
        try {
            MimeMessageHelper helper = new MimeMessageHelper(javaMailSender.createMimeMessage(), true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            javaMailSender.send(helper.getMimeMessage());
        } catch (Exception e) {
            log.error("Error occurred while sending email:", e);
        }
    }

    private String buildResetPasswordMailEmailContent(String to, String username, String uuid) {
        return "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\"/>" +
                "<title>Reset your password</title>" +
                "</head>" +
                "<body>" +
                "<p>สวัสดี " + username + "</p>" +
                "<p>เราได้รับคำขอการรีเซ็ตรหัสผ่านของบัญชีของคุณ: " + to + "</p>" +
                "<p>เพื่อรีเซ็ตรหัสผ่านของคุณ คลิกที่ปุ่มด้านล่างนี้ (กรุณารีเซ็ตรหัสผ่านภายใน 1 วัน):</p>" +
                "<a href= " + hostName + "/changepassword/" + uuid + ">\n" +
                "<button style=\"font-family: proxima_nova,'Open Sans','Lucida Grande','Segoe UI',Arial,Verdana,'Lucida Sans Unicode',Tahoma,'Sans Serif';font-size:20px;background: #1e2d4b;padding: 30px;border-radius: 20px;outline: none;color: white;cursor: pointer;margin-top: 50px;\">Active Account</button>\n" +
                "</a>" +
                "<p>หากคุณไม่ได้ขอรีเซ็ตรหัสผ่าน คุณไม่ต้องสนใจอีเมลนี้ จะไม่มีการเปลี่ยนแปลงใด ๆ กับบัญชีของคุณ</p>" +
                "<p>ขอบคุณ</p>" +
                "<p>ทีมงาน<br/>Action in Thai</p>" +
                "</body>" +
                "</html>";
    }
}

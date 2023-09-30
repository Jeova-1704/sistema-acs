package br.upe.acs.utils;

import br.upe.acs.model.Request;
import br.upe.acs.model.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import br.upe.acs.model.dto.EmailDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailUtils {

    private final JavaMailSender emailSender;

    public void sendVerificationCode(String email, String verificationCode) {
        EmailDTO emailDTO = new EmailDTO();

        emailDTO.setAssunto("Código de verificação - Sistema ACs UPE");
        emailDTO.setDestinatario(email);
        emailDTO.setMensagem(
                "Para confirmar seu email no Sistema ACs UPE:\n" +
                        "1 - Realize o login no sistema\n" +
                        "2 - Insira e envie este código na página de verificação do sistema: " + verificationCode);
        sendEmail(emailDTO);
    }

    public void sendRequestStatusChanged(Request request) {
        EmailDTO emailDTO = new EmailDTO();

        emailDTO.setDestinatario(request.getUser().getEmail());
        emailDTO.setAssunto("Modificação na sua requisição " + request.getId() + " - Sistema ACs UPE");
        emailDTO.setMensagem("Gostaríamos de informar que sua requisição " + request.getId()
                + " teve seu status alterado para " + request.getStatus().name() + ".\n" +
                "Para mais informações acesse o Sistema de ACs. " +
                "Em caso de erros entre em contato com o turmaestest@gmail.com.\n" +
                "Atenciosamente,\nCoordenação de " + request.getUser().getCourse() + ".");
        sendEmail(emailDTO);
    }

    public void sendRequestRecoveryPassword(User user, String token) {
        EmailDTO emailDTO = new EmailDTO();

        emailDTO.setDestinatario(user.getEmail());
        emailDTO.setAssunto("Solicitação de recuperação de conta - Sistema ACs UPE");
        emailDTO.setMensagem("Olá, " + user.getFullName() + ", \n" +
                "Nós recebemos um pedido para redefinir a senha do e-mail " + user.getEmail() + ".\n" +
                "Clique no link abaixo para redefinir sua senha:\n" +
                System.getenv("FRONTEND_URL") + "/account/reset/" + token);
        sendEmail(emailDTO);
    }

    private void sendEmail(EmailDTO emailInfo) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom("lapesupe@gmail.com");
        email.setTo(emailInfo.getDestinatario());
        email.setText(emailInfo.getMensagem());
        email.setSubject(emailInfo.getAssunto());

        emailSender.send(email);
    }
}

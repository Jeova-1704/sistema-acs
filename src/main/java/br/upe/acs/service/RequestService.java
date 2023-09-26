package br.upe.acs.service;

import br.upe.acs.controller.responses.RequisicaoSimplesResposta;
import br.upe.acs.model.Certificado;
import br.upe.acs.model.Curso;
import br.upe.acs.model.Requisicao;
import br.upe.acs.model.Usuario;
import br.upe.acs.model.enums.CertificadoStatusEnum;
import br.upe.acs.model.enums.EixoEnum;
import br.upe.acs.model.enums.RequisicaoStatusEnum;
import br.upe.acs.repository.CertificadoRepositorio;
import br.upe.acs.repository.RequisicaoRepositorio;
import br.upe.acs.utils.EmailUtils;
import br.upe.acs.utils.RequestPdfUtils;
import br.upe.acs.utils.exceptions.AcsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static br.upe.acs.utils.PaginationUtils.generatePagination;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequisicaoRepositorio repository;
    private final CertificadoRepositorio certificateRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final RequestPdfUtils requestPdfUtils;
    private final EmailUtils emailUtils;

    public Long createRequest(String email) {
        Usuario student = userService.findUserByEmail(email);
        List<Requisicao> requestsSketch = student.getRequisicoes().stream()
                .filter(requisicao -> requisicao.getStatusRequisicao().equals(RequisicaoStatusEnum.RASCUNHO)).toList();

        if (student.getHorasEnsino() + student.getHorasExtensao() + student.getHorasGestao() +
                student.getHorasPesquisa() >= student.getCurso().getHorasComplementares()) {
            throw new AcsException("The student has already completed his additional hours");
        }

        if (requestsSketch.size() >= 2) {
            throw new AcsException("Student can only have 2 draft requests");
        }

        Requisicao request = new Requisicao();
        request.setStatusRequisicao(RequisicaoStatusEnum.RASCUNHO);
        request.setCriacao(new Date());
        request.setUsuario(student);
        request.setCurso(student.getCurso());

        Requisicao requestSaved = repository.save(request);
        requestSaved.setIdRequisicao(String.format("%s-%05d", student.getCurso().getSigla(), requestSaved.getId()));
        repository.save(requestSaved);

        return requestSaved.getId();
    }

    public byte[] createRequestPdf(Long requestId) {
        Requisicao request = findRequestById(requestId);
        return requestPdfUtils.generateRequestPdf(request);
    }

    public String submitRequest(Long requestId) {
        Requisicao request = findRequestById(requestId);

        if (request.getStatusRequisicao() != RequisicaoStatusEnum.RASCUNHO) {
            throw new AcsException("This request has already been submitted");
        }

        if (request.getCertificados().isEmpty()) {
            throw new AcsException("A request needs at least one certificate");
        }
        List<Certificado> invalidCertificates = request.getCertificados().stream()
                .filter(certificado -> !isValidCertificate(certificado)).toList();
        if (!invalidCertificates.isEmpty()) {
            throw new AcsException(
                    "Certificates: " + String.join("; ", invalidCertificates.stream()
                            .map(certificado -> certificado.getId().toString()).toList())
                            + " have invalid data."
            );
        }
        String token = generateRequestToken();
        request.setToken(token);
        request.setDataDeSubmissao(new Date());
        request.setStatusRequisicao(RequisicaoStatusEnum.TRANSITO);
        modifyCertificates(request.getCertificados());
        repository.save(request);

        CompletableFuture.runAsync(() -> emailUtils.sendRequestStatusChanged(request));

        return token;
    }

    public Map<String, Object> listRequests(Boolean isArchived, RequisicaoStatusEnum status, Long userId, Long courseId, int page, int amount) {
        Usuario user = null;
        Curso course = null;
        if (userId != null){
            user = userService.findUserById(userId);
        }
        if (courseId != null) {
            course = courseService.findCourseById(courseId);
        }

        List<RequisicaoSimplesResposta> requests = repository.findWithFilters(isArchived, status, user, course)
                .stream().map(RequisicaoSimplesResposta::new).toList();

        return generatePagination(requests, page, amount);
    }

    public Map<String, Object> listStudentRequestsByAxle(Long studentId, EixoEnum axle, int page, int amount) {
        List<RequisicaoSimplesResposta> studentRequests = repository.findRequestsByUserIdAndAxle(studentId, axle)
                .stream().map(RequisicaoSimplesResposta::new).toList();

        return generatePagination(studentRequests, page, amount);
    }

    public Requisicao findRequestById(Long id) {
        return repository.findById(id).orElseThrow(() -> new AcsException("Request not found"));
    }

    public void archiveRequest(Long id, String email) {
        boolean isFinished = false;
        Requisicao request = repository.findById(id).orElseThrow(() -> new AcsException("Request not found"));
        Usuario user = userService.findUserByEmail(email);
        RequisicaoStatusEnum status = request.getStatusRequisicao();

        if (!user.equals(request.getUsuario())) {
            throw new AcsException("User not authorized to archive this request");
        }
        if (status == RequisicaoStatusEnum.ACEITO || status == RequisicaoStatusEnum.NEGADO) {
            isFinished = true;
        }
        if (!isFinished) {
            throw new AcsException("Unable to archive an unfinished request");
        }

        if (!request.isArquivada()) {
            request.setArquivada(true);
            repository.save(request);
        } else {
            throw new AcsException("Request already archived");
        }
    }

    public void unarchiveRequest(Long id, String email) {
        Requisicao request = repository.findById(id).orElseThrow();
        Usuario user = userService.findUserByEmail(email);

        if (!user.equals(request.getUsuario())) {
            throw new AcsException("User not authorized to unarchive this request");
        }

        if (request.isArquivada()) {
            request.setArquivada(false);
            repository.save(request);
        } else {
            throw new AcsException("The request is not archived");
        }
    }

    public void deleteRequest(Long requestId, String email) {
        Requisicao request = findRequestById(requestId);
        if (!request.getUsuario().getEmail().equals(email)) {
            throw new AcsException("User without permission to delete this request");
        }

        if (!request.getStatusRequisicao().equals(RequisicaoStatusEnum.RASCUNHO)) {
            throw new AcsException("A request already submitted cannot be deleted");
        }

        repository.deleteById(requestId);
    }

    private boolean isValidCertificate(Certificado certificate) {
        boolean isValid = true;

        if (certificate.getCertificado() == null) {
            isValid = false;
        } else if (certificate.getTitulo() == null || certificate.getTitulo().isBlank()) {
            isValid = false;
        } else if (certificate.getDataInicial().after(new Date())) {
            isValid = false;
        } else if (certificate.getDataFinal().after(new Date())) {
            isValid = false;
        } else if (certificate.getCargaHoraria() < 1) {
            isValid = false;
        } else if (certificate.getAtividade() == null) {
            isValid = false;
        }

        return isValid;
    }

    private String generateRequestToken() {
        String characters = "0123456789!@#$%.*";
        Random random = new Random();
        StringBuilder partialToken = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(characters.length());
            partialToken.append(characters.charAt(index));
        }

        Instant timeStamp = Instant.now();
        long epochSeconds = timeStamp.getEpochSecond();

        return partialToken + Long.toString(epochSeconds);
    }

    private void modifyCertificates(List<Certificado> certificates) {
        for (Certificado certificate : certificates) {
            certificate.setStatusCertificado(CertificadoStatusEnum.ENCAMINHADO_COORDENACAO);
            certificateRepository.save(certificate);
        }
    }
}

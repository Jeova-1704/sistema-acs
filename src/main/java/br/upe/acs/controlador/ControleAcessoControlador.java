package br.upe.acs.controlador;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.upe.acs.controlador.respostas.AutenticacaoResposta;
import br.upe.acs.dominio.dto.LoginDTO;
import br.upe.acs.dominio.dto.RegistroDTO;
import br.upe.acs.servico.ControleAcessoServico;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/acesso/auth")
@RequiredArgsConstructor
@CrossOrigin
public class ControleAcessoControlador {
	
	private final ControleAcessoServico servico;
	
	@Operation(summary = "Cadastro de usuário")
	@PostMapping("/cadastro")
	public ResponseEntity<?> cadastrarUsuario(@RequestBody RegistroDTO registro) {
		try {
			return ResponseEntity.ok(servico.cadastrarUsuario(registro));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
	
	@Operation(summary = "Login de usuário")
	@PostMapping("/login")
	public ResponseEntity<AutenticacaoResposta> loginUsuario(@RequestBody LoginDTO login) {
		return ResponseEntity.ok(servico.loginUsuario(login));
	}
}

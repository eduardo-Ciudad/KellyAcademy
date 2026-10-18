package eduar.studymindredacao.adapter.in.web.security;

import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Não é @Component de propósito: é instanciado no SecurityConfig para não ser
 * registrado duas vezes (como filtro do servlet e na cadeia do Security).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String PREFIXO_BEARER = "Bearer ";

    private final ValidarSessaoService validarSessao;

    public JwtAuthenticationFilter(ValidarSessaoService validarSessao) {
        this.validarSessao = validarSessao;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null
                && header.startsWith(PREFIXO_BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIXO_BEARER.length()).trim();
            // Assinatura, expiração e versão da sessão (senha trocada derruba o token). Recusado: segue sem
            // autenticação e o entry point responde 401
            validarSessao.validar(token).ifPresent(dados -> {
                var principal = new UsuarioAutenticado(dados.usuarioId(), dados.email(), dados.role());
                var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + dados.role().name()))
                );
                autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            });
        }

        filterChain.doFilter(request, response);
    }
}

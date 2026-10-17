package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class UsuarioRepositoryEmMemoria implements UsuarioRepositoryPort {
    private final Map<UUID, Usuario> usuarios = new HashMap<>();

    @Override
    public Usuario salvar(Usuario usuario) {
        UUID id = usuario.id() != null ? usuario.id() : UUID.randomUUID();
        OffsetDateTime criadoEm = usuario.criadoEm() != null ? usuario.criadoEm() : OffsetDateTime.now();
        var salvo = new Usuario(id, usuario.nome(), usuario.email(), usuario.senhaHash(), usuario.role(), criadoEm,
                usuario.emailVerificadoEm());
        usuarios.put(id, salvo);
        return salvo;
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return Optional.ofNullable(usuarios.get(id));
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        String normalizado = email.trim().toLowerCase();
        return usuarios.values().stream().filter(u -> u.email().equals(normalizado)).findFirst();
    }

    @Override
    public boolean existePorEmail(String email) {
        return buscarPorEmail(email).isPresent();
    }

    @Override
    public void excluirPorId(UUID id) {
        usuarios.remove(id);
    }
}

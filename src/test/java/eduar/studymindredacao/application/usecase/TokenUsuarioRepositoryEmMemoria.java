package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Mesmas regras do adapter JPA (consumo só se válido, invalidação só dos ativos), sem banco. */
class TokenUsuarioRepositoryEmMemoria implements TokenUsuarioRepositoryPort {
    private final Map<UUID, TokenUsuario> tokens = new LinkedHashMap<>();

    @Override
    public TokenUsuario salvar(TokenUsuario token) {
        UUID id = token.id() != null ? token.id() : UUID.randomUUID();
        var salvo = new TokenUsuario(id, token.usuarioId(), token.tipo(), token.tokenHash(),
                token.criadoEm(), token.expiraEm(), token.usadoEm());
        tokens.put(id, salvo);
        return salvo;
    }

    @Override
    public Optional<UUID> consumirValido(String tokenHash, TipoTokenUsuario tipo, OffsetDateTime agora) {
        return tokens.values().stream()
                .filter(t -> t.tokenHash().equals(tokenHash) && t.tipo() == tipo && t.estaValido(agora))
                .findFirst()
                .map(t -> {
                    marcarUsado(t, agora);
                    return t.usuarioId();
                });
    }

    @Override
    public int invalidarAtivos(UUID usuarioId, TipoTokenUsuario tipo, OffsetDateTime agora) {
        var ativos = tokens.values().stream()
                .filter(t -> t.usuarioId().equals(usuarioId) && t.tipo() == tipo && t.estaValido(agora))
                .toList();
        ativos.forEach(t -> marcarUsado(t, agora));
        return ativos.size();
    }

    @Override
    public int excluirAntigos(OffsetDateTime corte) {
        var antigos = tokens.values().stream()
                .filter(t -> t.expiraEm().isBefore(corte) || (t.usadoEm() != null && t.usadoEm().isBefore(corte)))
                .map(TokenUsuario::id)
                .toList();
        antigos.forEach(tokens::remove);
        return antigos.size();
    }

    List<TokenUsuario> todos() {
        return new ArrayList<>(tokens.values());
    }

    private void marcarUsado(TokenUsuario t, OffsetDateTime agora) {
        tokens.put(t.id(), new TokenUsuario(t.id(), t.usuarioId(), t.tipo(), t.tokenHash(),
                t.criadoEm(), t.expiraEm(), agora));
    }

    @Override
    public Optional<OffsetDateTime> ultimaEmissao(UUID usuarioId, TipoTokenUsuario tipo) {
        return tokens.values().stream()
                .filter(t -> t.usuarioId().equals(usuarioId) && t.tipo() == tipo)
                .map(TokenUsuario::criadoEm)
                .max(OffsetDateTime::compareTo);
    }
}

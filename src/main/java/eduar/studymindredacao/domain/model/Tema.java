package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.OrigemTema;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tema de redação. Os temas PERSONALIZADO têm dono (criadoPorId) e só ele os enxerga;
 * todas as outras origens são do sistema e nunca têm dono.
 */
public record Tema(
        UUID id,
        String titulo,
        String textosMotivadores,
        OrigemTema origem,
        Short ano,
        Boolean ativo,
        OffsetDateTime criadoEm,
        UUID criadoPorId
) {
    public static final int TITULO_PERSONALIZADO_MINIMO = 10;
    public static final int TITULO_PERSONALIZADO_MAXIMO = 200;
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    public Tema {
        Validacoes.validarTextoObrigatorio(titulo, "titulo", 255);
        Validacoes.requererNaoNulo(origem, "origem");
        if (ativo == null) {
            ativo = Boolean.TRUE;
        }
        // Mesma regra da ck_temas_personalizado_dono (V26)
        if ((origem == OrigemTema.PERSONALIZADO) != (criadoPorId != null)) {
            throw new IllegalArgumentException("só tema personalizado tem dono, e ele sempre tem");
        }
    }

    /** Temas do sistema (oficiais, autorais e possíveis): sem dono. */
    public Tema(UUID id, String titulo, String textosMotivadores, OrigemTema origem, Short ano, Boolean ativo,
                OffsetDateTime criadoEm) {
        this(id, titulo, textosMotivadores, origem, ano, ativo, criadoEm, null);
    }

    /** Tema digitado pelo aluno: título normalizado, de 10 a 200 caracteres, ativo e sem textos motivadores. */
    public static Tema personalizado(String titulo, UUID usuarioId) {
        Validacoes.requererNaoNulo(usuarioId, "usuarioId");
        String normalizado = normalizarTitulo(titulo);
        if (normalizado.length() < TITULO_PERSONALIZADO_MINIMO) {
            throw new IllegalArgumentException(
                    "titulo deve ter no mínimo " + TITULO_PERSONALIZADO_MINIMO + " caracteres");
        }
        Validacoes.validarTamanhoMaximo(normalizado, "titulo", TITULO_PERSONALIZADO_MAXIMO);
        return new Tema(null, normalizado, null, OrigemTema.PERSONALIZADO, null, true, null, usuarioId);
    }

    /** Tira as pontas e junta espaços repetidos (inclusive quebras de linha) num espaço só. */
    public static String normalizarTitulo(String titulo) {
        Validacoes.validarTextoObrigatorio(titulo, "titulo", null);
        return ESPACOS.matcher(titulo.strip()).replaceAll(" ");
    }

    /** Temas do sistema são de todos; o personalizado, só do dono. */
    public boolean visivelPara(UUID usuarioId) {
        return origem != OrigemTema.PERSONALIZADO || criadoPorId.equals(usuarioId);
    }
}

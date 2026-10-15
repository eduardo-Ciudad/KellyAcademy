package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.ResultadoAvaliacaoIA;
import eduar.studymindredacao.domain.model.SolicitacaoAvaliacao;
import eduar.studymindredacao.domain.service.RegrasDaGrade;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Mede a correção por IA contra redações de referência (issue #28).
 * <p>
 * A correção da Kelly é propositalmente mais rigorosa que a do ENEM: a nota oficial do INEP
 * é o TETO, não o alvo. As metas são: nunca acima do oficial, consistência entre execuções,
 * sanidade inferior (erro grosseiro de leitura) e teto nas redações de verificação.
 * <p>
 * Chama a API real do Gemini: fica fora do {@code mvn test} e do CI (tag "calibracao").
 * Não reprova: só gera o relatório. Ver {@code src/test/resources/calibracao/README.md}.
 */
@Tag("calibracao")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class CalibracaoRunnerTest {

    private static final Path PASTA_REDACOES = Path.of("src/test/resources/calibracao");
    private static final Path PASTA_SAIDA = Path.of("target/calibracao");
    /** Nota oficial é média de dois corretores (múltiplos de 20); a IA responde em múltiplos de 40. */
    private static final int FOLGA_ACIMA_DO_OFICIAL = 20;
    private static final int AMPLITUDE_MAXIMA = 80;
    private static final int LIMITE_ABAIXO_DO_OFICIAL = 200;

    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * tipo "oficial": tem nota humana por competência (teto). tipo "verificacao": sem nota oficial,
     * só um teto de total (ex.: caso da #23, que não pode voltar a tirar 1000).
     */
    record RedacaoCalibracao(String id, String tipo, String tema, String texto, int[] notasOficiais,
                             int totalOficial, int tetoTotal, boolean conferido) {
        boolean oficial() {
            return "oficial".equals(tipo);
        }
    }

    /** Uma execução da correção: notas C1..C5, ou erro se a chamada falhou. */
    /** Notas finais (após a grade), notas dadas pela IA e quais competências a grade limitou. */
    record Execucao(int[] notas, int[] notasIA, boolean[] ajustadas, String erro, long milissegundos) {
        boolean ok() {
            return erro == null;
        }

        int total() {
            int soma = 0;
            for (int nota : notas) {
                soma += nota;
            }
            return soma;
        }
    }

    @Test
    void medirDesvioDaCorrecao() throws IOException {
        List<RedacaoCalibracao> redacoes = carregarRedacoes();
        assumeFalse(redacoes.isEmpty(), "nenhuma redação em " + PASTA_REDACOES.toAbsolutePath());

        // O IntelliJ desativa as condições @Enabled* ao rodar uma classe direto: confere a chave aqui também.
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY não definida: configure na Run Configuration (Environment variables) ou no terminal");
        }

        Properties app = carregarApplicationProperties();
        int repeticoes = Integer.getInteger("calibracao.repeticoes", 3);
        // Modelos não-lite do plano grátis aceitam só 5 requisições/min: use -Dcalibracao.intervalo=13
        int intervaloSegundos = Integer.getInteger("calibracao.intervalo", 0);
        String modelo = System.getProperty("calibracao.modelo", resolver(app, "gemini.avaliacao.modelo"));
        String raciocinio = System.getProperty("calibracao.raciocinio", resolver(app, "gemini.avaliacao.raciocinio"));
        double temperatura = Double.parseDouble(
                System.getProperty("calibracao.temperatura", app.getProperty("gemini.temperatura")));
        String baseUrl = app.getProperty("gemini.base-url");

        var adapter = new GeminiAvaliacaoAdapter(
                new PromptAvaliacaoBuilder(),
                new GeminiClient(baseUrl, apiKey, 300),
                modelo,
                temperatura,
                raciocinio);

        String carimbo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path pastaRespostas = PASTA_SAIDA.resolve("respostas").resolve(carimbo);
        Files.createDirectories(pastaRespostas);

        List<List<Execucao>> resultados = new ArrayList<>();
        for (RedacaoCalibracao redacao : redacoes) {
            List<Execucao> execucoes = new ArrayList<>();
            for (int i = 1; i <= repeticoes; i++) {
                if (intervaloSegundos > 0 && !(resultados.isEmpty() && execucoes.isEmpty())) {
                    pausar(intervaloSegundos);
                }
                System.out.printf("[calibracao] %s — execução %d/%d%n", redacao.id(), i, repeticoes);
                execucoes.add(executar(adapter, redacao, pastaRespostas.resolve(redacao.id() + "-" + i + ".json")));
            }
            resultados.add(execucoes);
        }

        String relatorio = montarRelatorio(redacoes, resultados, modelo, raciocinio, temperatura, repeticoes, carimbo);
        Path arquivo = PASTA_SAIDA.resolve("relatorio-" + carimbo + ".md");
        Files.writeString(arquivo, relatorio, StandardCharsets.UTF_8);
        System.out.println(relatorio);
        System.out.println("[calibracao] relatório salvo em " + arquivo.toAbsolutePath());
    }

    private static void pausar(int segundos) {
        try {
            Thread.sleep(segundos * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("calibração interrompida", e);
        }
    }

    private Execucao executar(GeminiAvaliacaoAdapter adapter, RedacaoCalibracao redacao, Path arquivoResposta) {
        long inicio = System.nanoTime();
        try {
            ResultadoAvaliacaoIA daIA = adapter.avaliar(new SolicitacaoAvaliacao(redacao.tema(), redacao.texto()));
            Files.writeString(arquivoResposta, daIA.respostaBrutaJson(), StandardCharsets.UTF_8);
            // Mede o que vai para produção: a nota depois das regras da grade (#27)
            RegrasDaGrade.Resultado conferido = RegrasDaGrade.aplicar(daIA);
            int[] notas = new int[5];
            int[] notasIA = new int[5];
            boolean[] ajustadas = new boolean[5];
            for (CompetenciaAvaliada competencia : conferido.avaliacao().competencias()) {
                notas[competencia.numero() - 1] = competencia.nota();
            }
            for (CompetenciaAvaliada competencia : daIA.competencias()) {
                notasIA[competencia.numero() - 1] = competencia.nota();
            }
            for (RegrasDaGrade.Ajuste ajuste : conferido.ajustes()) {
                ajustadas[ajuste.competencia() - 1] = true;
            }
            return new Execucao(notas, notasIA, ajustadas, null, (System.nanoTime() - inicio) / 1_000_000);
        } catch (RuntimeException | IOException e) {
            return new Execucao(new int[5], new int[5], new boolean[5], e.getClass().getSimpleName() + ": " + e.getMessage(),
                    (System.nanoTime() - inicio) / 1_000_000);
        }
    }

    // ---------------------------------------------------------------- relatório

    private String montarRelatorio(List<RedacaoCalibracao> redacoes, List<List<Execucao>> resultados,
                                   String modelo, String raciocinio, double temperatura, int repeticoes,
                                   String carimbo) {
        var md = new StringBuilder();
        md.append("# Calibração da correção — ").append(carimbo).append("\n\n");
        md.append("- Prompt: `").append(nomeDoPrompt()).append("`\n");
        md.append("- Modelo: `").append(modelo).append("` · raciocínio: `")
                .append(raciocinio == null || raciocinio.isBlank() ? "padrão do modelo" : raciocinio)
                .append("` · temperatura ").append(temperatura).append("\n");
        double tempoMedio = resultados.stream().flatMap(List::stream).filter(Execucao::ok)
                .mapToLong(Execucao::milissegundos).average().orElse(0) / 1000.0;
        md.append(String.format(Locale.ROOT, "- Tempo médio por correção: %.1f s%n", tempoMedio));
        md.append("- Redações: ").append(redacoes.size()).append(" · repetições: ").append(repeticoes).append("\n");
        md.append("- Régua: correção rigorosa. A nota oficial do INEP é o teto, não o alvo.\n\n");

        int acimaDoOficial = 0;
        int abaixoDoLimite = 0;
        int redacoesInconsistentes = 0;
        int verificacoesFalhas = 0;
        int falhas = 0;
        double somaErroExecucao = 0;
        int execucoesOficiais = 0;
        double[] somaVies = new double[5];
        int[] contagemVies = new int[5];
        int[] competenciasAcima = new int[5];
        int[] ajustesDaGrade = new int[5];
        for (List<Execucao> execucoes : resultados) {
            for (Execucao execucao : execucoes) {
                if (execucao.ok()) {
                    for (int c = 0; c < 5; c++) {
                        if (execucao.ajustadas()[c]) {
                            ajustesDaGrade[c]++;
                        }
                    }
                }
            }
        }

        md.append("## Redações com nota oficial\n\n");
        md.append("| Redação | Oficial | Execuções | Média | Viés | Amplitude | C1 | C2 | C3 | C4 | C5 | Obs. |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int r = 0; r < redacoes.size(); r++) {
            RedacaoCalibracao redacao = redacoes.get(r);
            if (!redacao.oficial()) {
                continue;
            }
            List<Execucao> validas = resultados.get(r).stream().filter(Execucao::ok).toList();
            falhas += resultados.get(r).size() - validas.size();
            if (validas.isEmpty()) {
                md.append(String.format("| %s | %d | — | — | — | — | — | — | — | — | — | todas as execuções falharam |%n",
                        redacao.id(), redacao.totalOficial()));
                continue;
            }

            List<String> obs = new ArrayList<>();
            if (!redacao.conferido()) {
                obs.add("transcrição não conferida");
            }
            for (Execucao execucao : validas) {
                int diferenca = execucao.total() - redacao.totalOficial();
                somaErroExecucao += Math.abs(diferenca);
                execucoesOficiais++;
                if (diferenca > FOLGA_ACIMA_DO_OFICIAL) {
                    acimaDoOficial++;
                    obs.add("execução " + execucao.total() + " acima do oficial");
                }
                if (diferenca < -LIMITE_ABAIXO_DO_OFICIAL) {
                    abaixoDoLimite++;
                    obs.add("execução " + execucao.total() + " abaixo do limite");
                }
            }
            int amplitude = amplitude(validas);
            if (amplitude > AMPLITUDE_MAXIMA) {
                redacoesInconsistentes++;
                obs.add("inconsistente");
            }

            String[] colunas = new String[5];
            for (int c = 0; c < 5; c++) {
                final int indice = c;
                double media = validas.stream().mapToInt(e -> e.notas()[indice]).average().orElse(0);
                colunas[c] = String.format(Locale.ROOT, "%.0f (%d)", media, redacao.notasOficiais()[c]);
                for (Execucao execucao : validas) {
                    int diferenca = execucao.notas()[c] - redacao.notasOficiais()[c];
                    somaVies[c] += diferenca;
                    contagemVies[c]++;
                    if (diferenca > FOLGA_ACIMA_DO_OFICIAL) {
                        competenciasAcima[c]++;
                    }
                }
            }
            double media = validas.stream().mapToInt(Execucao::total).average().orElse(0);
            md.append(String.format(Locale.ROOT, "| %s | %d | %s | %.0f | %+.0f | %d | %s | %s | %s | %s | %s | %s |%n",
                    redacao.id(), redacao.totalOficial(), totais(validas), media, media - redacao.totalOficial(),
                    amplitude, colunas[0], colunas[1], colunas[2], colunas[3], colunas[4], String.join("; ", obs)));
        }
        md.append("\nEntre parênteses: nota oficial da competência.\n\n");

        md.append("## Redações de verificação (teto)\n\n");
        md.append("| Redação | Teto | Execuções | Máx. | Amplitude | Status |\n|---|---|---|---|---|---|\n");
        boolean temVerificacao = false;
        for (int r = 0; r < redacoes.size(); r++) {
            RedacaoCalibracao redacao = redacoes.get(r);
            if (redacao.oficial()) {
                continue;
            }
            temVerificacao = true;
            List<Execucao> validas = resultados.get(r).stream().filter(Execucao::ok).toList();
            falhas += resultados.get(r).size() - validas.size();
            if (validas.isEmpty()) {
                verificacoesFalhas++;
                md.append(String.format("| %s | %d | — | — | — | todas as execuções falharam |%n", redacao.id(), redacao.tetoTotal()));
                continue;
            }
            int maximo = validas.stream().mapToInt(Execucao::total).max().orElse(0);
            int amplitude = amplitude(validas);
            boolean ok = maximo <= redacao.tetoTotal() && maximo < 1000;
            if (!ok) {
                verificacoesFalhas++;
            }
            if (amplitude > AMPLITUDE_MAXIMA) {
                redacoesInconsistentes++;
            }
            md.append(String.format("| %s | %d | %s | %d | %d | %s |%n", redacao.id(), redacao.tetoTotal(),
                    totais(validas), maximo, amplitude, ok ? "OK" : "ACIMA DO TETO"));
        }
        if (!temVerificacao) {
            md.append("| — | — | — | — | — | nenhuma redação de verificação |\n");
        }

        md.append("\n## Viés por competência (IA − oficial)\n\n");
        md.append("| | C1 | C2 | C3 | C4 | C5 |\n|---|---|---|---|---|---|\n| Viés médio |");
        for (int c = 0; c < 5; c++) {
            double vies = contagemVies[c] == 0 ? 0 : somaVies[c] / contagemVies[c];
            md.append(String.format(Locale.ROOT, " %+.0f |", vies));
        }
        md.append("\n| Execuções acima do oficial |");
        for (int c = 0; c < 5; c++) {
            md.append(" ").append(competenciasAcima[c]).append(" |");
        }
        md.append("\n| Notas limitadas pela grade (#27) |");
        for (int c = 0; c < 5; c++) {
            md.append(" ").append(ajustesDaGrade[c]).append(" |");
        }
        md.append("\n\nNegativo = mais rigorosa que o INEP (esperado). Positivo = mais generosa (a evitar).\n");
        md.append("As notas do relatório já são as finais, depois das regras da grade.\n\n");

        boolean semDados = execucoesOficiais == 0;
        boolean metaOk = !semDados && acimaDoOficial == 0 && abaixoDoLimite == 0
                && redacoesInconsistentes == 0 && verificacoesFalhas == 0;

        md.append("## Metas\n\n");
        if (semDados) {
            md.append("- **Nenhuma execução válida**: métricas indisponíveis (ver Falhas).\n");
        }
        md.append(String.format("- Execuções acima do oficial (+%d): **%d** (meta 0)%n", FOLGA_ACIMA_DO_OFICIAL, acimaDoOficial));
        md.append(String.format("- Redações com amplitude acima de %d: **%d** (meta 0)%n", AMPLITUDE_MAXIMA, redacoesInconsistentes));
        md.append(String.format("- Execuções mais de %d abaixo do oficial: **%d** (meta 0)%n", LIMITE_ABAIXO_DO_OFICIAL, abaixoDoLimite));
        md.append(String.format("- Verificações acima do teto ou com 1000: **%d** (meta 0)%n", verificacoesFalhas));
        md.append(semDados
                ? "- Erro médio por execução: —\n"
                : String.format(Locale.ROOT, "- Erro médio por execução (informativo): %.0f%n", somaErroExecucao / execucoesOficiais));
        md.append(String.format("- Execuções com falha: %d%n", falhas));
        md.append("- Resultado: **").append(metaOk ? "OK" : "NÃO ATINGIDO").append("**\n");

        if (falhas > 0) {
            md.append("\n## Falhas\n\n");
            for (int r = 0; r < redacoes.size(); r++) {
                for (Execucao execucao : resultados.get(r)) {
                    if (!execucao.ok()) {
                        md.append("- ").append(redacoes.get(r).id()).append(": ").append(execucao.erro()).append("\n");
                    }
                }
            }
        }
        return md.toString();
    }

    private static int amplitude(List<Execucao> execucoes) {
        int min = execucoes.stream().mapToInt(Execucao::total).min().orElse(0);
        int max = execucoes.stream().mapToInt(Execucao::total).max().orElse(0);
        return max - min;
    }

    private static String totais(List<Execucao> execucoes) {
        return String.join(", ", execucoes.stream().map(e -> String.valueOf(e.total())).toList());
    }

    // ---------------------------------------------------------------- carga

    private List<RedacaoCalibracao> carregarRedacoes() throws IOException {
        if (!Files.isDirectory(PASTA_REDACOES)) {
            return List.of();
        }
        List<Path> arquivos;
        try (Stream<Path> stream = Files.list(PASTA_REDACOES)) {
            arquivos = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        List<RedacaoCalibracao> redacoes = new ArrayList<>();
        for (Path arquivo : arquivos) {
            redacoes.add(lerRedacao(arquivo));
        }
        return redacoes;
    }

    private RedacaoCalibracao lerRedacao(Path arquivo) throws IOException {
        JsonNode no = json.readTree(Files.readString(arquivo, StandardCharsets.UTF_8));
        String id = no.path("id").asString("");
        String tipo = no.path("tipo").asString("oficial");
        String tema = no.path("tema").asString("");
        String texto = no.path("texto").asString("");
        if (id.isBlank() || tema.isBlank() || texto.isBlank()) {
            throw new IllegalStateException(arquivo + ": id, tema e texto são obrigatórios");
        }
        boolean conferido = no.path("conferido").asBoolean(false);

        if ("verificacao".equals(tipo)) {
            int teto = no.path("teto_total").asInt(-1);
            if (teto <= 0) {
                throw new IllegalStateException(arquivo + ": redação de verificação precisa de teto_total");
            }
            return new RedacaoCalibracao(id, tipo, tema, texto, new int[5], 0, teto, conferido);
        }
        if (!"oficial".equals(tipo)) {
            throw new IllegalStateException(arquivo + ": tipo deve ser \"oficial\" ou \"verificacao\"");
        }

        JsonNode notas = no.path("notas_oficiais");
        int[] notasOficiais = new int[5];
        int soma = 0;
        for (int c = 0; c < 5; c++) {
            JsonNode nota = notas.path("c" + (c + 1));
            if (!nota.isInt()) {
                throw new IllegalStateException(arquivo + ": notas_oficiais.c" + (c + 1) + " ausente");
            }
            notasOficiais[c] = nota.asInt();
            soma += notasOficiais[c];
        }
        int total = no.path("total_oficial").asInt(-1);
        if (total != soma) {
            throw new IllegalStateException(arquivo + ": total_oficial (" + total + ") difere da soma (" + soma + ")");
        }
        return new RedacaoCalibracao(id, tipo, tema, texto, notasOficiais, total, total, conferido);
    }

    /**
     * Resolve placeholders do application.properties como o Spring faria:
     * {@code ${VARIAVEL:padrão}} usa a variável de ambiente ou outra propriedade, senão o padrão.
     */
    private static String resolver(Properties app, String chave) {
        return resolverValor(app, app.getProperty(chave, ""), 0);
    }

    private static String resolverValor(Properties app, String valor, int profundidade) {
        if (valor == null || profundidade > 5 || !valor.startsWith("${") || !valor.endsWith("}")) {
            return valor;
        }
        String miolo = valor.substring(2, valor.length() - 1);
        int separador = miolo.indexOf(':');
        String nome = separador < 0 ? miolo : miolo.substring(0, separador);
        String padrao = separador < 0 ? "" : miolo.substring(separador + 1);
        String ambiente = System.getenv(nome);
        if (ambiente != null && !ambiente.isBlank()) {
            return ambiente;
        }
        if (app.containsKey(nome)) {
            return resolverValor(app, app.getProperty(nome), profundidade + 1);
        }
        return resolverValor(app, padrao, profundidade + 1);
    }

    private static Properties carregarApplicationProperties() throws IOException {
        var propriedades = new Properties();
        try (InputStream in = CalibracaoRunnerTest.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException("application.properties não encontrado no classpath");
            }
            propriedades.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return propriedades;
    }

    /** Lido do PromptAvaliacaoBuilder para o relatório registrar qual versão do prompt foi medida. */
    private static String nomeDoPrompt() {
        try {
            var campo = PromptAvaliacaoBuilder.class.getDeclaredField("RECURSO");
            campo.setAccessible(true);
            return (String) campo.get(null);
        } catch (ReflectiveOperationException e) {
            return "desconhecido";
        }
    }
}

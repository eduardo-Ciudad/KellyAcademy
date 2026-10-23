package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.exception.TranscricaoIAException;
import eduar.studymindredacao.domain.model.ImagemRedacao;
import eduar.studymindredacao.domain.model.ResultadoTranscricao;
import eduar.studymindredacao.domain.port.TranscricaoIAPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;


@Component
public class GeminiTranscricaoAdapter implements TranscricaoIAPort {
    private final PromptTranscricaoBuilder promptBuilder;
    private final GeminiClient client;
    private final GeminiTranscricaoParser parser;
    private final String modelo;
    private final double temperatura;

    public GeminiTranscricaoAdapter(
            PromptTranscricaoBuilder promptBuilder,
            @Qualifier("geminiClientTranscricao") GeminiClient client,
            @Value("${gemini.transcricao.modelo}") String modelo,
            @Value("${gemini.transcricao.temperatura}") double temperatura
    ) {
        this.promptBuilder = promptBuilder;
        this.client = client;
        this.parser = new GeminiTranscricaoParser(JsonMapper.builder().build());
        this.modelo = modelo;
        this.temperatura = temperatura;
    }

    @Override
    public ResultadoTranscricao transcrever(List<ImagemRedacao> imagens) {
        if (imagens == null || imagens.isEmpty()) {
            throw new IllegalArgumentException("a transcrição precisa de ao menos uma imagem");
        }
        List<GeminiParte> partes = new ArrayList<>();
        for (ImagemRedacao imagem : imagens) {
            partes.add(GeminiParte.Imagem.de(imagem.tipo().mimeType(), imagem.bytes()));
        }
        partes.add(new GeminiParte.Texto(promptBuilder.construir(imagens.size())));

        try {
            return client.gerar(modelo, temperatura, partes, parser::interpretar);
        } catch (GeminiException e) {
            throw new TranscricaoIAException(e.getMessage(), e, e.custo());
        }
    }
}
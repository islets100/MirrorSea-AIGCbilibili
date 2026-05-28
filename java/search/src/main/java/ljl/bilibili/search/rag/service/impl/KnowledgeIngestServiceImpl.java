package ljl.bilibili.search.rag.service.impl;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.service.KnowledgeIngestService;
import ljl.bilibili.search.rag.support.RagSegmentIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
public class KnowledgeIngestServiceImpl implements KnowledgeIngestService {

    @Resource
    @Qualifier("knowledgeEmbeddingStore")
    private EmbeddingStore<TextSegment> knowledgeEmbeddingStore;

    @Resource
    private EmbeddingModel creatorEmbeddingModel;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    private final ApacheTikaDocumentParser documentParser = new ApacheTikaDocumentParser();

    @Override
    public int ingest() {
        knowledgeEmbeddingStore.removeAll();
        Path knowledgeDir = Paths.get(creatorRagProperties.getKnowledgeDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(knowledgeDir)) {
            log.warn("Knowledge directory not found: {}", knowledgeDir);
            return 0;
        }
        DocumentSplitter splitter = DocumentSplitters.recursive(
                creatorRagProperties.getChunkSize(),
                creatorRagProperties.getChunkOverlap());
        int count = 0;
        try (Stream<Path> paths = Files.walk(knowledgeDir)) {
            List<Path> files = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                    .filter(this::supportedFile)
                    .forEach(files::add);
            for (Path file : files) {
                count += ingestFile(file, knowledgeDir, splitter);
            }
        } catch (IOException e) {
            log.error("Knowledge ingest failed", e);
        }
        log.info("Knowledge ingest completed (LangChain4j), chunks={}", count);
        return count;
    }

    private boolean supportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".pdf");
    }

    private int ingestFile(Path file, Path rootDir, DocumentSplitter splitter) {
        try {
            Document document = FileSystemDocumentLoader.loadDocument(file, documentParser);
            if (document.text() == null || document.text().trim().isEmpty()) {
                return 0;
            }
            String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
            String category = guessCategory(relativePath);
            Metadata baseMeta = new Metadata()
                    .put("source_file", relativePath)
                    .put("category", category);
            Document enriched = Document.from(document.text(), baseMeta);
            List<TextSegment> segments = splitter.split(enriched);
            List<TextSegment> withIds = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                String chunkId = "kb_" + relativePath.replaceAll("[^a-zA-Z0-9]", "_") + "_" + i;
                Metadata chunkMeta = new Metadata()
                        .put("chunk_id", chunkId)
                        .put("source_file", relativePath)
                        .put("category", category);
                withIds.add(TextSegment.from(segments.get(i).text(), chunkMeta));
            }
            RagSegmentIngestor.ingest(creatorEmbeddingModel, knowledgeEmbeddingStore, withIds);
            return withIds.size();
        } catch (Exception e) {
            log.warn("Failed to ingest file: {}", file, e);
            return 0;
        }
    }

    private static String guessCategory(String path) {
        if (path.contains("运营") || path.contains("gemini")) {
            return "运营规范";
        }
        return "通用";
    }
}

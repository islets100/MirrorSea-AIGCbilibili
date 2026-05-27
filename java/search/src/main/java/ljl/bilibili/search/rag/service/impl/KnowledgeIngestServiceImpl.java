package ljl.bilibili.search.rag.service.impl;

import com.alibaba.fastjson.JSON;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.constant.RagConstant;
import ljl.bilibili.search.rag.service.EmbeddingService;
import ljl.bilibili.search.rag.service.KnowledgeIngestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Slf4j
public class KnowledgeIngestServiceImpl implements KnowledgeIngestService {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    private final Tika tika = new Tika();

    @Override
    public int ingest() {
        Path knowledgeDir = Paths.get(creatorRagProperties.getKnowledgeDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(knowledgeDir)) {
            log.warn("Knowledge directory not found: {}", knowledgeDir);
            return 0;
        }
        int count = 0;
        try (Stream<Path> paths = Files.walk(knowledgeDir)) {
            List<Path> files = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                    .filter(this::supportedFile)
                    .forEach(files::add);
            for (Path file : files) {
                count += ingestFile(file, knowledgeDir);
            }
        } catch (IOException e) {
            log.error("Knowledge ingest failed", e);
        }
        log.info("Knowledge ingest completed, chunks={}", count);
        return count;
    }

    private boolean supportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".pdf");
    }

    private int ingestFile(Path file, Path rootDir) {
        String text;
        try {
            text = tika.parseToString(file);
        } catch (Exception e) {
            log.warn("Failed to parse file: {}", file, e);
            return 0;
        }
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            return indexChunks(file, rootDir, text);
        } catch (IOException e) {
            log.error("Failed to index file: {}", file, e);
            return 0;
        }
    }

    private int indexChunks(Path file, Path rootDir, String text) throws IOException {
        String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
        List<String> chunks = splitText(text);
        int count = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String chunkId = "kb_" + relativePath.replaceAll("[^a-zA-Z0-9]", "_") + "_" + i;
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", chunkId);
            doc.put("source_file", relativePath);
            doc.put("category", guessCategory(relativePath));
            doc.put("content", chunk);
            doc.put("updated_at", Instant.now().toString());
            doc.put("content_vector", embeddingService.embed(chunk));

            IndexRequest indexRequest = new IndexRequest(RagConstant.KNOWLEDGE_INDEX)
                    .id(chunkId)
                    .source(JSON.toJSONString(doc), XContentType.JSON);
            try {
                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            } catch (Exception e) {
                throw new IOException(e);
            }
            count++;
        }
        return count;
    }

    private List<String> splitText(String text) {
        int chunkSize = creatorRagProperties.getChunkSize();
        int overlap = creatorRagProperties.getChunkOverlap();
        List<String> chunks = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= chunkSize) {
            chunks.add(normalized);
            return chunks;
        }
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    private static String guessCategory(String path) {
        if (path.contains("运营") || path.contains("gemini")) {
            return "运营规范";
        }
        return "通用";
    }
}

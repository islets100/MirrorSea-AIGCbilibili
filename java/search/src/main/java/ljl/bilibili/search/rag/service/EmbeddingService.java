package ljl.bilibili.search.rag.service;

import java.util.List;

public interface EmbeddingService {
    float[] embed(String text);

    int dimensions();
}

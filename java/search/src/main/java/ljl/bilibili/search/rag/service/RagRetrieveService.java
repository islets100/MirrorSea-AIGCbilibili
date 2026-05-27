package ljl.bilibili.search.rag.service;

import ljl.bilibili.client.creator.RagRetrieveRequest;
import ljl.bilibili.client.creator.RagRetrieveResponse;

public interface RagRetrieveService {
    RagRetrieveResponse retrieve(RagRetrieveRequest request);
}

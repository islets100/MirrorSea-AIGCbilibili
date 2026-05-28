package ljl.bilibili.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import ljl.bilibili.search.config.properties.ElasticsearchClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch 8.x Java API Client（供关键词搜索与 LangChain4j RAG 共用）
 */
@Configuration
@Slf4j
public class ElasticsearchClientConfig {

    private final ElasticsearchClientProperties properties;

    public ElasticsearchClientConfig(ElasticsearchClientProperties properties) {
        this.properties = properties;
    }

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        List<String> hosts = properties.getHosts();
        if (hosts == null || hosts.isEmpty()) {
            throw new IllegalStateException("elasticsearch-client.hosts is empty");
        }
        HttpHost[] httpHosts = hosts.stream().map(HttpHost::create).toArray(HttpHost[]::new);
        return RestClient.builder(httpHosts)
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultIOReactorConfig(IOReactorConfig.custom()
                                .setIoThreadCount(8)
                                .build()))
                .setRequestConfigCallback(requestConfigBuilder ->
                        requestConfigBuilder.setConnectTimeout(300_000)
                                .setSocketTimeout(300_000))
                .build();
    }

    @ConditionalOnMissingBean(ElasticsearchClient.class)
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient elasticsearchRestClient) {
        ElasticsearchTransport transport = new RestClientTransport(
                elasticsearchRestClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(transport);
        log.info("Connecting Elasticsearch 8.x...");
        while (true) {
            try {
                if (client.ping().value()) {
                    break;
                }
            } catch (Exception e) {
                log.warn("Connecting Elasticsearch failed, retry in 10 seconds...", e);
                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while connecting Elasticsearch", ie);
                }
            }
        }
        log.info("Elasticsearch 8.x connected.");
        return client;
    }
}

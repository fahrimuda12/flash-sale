package com.fahri.order.config;

import co.elastic.clients.transport.TransportOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.support.HttpHeaders;

import java.time.Duration;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.fahri.order.repository")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private static final String ES_COMPATIBILITY_HEADER = "application/vnd.elasticsearch+json;compatible-with=8";

    @Value("${spring.elasticsearch.uris:localhost:9200}")
    private String elasticsearchUris;

    @Override
    public ClientConfiguration clientConfiguration() {
        HttpHeaders compatibilityHeaders = new HttpHeaders();
        compatibilityHeaders.add("Accept", ES_COMPATIBILITY_HEADER);
        compatibilityHeaders.add("Content-Type", ES_COMPATIBILITY_HEADER);

        String hostAndPort = elasticsearchUris
                .replace("http://", "")
                .replace("https://", "")
                .replaceAll("/+$", "");

        return ClientConfiguration.builder()
                .connectedTo(hostAndPort)
                .withConnectTimeout(Duration.ofSeconds(5))
                .withSocketTimeout(Duration.ofSeconds(5))
                .withHeaders(() -> compatibilityHeaders)
                .withDefaultHeaders(compatibilityHeaders)
                .build();
    }

    @Override
    public TransportOptions transportOptions() {
        return super.transportOptions().toBuilder()
                .setHeader("Accept", ES_COMPATIBILITY_HEADER)
                .setHeader("Content-Type", ES_COMPATIBILITY_HEADER)
                .build();
    }
}

package com.fahri.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.support.HttpHeaders;

import java.time.Duration;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.fahri.product.repository")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:localhost:9200}")
    private String elasticsearchUris;

    @Override
    public ClientConfiguration clientConfiguration() {
        HttpHeaders defaultHeaders = new HttpHeaders();
        defaultHeaders.add("Accept", "application/vnd.elasticsearch+json;compatible-with=8");
        defaultHeaders.add("Content-Type", "application/vnd.elasticsearch+json;compatible-with=8");

        String hostAndPort = elasticsearchUris
                .replace("http://", "")
                .replace("https://", "")
                .replaceAll("/+$", "");

        return ClientConfiguration.builder()
                .connectedTo(hostAndPort)
                .withConnectTimeout(Duration.ofSeconds(5))
                .withSocketTimeout(Duration.ofSeconds(5))
                .withDefaultHeaders(defaultHeaders)
                .build();
    }
}

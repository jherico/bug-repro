package com.remind.graph.test;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {
    @Value("${ELASTICSEARCH_HOST:localhost:9200}")
    private String elasticsearchHost;

    @Bean
    public RestHighLevelClient restClientBuilder() {
        return new RestHighLevelClient(RestClient.builder(HttpHost.create("http://" + elasticsearchHost)));
    }
}

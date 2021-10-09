package com.remind.graph.test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.javafaker.Faker;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.core.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class TestBulkProcessor implements ApplicationContextAware, CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TestBulkProcessor.class);
    private ApplicationContext context;
    @Value("${ELASTICSEARCH_INDEX:test_index}")
    private String indexName;

    @Autowired
    private RestHighLevelClient restClient;
    private ObjectMapper mapper = new ObjectMapper();
    private Faker faker = new Faker();

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TestBulkProcessor.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public void quit() {
        LOG.info("Quit");
        ((ConfigurableApplicationContext) context).close();
        System.exit(0);
    }

    public boolean exists() throws IOException {
        var getIndexRequest = new GetIndexRequest(indexName);
        return restClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
    }

    public void setup() throws IOException {
        if (!exists()) {
            var mapping = Resources.toString(Resources.getResource("mapping.json"), Charsets.UTF_8);
            var createIndexRequest = new CreateIndexRequest(indexName).source(mapping, XContentType.JSON);
            var createIndexResponse = restClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            LOG.info("Created Index for {} with mapping {}", createIndexResponse.index(), mapping);
        }
    }

    public BulkProcessor buildBulkProcessor() {
        var builder = BulkProcessor.builder(
                (request, bulkListener) -> restClient.bulkAsync(request, RequestOptions.DEFAULT, bulkListener),
                new TestListener(), "test");
        builder.setConcurrentRequests(0);
        builder.setBulkActions(10000);
        builder.setFlushInterval(TimeValue.timeValueMillis(100));
        builder.setBulkSize(ByteSizeValue.ofMb(5));
        return builder.build();
    }

    @SuppressWarnings({ "java:S2189", "java:S1181" })
    public void run(String... args) throws Exception {
        LOG.info("Started");
        setup();
        try (BulkProcessor bulkProcessor = buildBulkProcessor()) {
            long lastReportCount = 0;
            while (true) {
                var count = TestListener.counter.get();
                if (count - lastReportCount > 1000) {
                    lastReportCount = count;
                    LOG.info("{} documents indexed", count);
                }
                var document = generateDocument();
                var request = Requests.indexRequest().index(indexName).source(document, XContentType.JSON);
                bulkProcessor.add(request);
            }
        } catch (Throwable t) {
            LOG.error("Error", t);
            quit();
        }
        LOG.info("Done");
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Document {
        public UUID uuid = UUID.randomUUID();
        public String firstName;
        public String lastName;
        public String prefix;
        public List<String> search;
    }

    private String generateDocument() throws JsonProcessingException {
        var document = new Document();
        document.firstName = faker.name().firstName();
        document.lastName = faker.name().lastName();
        document.prefix = faker.name().prefix();
        document.search = faker.lorem().words(5);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
    }
}

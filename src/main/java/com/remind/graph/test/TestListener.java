package com.remind.graph.test;

import java.util.concurrent.atomic.AtomicLong;

import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestListener implements BulkProcessor.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(TestListener.class);

    public static AtomicLong counter = new AtomicLong(0);

    public void beforeBulk(long executionId, BulkRequest request) {
        LOG.debug("Executing {}", executionId);
    }

    public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
        LOG.debug("Executed {}", executionId);
        if (response.hasFailures()) {
            LOG.error("Failures {} " + response.buildFailureMessage());
        }
        counter.addAndGet(request.numberOfActions());
    }

    public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
        if (failure != null) {
            LOG.error("Bulk failure", failure);
        }
    }
}
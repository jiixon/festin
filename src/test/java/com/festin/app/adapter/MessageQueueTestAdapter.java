package com.festin.app.adapter;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MessageQueueTestAdapter {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    public boolean canConnect() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            String clusterId = clusterResult.clusterId().get(5, TimeUnit.SECONDS);
            return clusterId != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String getClusterId() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            return clusterResult.clusterId().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kafka cluster ID", e);
        }
    }
}

package com.orangecode.agent.config;

import com.orangecode.agent.client.MilvusClientFactory;
import io.milvus.client.MilvusServiceClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {
    @Autowired(required = false)
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    /**
     * 创建 MilvusServiceClient Bean
     *
     * @return MilvusServiceClient 实例
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        milvusClient = milvusClientFactory.createClient();
        return milvusClient;
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            milvusClient.close();
        }
    }
}

package com.orangecode.agent.client;

import com.orangecode.agent.config.MilvusProperties;
import com.orangecode.agent.constants.MilvusConstants;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Milvus 客户端工厂类
 * 负责创建和初始化 Milvus 客户端连接
 */
@Component
public class MilvusClientFactory {

    @Resource
    private MilvusProperties milvusProperties;

    /**
     * 创建并初始化 Milvus 客户端
     * 简化版本：直接连接并创建 collection
     *
     * @return MilvusServiceClient 实例
     * @throws RuntimeException 如果连接或初始化失败
     */
    public MilvusServiceClient createClient(){
        MilvusServiceClient client = null;

        try {
            // 1. 连接到 Milvus
            client = connectToMilvus();

            // 2. 检查并创建 biz collection
            if (!collectionExists(client, MilvusConstants.MILVUS_COLLECTION_NAME)) {
                createBizCollection(client);

                // 如果不存在，创建索引
                createIndexes(client);
            }
            return client;

        } catch (Exception e) {
            if (client != null) {
                client.close();
            }
            throw new RuntimeException("创建 Milvus 客户端失败: " + e.getMessage(), e);
        }
    }

    /**
     * 连接到 Milvus
     */
    private MilvusServiceClient connectToMilvus() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .withConnectTimeout(milvusProperties.getTimeout(), TimeUnit.MILLISECONDS);

        // 如果配置了用户名和密码
        if (milvusProperties.getUsername() != null && !milvusProperties.getUsername().isEmpty()) {
            builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
        }

        return new MilvusServiceClient(builder.build());
    }

    /**
     * 检查 collection 是否存在
     */
    private boolean collectionExists(MilvusServiceClient client, String collectionName) {
        R<Boolean> response = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("检查 collection 失败: " + response.getMessage());
        }

        return response.getData();
    }

    /**
     * 创建 biz collection
     */
    private void createBizCollection(MilvusServiceClient client) {
        // 定义字段
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.ID_MAX_LENGTH)
                .withPrimaryKey(true)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)  // 改为 FloatVector
                .withDimension(MilvusConstants.VECTOR_DIM)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                .build();

        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.JSON)
                .build();

        // 创建 collection schema
        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .addFieldType(idField)
                .addFieldType(vectorField)
                .addFieldType(contentField)
                .addFieldType(metadataField)
                .build();

        // 创建 collection
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withDescription("Business knowledge collection")
                .withSchema(schema)
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .build();

        R<RpcStatus> response = client.createCollection(createParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("创建 collection 失败: " + response.getMessage());
        }
    }

    /**
     * 为 collection 创建索引
     */
    private void createIndexes(MilvusServiceClient client) {
        // 为 vector 字段创建索引（FloatVector 使用 IVF_FLAT 和 L2 距离）
        CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)  // L2 距离（欧氏距离）
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.FALSE)
                .build();

        R<RpcStatus> response = client.createIndex(vectorIndexParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("创建 vector 索引失败: " + response.getMessage());
        }
    }
}

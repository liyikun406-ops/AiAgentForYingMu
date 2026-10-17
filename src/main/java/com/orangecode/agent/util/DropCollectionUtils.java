package com.orangecode.agent.util;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;

public class DropCollectionUtils {
    public static void main(String[] args){
        MilvusServiceClient client = null;

        try{
            // 连接到Milvus
            client = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost("localhost")
                            .withPort(19530)
                            .build()
            );

            String collectionName = "biz";

            // 检查 Collection 是否存在
            R<Boolean> hasResponse = client.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );

            if (hasResponse.getData()){
                // 删除 Collection
                R<RpcStatus> dropResponse = client.dropCollection(
                        DropCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build()
                );

                if (dropResponse.getStatus() == 0) {
                    System.out.println("✓ Collection 已成功删除");
                    System.out.println("\n请重启 Spring Boot 应用，它会自动创建新的 FloatVector Collection");
                } else {
                    System.err.println("✗ 删除失败: " + dropResponse.getMessage());
                }
            } else {
                System.out.println("Collection '" + collectionName + "' 不存在");
            }
        }catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

}

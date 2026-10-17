package com.orangecode.agent.service;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.orangecode.agent.constants.MilvusConstants;
import com.orangecode.agent.model.dto.DocumentChunk;
import com.orangecode.agent.response.IndexingResult;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VectorIndexService {

    private static final Logger logger = LoggerFactory.getLogger(VectorIndexService.class);

    @Resource
    private MilvusServiceClient milvusServiceClient;

    @Resource
    private DocumentChunkService documentChunkService;

    @Resource
    private EmbeddingService embeddingService;

    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 索引单个文件
     *
     * @param filePath 文件路径
     * @throws Exception 索引失败时抛出异常
     */
    public void indexSingleFile(String filePath){
        Path path = Paths.get(filePath).normalize();
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        // 删除旧索引数据（若存在）
        deleteExistingDAta(path.toString());
        try{
            // 读取文件内容并分片
            String content = Files.readString(path);
            List<DocumentChunk> chunks = documentChunkService.chunkDocument(content, path.toString());

            for(DocumentChunk chunk : chunks){
                // 生成向量
                List<Float> vector = embeddingService.generateEmbedding(chunk.getContent());

                // 构建元数据（包含文件信息）
                Map<String, Object> metadata = buildMetadata(path.toString(), chunk, chunks.size());

                // 插入到 Milvus
                insertToMilvus(chunk.getContent(), vector, metadata, chunk.getChunkIndex());

            }
        } catch (IOException | NoApiKeyException e){
            logger.error("✗ 分片索引失败", e);
            throw new RuntimeException("分片索引失败: " + e.getMessage(), e);
        }
    }

    /**
     * 索引指定目录下的所有文件
     *
     * @param directoryPath 目录路径（可选，默认使用配置的上传目录）
     * @return 索引结果  这里可以优化：定时重建目录下所有文件的索引
     */
    public IndexingResult indexDirectory(String directoryPath){
        IndexingResult result = new IndexingResult();
        result.setStartTime(LocalDateTime.now());

        try{
            // 使用指定目录或默认上传目录
            String targetPath = (directoryPath != null && !directoryPath.trim().isEmpty())
                    ? directoryPath : uploadPath;

            Path dirPath = Paths.get(targetPath).normalize();
            File directory = dirPath.toFile();

            if (!directory.exists() || !directory.isDirectory()) {
                throw new IllegalArgumentException("目录不存在或不是有效目录: " + targetPath);
            }

            result.setDirectoryPath(directory.getAbsolutePath());

            // 获取所有支持的文件
            File[] files = directory.listFiles((dir, name) ->
                    name.endsWith(".txt") || name.endsWith(".md")
            );

            if (files == null || files.length == 0) {
                logger.warn("目录中没有找到支持的文件: {}", targetPath);
                result.setTotalFiles(0);
                result.setSuccess(true);
                result.setEndTime(LocalDateTime.now());
                return result;
            }

            result.setTotalFiles(files.length);
            logger.info("开始索引目录: {}, 找到 {} 个文件", targetPath, files.length);

            // 遍历并索引每个文件
            for (File file : files) {
                try {
                    indexSingleFile(file.getAbsolutePath());
                    result.incrementSuccessCount();
                    logger.info("✓ 文件索引成功: {}", file.getName());
                } catch (Exception e) {
                    result.incrementFailCount();
                    result.addFailedFile(file.getAbsolutePath(), e.getMessage());
                    logger.error("✗ 文件索引失败: {}", file.getName(), e);
                }
            }

            result.setSuccess(result.getFailCount() == 0);
            result.setEndTime(LocalDateTime.now());

            logger.info("目录索引完成: 总数={}, 成功={}, 失败={}",
                    result.getTotalFiles(), result.getSuccessCount(), result.getFailCount());

            return result;
        } catch (Exception e) {
            logger.error("索引目录失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            return result;
        }
    }

    private Map<String, Object> buildMetadata(String filePath, DocumentChunk chunk, int totalChunks){
        Map<String, Object> metadata = new HashMap<>();

        // 标准化路径：使用统一的路径分隔符（正斜杠）用于存储，确保跨平台一致性
        Path path = Paths.get(filePath).normalize();
        String normalizedPath = path.toString().replace(File.separator, "/");

        // 文件信息
        Path fileName = path.getFileName();
        String fileNameStr = fileName != null ? fileName.toString() : "";
        String extension = "";
        int dotIndex = fileNameStr.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileNameStr.substring(dotIndex);
        }

        metadata.put("_source", normalizedPath);
        metadata.put("_extension", extension);
        metadata.put("_file_name", fileNameStr);

        // 分片信息
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("totalChunks", totalChunks);

        // 标题信息
        if (chunk.getTitle() != null && !chunk.getTitle().isEmpty()) {
            metadata.put("title", chunk.getTitle());
        }

        return metadata;
    }

    private void insertToMilvus(String content, List<Float> vector, Map<String, Object> metadata, int chunkIndex){
        try{
            // 验证collection是否存在
            R<RpcStatus> loadResponse = milvusServiceClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );

            if(loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535){
                throw new RuntimeException("加载collection失败：" + loadResponse.getMessage());
            }

            // 生成唯一ID
            String source = (String) metadata.get("_source");
            String id = UUID.nameUUIDFromBytes((source+"_"+chunkIndex).getBytes()).toString();

            // 构建字段数据
            List<InsertParam.Field> fields = new ArrayList<>();

            // ID 字段
            fields.add(new InsertParam.Field("id", Collections.singletonList(id)));

            // content 字段
            fields.add(new InsertParam.Field("content", Collections.singletonList(content)));

            // vector 字段
            fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));

            // metadata 字段（JSON 对象）
            Gson gson = new Gson();
            JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();
            fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

            // 构造插入参数
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withFields(fields)
                    .build();

            // 执行插入
            R<MutationResult> insertResponse = milvusServiceClient.insert(insertParam);

            if (insertResponse.getStatus() != 0) {
                throw new RuntimeException("插入向量失败: " + insertResponse.getMessage());
            }

            logger.debug("向量插入成功: id={}, source={}, chunk={}", id, source, chunkIndex);

        } catch (Exception e) {
            logger.error("插入向量到 Milvus 失败", e);
            throw e;
        }
    }

    private void deleteExistingDAta(String filePath){
        try{
            // 将系统路径转换为统一格式
            Path path = Paths.get(filePath).normalize();
            String normalizedPath = path.toString().replace(File.separator, "/");

            // 构造删除表达式
            String expr = String.format("metadata[\"_source\"] == \"%s\"", normalizedPath);

            // 验证collection是否存在
            R<RpcStatus> loadResponse = milvusServiceClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );

            if(loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535){
                throw new RuntimeException("加载collection失败：" + loadResponse.getMessage());
            }

            // 构造请求参数
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            // 执行删除操作
            R<MutationResult> response = milvusServiceClient.delete(deleteParam);

            if (response.getStatus() != 0) {
                logger.warn("删除旧数据时出现警告: {}", response.getMessage());
            } else {
                long deletedCount = response.getData().getDeleteCnt();
                logger.info("✓ 已删除文件的旧数据: {}, 删除记录数: {}", normalizedPath, deletedCount);
            }
        } catch (Exception e) {
            logger.warn("删除旧数据失败（可能是首次索引）: {}", e.getMessage());
        }
    }

}

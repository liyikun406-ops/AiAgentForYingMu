package com.orangecode.agent.service;

import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.retry.embedding.options.model}")
    private String model;

    private TextEmbedding textEmbedding;

    @PostConstruct
    public void init() {
        // 验证API-KEY
        if(apiKey == null || apiKey.isEmpty()){
            throw new IllegalStateException("请设置环境变量 ALI_API_KEY 或在 application.yml 中配置正确的 API Key");
        }

        // 设置全局API-KEY
        Constants.apiKey = apiKey;

//        if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
//            throw new IllegalStateException("API Key 设置到 Constants 失败");
//        }

        textEmbedding = new TextEmbedding();
        logger.info("阿里云 DashScope Embedding 服务初始化完成，模型: {}", model);
    }

    public List<Float> generateEmbedding(String content) throws NoApiKeyException {
        try{
            if(content == null || content.isEmpty()){
                throw new IllegalArgumentException("内容不能为空");
            }

            if(Constants.apiKey == null || Constants.apiKey.isEmpty()){
                Constants.apiKey = apiKey;
            }

            // 构建请求参数
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .model(model)
                    .texts(Collections.singletonList(content))
                    .build();

            // 调用模型
            TextEmbeddingResult result = textEmbedding.call(param);

            List<Float> floatEmbedding = getFloats(result);

            logger.info("成功生成向量嵌入, 内容长度: {} 字符, 向量维度: {}",
                    content.length(), floatEmbedding.size());

            return floatEmbedding;
        } catch (NoApiKeyException e){
            logger.error("API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            logger.error("生成向量嵌入失败, 内容长度: {}", content != null ? content.length() : 0, e);
            throw new RuntimeException("生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    private List<Float> getFloats(TextEmbeddingResult result){
        if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
            throw new RuntimeException("DashScope API 返回空结果");
        }

        TextEmbeddingOutput output = result.getOutput();
        List<TextEmbeddingResultItem> embeddings = output.getEmbeddings();

        if (embeddings.isEmpty()) {
            throw new RuntimeException("DashScope API 返回空向量列表");
        }

        // 获取第一个文本的向量
        List<Double> embeddingDoubles = embeddings.get(0).getEmbedding();

        // 转换为 List<Float>
        List<Float> floatEmbedding = new ArrayList<>(embeddingDoubles.size());
        for (Double value : embeddingDoubles) {
            floatEmbedding.add(value.floatValue());
        }
        return floatEmbedding;
    }

}

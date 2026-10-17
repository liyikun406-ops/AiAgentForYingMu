package com.orangecode.agent.service;

import com.orangecode.agent.config.DocumentChunkConfig;
import com.orangecode.agent.model.dto.DocumentChunk;
import com.orangecode.agent.model.entity.Section;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentChunkService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkService.class);
    @Resource
    private DocumentChunkConfig documentChunkConfig;

    /**
     * 智能分片文档
     * 优先按照标题、段落边界进行分割，保持语义完整性
     *
     * @param content 文档内容
     * @Param filePath 文件路径（日志用）
     * @return 文档分片列表
     */
    public List<DocumentChunk> chunkDocument(String content, String filePath){
        List<DocumentChunk> chunks = new ArrayList<>();

//        if(content == null || chunks.isEmpty()){
//            logger.error("文档内容为空");
//            return chunks;
//        }

        // 首先尝试按照标题分片
        List<Section> sections = splitByHeadings(content);

        // 对每个章节进一步分片
        int globalChunkIndex = 0;
        for(Section section : sections){
            List<DocumentChunk> sectionChunks = chunkSection(section, globalChunkIndex);
            chunks.addAll(sectionChunks);
            globalChunkIndex += sectionChunks.size();
        }
        logger.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());
        return chunks;
    }

    // 根据标题分章节
    private List<Section> splitByHeadings(String content){
        List<Section> sections = new ArrayList<>();

        // 匹配标题（Markdown #标题 ##标题）
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);

        int lastEnd = 0;
        String currentTitle = null;

        // 保存每个标题章节
        while(matcher.find()){
            if(lastEnd < matcher.start()){
                String sectiondContent = content.substring(lastEnd, matcher.start());

                if(!sectiondContent.isEmpty()){
                    sections.add(new Section(currentTitle, sectiondContent,lastEnd));
                }
            }

            lastEnd = matcher.start();
            currentTitle = matcher.group(2).trim();
        }

        // 添加最后一个章节
        if(lastEnd < content.length()){
            String sectionContent = content.substring(lastEnd);
            if(!sectionContent.isEmpty()){
                sections.add(new Section(currentTitle, sectionContent, lastEnd));
            }
        }

        // 如果没有找到任何章节，将整个文章作为一个章节
        if(sections.isEmpty()) {
            sections.add(new Section(null, content, 0));
        }

        return sections;
    }

    // 对每一个章节进行细分
    private List<DocumentChunk> chunkSection(Section section, int globalChunkIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();

        String content = section.getContent();
        String title = section.getTitle();

        if (content.length() <= documentChunkConfig.getMaxSize()) {
            DocumentChunk chunk = new DocumentChunk(
                    content,
                    section.getStartINdex(),
                    section.getStartINdex() + content.length(),
                    globalChunkIndex,
                    title
            );
            chunks.add(chunk);
            return chunks;
        }

        // 章节内容太长要进一步分片
        // 优先按段落分割
        List<String> paragraphs = new ArrayList<>();

        String[] parts = content.split("\n\n+");
        for (String part : parts) {
            String trimed = part.trim();
            if (!trimed.isEmpty()) {
                paragraphs.add(trimed);
            }
        }

        StringBuilder currentChunk = new StringBuilder();
        int currentStartIndex = section.getStartINdex();
        int chunkIndex = globalChunkIndex;

        for (String paragraph : paragraphs) {
            // 当前缓冲区部分加上新段落超出最大值，预先保存缓冲区。
            if (currentChunk.length() > 0
                    && currentChunk.length() + paragraph.length() > documentChunkConfig.getMaxSize()) {

                String chunkContent = currentChunk.toString();
                DocumentChunk chunk = new DocumentChunk(
                        chunkContent,
                        currentStartIndex,
                        currentStartIndex + chunkContent.length(),
                        chunkIndex ++,
                        title
                );
                chunks.add(chunk);

                // 开始新分片，保证重叠部分
                String overlap = getOverlap(chunkContent);
                currentChunk = new StringBuilder(overlap);
                currentStartIndex = currentStartIndex + chunkContent.length() - overlap.length();
            }

            // 将当前段落加入缓冲区
            currentChunk.append(paragraph).append("\n\n");

            // 当前段落加入后直接超过最大值，单段落超限，直接将该段加入，不在分割。
            if(currentChunk.length() + paragraph.length() > documentChunkConfig.getMaxSize()) {
                String currentcontent = currentChunk.toString();
                DocumentChunk chunk = new DocumentChunk(
                        currentcontent,
                        currentStartIndex,
                        currentStartIndex + currentcontent.length(),
                        chunkIndex ++,
                        title
                );
                chunks.add(chunk);
                String overlap = getOverlap(currentcontent);
                currentChunk = new StringBuilder(overlap);
                currentStartIndex = currentStartIndex + currentcontent.length() - overlap.length();
            }
        }

        // 保存最后一个分片
        if (currentChunk.length() > 0) {
            String chunkContent = currentChunk.toString().trim();
            DocumentChunk chunk = new DocumentChunk(
                    chunkContent,
                    currentStartIndex,
                    currentStartIndex + chunkContent.length(),
                    chunkIndex,
                    title
            );
            chunks.add(chunk);
        }

        return chunks;
    }

    private String getOverlap(String chunkContent){
        int overlapSize = Math.min(documentChunkConfig.getOverlap(), chunkContent.length());
        if (overlapSize <= 0) {
            return "";
        }

        // 从末尾提取重叠内容
        String overlap = chunkContent.substring(chunkContent.length() - overlapSize);

        // 尝试在句子边界截断（查找最后一个句号、问号、感叹号）
        int lastSentenceEnd = Math.max(
                overlap.lastIndexOf('。'),
                Math.max(overlap.lastIndexOf('？'), overlap.lastIndexOf('！'))
        );

        if (lastSentenceEnd > overlapSize / 2) {
            return overlap.substring(lastSentenceEnd + 1).trim();
        }

        return overlap.trim();
    }

}

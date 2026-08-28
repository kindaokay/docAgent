package org.javaup.ai.split;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 自定义分片器
 * @author: 阿星不是程序员
 **/
/**
 * 自定义分片器：支持 chunkSize、overlap，并按段落拆分
 * 基于Spring AI的TextSplitter抽象类实现
 */
public class OverlapParagraphTextSplit extends TextSplitter {
    
    // 每块最大字符数
    protected final int chunkSize;
    // 相邻块之间重叠字符数
    protected final int overlap;
    

    public OverlapParagraphTextSplit(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new RuntimeException("chunkSize 必须大于 0");
        }
        if (overlap < 0) {
            throw new RuntimeException("overlap 不能为负数");
        }
        if (overlap >= chunkSize) {
            throw new RuntimeException("overlap 不能大于等于 chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }
    
    @Override
    public List<String> splitText(String text) {
        if (StrUtil.isEmpty(text)) {
            return Collections.emptyList();
        }
        String[] paragraphs = text.split("\\n+");
        List<String> allChunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            if (StrUtil.isEmpty(paragraph)) {
                continue;
            }
            int start = 0;
            while (start < paragraph.length()) {
                int remainingSpace = chunkSize - currentChunk.length();
                int end = Math.min(start + remainingSpace, paragraph.length());
                
                if (!currentChunk.isEmpty()) {
                    currentChunk.append("\n");
                }
                currentChunk.append(paragraph, start, end);
                
                // 如果当前块已满，保存并生成新块
                if (currentChunk.length() >= chunkSize) {
                    allChunks.add(currentChunk.toString());
                    
                    // 计算重叠部分
                    String overlapText = "";
                    if (overlap > 0) {
                        int overlapStart = Math.max(0, currentChunk.length() - overlap);
                        overlapText = currentChunk.substring(overlapStart);
                    }
                    
                    currentChunk = new StringBuilder();
                    if (!overlapText.isEmpty()) {
                        currentChunk.append(overlapText);
                    }
                }
                start = end;
            }
        }
        
        if (!currentChunk.isEmpty()) {
            allChunks.add(currentChunk.toString());
        }
        
        return allChunks;
    }
    
    @Override
    public List<Document> apply(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }
        
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            List<String> chunks = splitText(doc.getText());
            for (String chunk : chunks) {
                result.add(new Document(chunk));
            }
        }
        return result;
    }
}
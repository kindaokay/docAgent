package org.javaup.ai.handler;

import org.springframework.ai.document.Document;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 处理器
 * @author: 阿星不是程序员
 **/
public interface ReaderHandler {
    /**
     * 是否可以处理该文件
     */
    boolean canHandle(File file);

    /**
     * 读取文件并返回Document列表
     */
    List<Document> readhandle(File file) throws IOException;
}
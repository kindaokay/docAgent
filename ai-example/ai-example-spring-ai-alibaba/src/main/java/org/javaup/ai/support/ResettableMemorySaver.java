package org.javaup.ai.support;

import java.util.Collection;

import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 支撑组件
 * @author: 阿星不是程序员
 **/
public class ResettableMemorySaver extends MemorySaver {

    public int clearThread(String threadId) {
        Collection<Checkpoint> removed = remove(threadId);
        return removed != null ? removed.size() : 0;
    }

}

package org.javaup.route.model;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 对外返回的完整路由响应
 * @author: 阿星不是程序员
 **/
/**
 * 对外返回的完整路由响应。
 */
public class RouteChatResponse {

    private final String sessionId;
    private final String question;
    private final RouteIntent intent;
    private final String routeName;
    private final String answer;

    public RouteChatResponse(String sessionId,
                             String question,
                             RouteIntent intent,
                             String routeName,
                             String answer) {
        this.sessionId = sessionId;
        this.question = question;
        this.intent = intent;
        this.routeName = routeName;
        this.answer = answer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public RouteIntent getIntent() {
        return intent;
    }

    public String getRouteName() {
        return routeName;
    }

    public String getAnswer() {
        return answer;
    }
}

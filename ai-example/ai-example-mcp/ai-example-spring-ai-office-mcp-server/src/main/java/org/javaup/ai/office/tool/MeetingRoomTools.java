package org.javaup.ai.office.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 工具类
 * @author: 阿星不是程序员
 **/
@Service
public class MeetingRoomTools {

    /**
     * 查询会议室排期
     */
    @Tool(description = "查询指定会议室在某天的预订情况和空闲时段。" +
                        "当用户想知道会议室是否有空、什么时候可以用时，使用此工具。")
    public String queryRoomSchedule(
            @ToolParam(description = "会议室编号，如A301、B502") String roomId,
            @ToolParam(description = "查询日期，格式YYYY-MM-DD") String date) {
        
        // 模拟返回会议室排期
        return String.format("""
            {
                "roomId": "%s",
                "roomName": "第%s会议室",
                "date": "%s",
                "capacity": 10,
                "bookedSlots": [
                    {"start": "09:00", "end": "10:30", "subject": "产品需求评审", "organizer": "张经理"},
                    {"start": "14:00", "end": "15:00", "subject": "技术方案讨论", "organizer": "李工"}
                ],
                "availableSlots": [
                    {"start": "10:30", "end": "12:00"},
                    {"start": "13:00", "end": "14:00"},
                    {"start": "15:00", "end": "18:00"}
                ]
            }
            """, roomId, roomId, date);
    }

    /**
     * 预订会议室
     */
    @Tool(description = "预订会议室。当用户说要订会议室、约会议室时使用此工具。")
    public String bookMeetingRoom(
            @ToolParam(description = "会议室编号") String roomId,
            @ToolParam(description = "预订日期，格式YYYY-MM-DD") String date,
            @ToolParam(description = "开始时间，格式HH:mm") String startTime,
            @ToolParam(description = "结束时间，格式HH:mm") String endTime,
            @ToolParam(description = "会议主题") String subject,
            @ToolParam(description = "预订人姓名") String organizer) {
        
        // 模拟预订成功
        String bookingId = "BK" + System.currentTimeMillis();
        
        return String.format("""
            {
                "success": true,
                "bookingId": "%s",
                "roomId": "%s",
                "date": "%s",
                "timeSlot": "%s - %s",
                "subject": "%s",
                "organizer": "%s",
                "message": "会议室预订成功"
            }
            """, bookingId, roomId, date, startTime, endTime, subject, organizer);
    }
    
    /**
     * 取消会议室预订
     */
    @Tool(description = "取消已预订的会议室。当用户说要取消会议、退订会议室时使用此工具。")
    public String cancelBooking(
            @ToolParam(description = "预订单号") String bookingId) {
        
        return String.format("""
            {
                "success": true,
                "bookingId": "%s",
                "message": "预订已取消"
            }
            """, bookingId);
    }
}
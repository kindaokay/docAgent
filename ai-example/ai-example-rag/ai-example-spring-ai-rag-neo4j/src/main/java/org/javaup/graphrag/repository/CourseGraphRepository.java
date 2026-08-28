package org.javaup.graphrag.repository;

import org.javaup.graphrag.dto.InstructorCoursesDto;
import org.javaup.graphrag.entity.Course;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 数据访问层
 * @author: 阿星不是程序员
 **/
public interface CourseGraphRepository extends Neo4jRepository<Course, String> {

    /**
     * 多跳查询：用CONTAINS模糊匹配课程名，找到它的讲师教授的其他课程
     */
    @Query("""
        MATCH (c:Course) WHERE c.courseName CONTAINS $courseName
        WITH c
        MATCH (c) <-[:TEACHES]- (i:Instructor) -[:TEACHES]-> (other:Course)
        WHERE other.courseName <> c.courseName
        RETURN i.name AS instructor, collect(other.courseName) AS otherCourses
        """)
    List<InstructorCoursesDto> findOtherCoursesByInstructors(@Param("courseName") String courseName);

    /**
     * 带方向过滤的多跳查询
     */
    @Query("""
        MATCH (c:Course) WHERE c.courseName CONTAINS $courseName
        WITH c
        MATCH (c) <-[:TEACHES]- (i:Instructor) -[:TEACHES]-> (other:Course)
        WHERE other.courseName <> c.courseName AND other.category = $category
        RETURN i.name AS instructor, collect(other.courseName) AS otherCourses
        """)
    List<InstructorCoursesDto> findOtherCoursesByCategory(@Param("courseName") String courseName, @Param("category") String category);
}

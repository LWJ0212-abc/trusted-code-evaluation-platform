package lwj.code.server.identity.domain;

/**
 * 课程角色
 */
public enum CourseRole {
    STUDENT, //学生，可以提交代码、查看自己的评测结果。
    TEACHER, //教师，可以管理课程、作业和查看课程成绩。
    TA  //助教，通常可以协助查看提交、评测结果或处理课程事务，但权限通常少于教师。
}

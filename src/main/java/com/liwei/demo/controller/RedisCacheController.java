package com.liwei.demo.controller;

import com.liwei.demo.entity.Student;
import com.liwei.demo.service.impl.RedisCacheServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName RedisCacheController
 * @Description: TODO
 * @Author LiWei
 * @Date 2020/5/25
 **/
@RestController
public class RedisCacheController {

    @Autowired
    RedisCacheServiceImpl redisCacheService;


    /*
     * @MethodName: getStudentListLocal
     * @Description: 查询数据库中的所有学生
     * @Param: []
     * @Return: java.util.List<com.liwei.demo.entity.Student>
     * @Author: LiWei
     * @Date: 2020/5/25
    **/
    @RequestMapping("/getStudentList")
    public List<Student> getStudentList(){
        List<Student> studentList = redisCacheService.getStudentList();
        return studentList;
    }
}

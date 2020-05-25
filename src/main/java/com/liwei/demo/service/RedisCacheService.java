package com.liwei.demo.service;

import com.liwei.demo.entity.Student;

import java.util.List;

/**
 * @ClassName RedisCacheService
 * @Description: TODO
 * @Author LiWei
 * @Date 2020/5/25
 **/
public interface RedisCacheService {

    List<Student> getStudentList();
}

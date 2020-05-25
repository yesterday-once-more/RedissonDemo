package com.liwei.demo.entity;

/**
 * @ClassName Student
 * @Description: TODO
 * @Author LiWei
 * @Date 2020/5/25
 **/
public class Student {
    private String name;
    private Integer age;
    private String sex;

    public Student() {
    }

    public Student(String name, Integer age, String sex) {
        this.name = name;
        this.age = age;
        this.sex = sex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
}

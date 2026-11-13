package com.ghostchu.btn.sparkle.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface CareerIngestionMapper {
    int feature(@Param("p") Map<String,Object> p);
    int init(@Param("p") Map<String,Object> p);
    Map<String,Long> checkpoint(@Param("p") Map<String,Object> p);
    int advance(@Param("p") Map<String,Object> p);
    int event(@Param("p") Map<String,Object> p);
    int observations(@Param("p") Map<String,Object> p);
    int activity(@Param("p") Map<String,Object> p);
    int traffic(@Param("p") Map<String,Object> p);
}
package com.campusexpress.exception;

import com.campusexpress.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLIntegrityConstraintViolationException;

@ControllerAdvice(annotations = {RestController.class, Controller.class})
@ResponseBody
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result<String> exceptionHandler(SQLIntegrityConstraintViolationException exception){
        String message = exception.getMessage();
        if(message.contains("Duplicate entry")){
            String[] split = message.split(" ");
            String msg = split[2] + "已存在";
            return Result.error(msg);
        }else{
            return Result.error("未知错误");
        }
    }

}

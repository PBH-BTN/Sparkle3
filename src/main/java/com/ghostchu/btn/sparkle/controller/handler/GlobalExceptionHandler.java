package com.ghostchu.btn.sparkle.controller.handler;

import java.nio.charset.MalformedInputException;

import io.sentry.Sentry;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserBannedException;
import com.ghostchu.btn.sparkle.wrapper.StdResp;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<@NotNull StdResp<String>> businessExceptionHandler(BusinessException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new StdResp<>(false, e.getMessage(), null));
    }

    @ExceptionHandler(UserBannedException.class)
    public ResponseEntity<@NotNull StdResp<String>> userBannedException(UserBannedException e) {
        return ResponseEntity.status(403).body(new StdResp<>(false, "此用户已被管理员停用，请与系统管理员联系以获取更多信息。", null));
    }

    @ExceptionHandler(UserApplicationBannedException.class)
    public ResponseEntity<@NotNull StdResp<String>> userApplicationBannedException(UserApplicationBannedException e) {
        return ResponseEntity.status(403).body(new StdResp<>(false, "此用户应用程序已被管理员停用：" + e.getMessage() + "。请与系统管理员联系以获取更多信息。", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<@NotNull StdResp<String>> noResourceFoundException(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new StdResp<>(false, "404 Not Found - 资源未找到", null));
    }

//    @ExceptionHandler(ClientAbortException.class)
//    public void clientAbort(Exception e) {
//        log.warn("Client abort a connection: {}", e.getMessage());
//    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<@NotNull StdResp<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new StdResp<>(false, "不允许的请求方式: " + e.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<@NotNull StdResp<Void>> httpMessageNotReadable(HttpMessageNotReadableException e) {
        int loop = 0;
        Throwable exception = e;
        while (exception.getCause() != null) {
            loop++;
            if (loop > 30) break;
            exception = exception.getCause();
            if (exception instanceof ClientAbortException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new StdResp<>(false, "客户端已放弃请求: " + e.getMessage(), null));
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new StdResp<>(false, "不可读的 HTTP 消息: " + e.getMessage(), null));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncReqNotUsable(AsyncRequestNotUsableException e) {
        //log.warn("Unable to complete async request because: [{}], async request not usable.", e.getMessage());
        // not my issue
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<@NotNull StdResp<String>> invalidParameterHandler(InvalidParameterException e, HttpServletRequest request) {
        String queryString = request.getQueryString();
        boolean isTrackerRequest = queryString != null && queryString.contains("info_hash");
        
        if (isTrackerRequest) {
            log.debug("BitTorrent tracker request detected with invalid parameter: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new StdResp<>(false, "d14:failure reason82:This is not a BitTorrent tracker. Invalid parameter encoding detected.e", null));
        }
        log.warn("Invalid parameter received: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new StdResp<>(false, "无效的请求参数: " + e.getMessage(), null));
    }

    @ExceptionHandler(MalformedInputException.class)
    public ResponseEntity<@NotNull StdResp<String>> malformedInputHandler(MalformedInputException e, HttpServletRequest request) {
        String queryString = request.getQueryString();
        boolean isTrackerRequest = queryString != null && queryString.contains("info_hash");
        if (isTrackerRequest) {
            log.debug("BitTorrent tracker request detected with malformed input: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new StdResp<>(false, "d14:failure reason70:This is not a BitTorrent tracker. Malformed input detected.e", null));
        }
        
        log.warn("Malformed input received: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new StdResp<>(false, "请求包含格式错误的数据: " + e.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NotNull StdResp<String>> jvmExceptionHandler(Exception e, HttpServletRequest request) {
        // Check if it's a parameter encoding issue deep in the stack
        var sentryId = Sentry.captureException(e);
        Throwable cause = e;
        int depth = 0;
        while (cause != null && depth < 20) {
            if (cause instanceof InvalidParameterException) {
                return invalidParameterHandler((InvalidParameterException) cause, request);
            }
            if (cause instanceof MalformedInputException) {
                return malformedInputHandler((MalformedInputException) cause, request);
            }
            cause = cause.getCause();
            depth++;
        }

        log.error("Unexpected exception. Tracing Id: {}", sentryId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new StdResp<>(false, "很抱歉，由于服务器内部错误，您的请求未能完成。此错误我们已收悉，错误跟踪ID：" + sentryId, null));

    }
}

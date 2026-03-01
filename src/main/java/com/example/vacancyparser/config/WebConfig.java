package com.example.vacancyparser.config;

import com.example.vacancyparser.service.RequestLogDaemon;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RequestLogDaemon requestLogDaemon;

    public WebConfig(RequestLogDaemon requestLogDaemon) {
        this.requestLogDaemon = requestLogDaemon;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoggingInterceptor(requestLogDaemon))
                .addPathPatterns("/api/**", "/", "/health");
    }

    /**
     * Перехватчик для логирования HTTP запросов
     */
    private static class LoggingInterceptor implements HandlerInterceptor {

        private final RequestLogDaemon requestLogDaemon;
        private final ThreadLocal<Long> startTime = new ThreadLocal<>();

        public LoggingInterceptor(RequestLogDaemon requestLogDaemon) {
            this.requestLogDaemon = requestLogDaemon;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            startTime.set(System.currentTimeMillis());
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            long duration = System.currentTimeMillis() - startTime.get();
            String method = request.getMethod();
            String path = request.getRequestURI();
            int status = response.getStatus();

            // Отправляем лог в демон-поток
            requestLogDaemon.logRequest(method, path, status, duration);

            startTime.remove();
        }
    }
}
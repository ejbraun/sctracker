package com.howl.uwtracker.web;

import com.howl.uwtracker.auth.AdminAuthInterceptor;
import com.howl.uwtracker.auth.CurrentPersonIdArgumentResolver;
import com.howl.uwtracker.auth.SessionAuthInterceptor;
import com.howl.uwtracker.repository.AdminRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminRepository adminRepository;

    public WebMvcConfig(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionAuthInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/signup", "/api/login");
        // Registered after SessionAuthInterceptor (interceptors run in registration order), so an
        // unauthenticated request is already rejected with 401 before this ever runs.
        registry.addInterceptor(new AdminAuthInterceptor(adminRepository))
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentPersonIdArgumentResolver());
    }

    // Explicit url-patterns (not a bare @Component filter, which defaults to "/*") so this only ever
    // sees the API surface — /api/**, plus the top-level plugin-facing endpoints that predate the
    // /api prefix convention — and never SpaFallbackController's HTML forwards or static assets.
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.addUrlPatterns("/api/*", "/upload-run", "/report-run-failure", "/can-report-run-failure",
                "/report-run-mvp", "/plugin-version", "/SCTracker.dll", "/artifacts", "/module-entitlements",
                "/modules/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

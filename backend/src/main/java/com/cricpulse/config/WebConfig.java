package com.cricpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cricpulse.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS");
    }

    /**
     * ETags on API responses: a polling client whose data has not changed gets a 304 with no body,
     * which saves bandwidth when many clients poll the same match.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> bean = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        bean.addUrlPatterns("/api/*");
        return bean;
    }

    @Bean(name = "simulationWorkers")
    public TaskExecutor simulationWorkers(SimulationProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getWorkerThreads());
        executor.setMaxPoolSize(props.getWorkerThreads());
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("sim-");
        executor.initialize();
        return executor;
    }

    @Bean
    public OpenAPI cricPulseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CricPulse API")
                .version("1.0")
                .description("Real-time cricket scoring: matches, ball-by-ball updates, scorecards and player statistics. "
                        + "Live updates are also pushed over STOMP/WebSocket at /ws (topics /topic/matches and /topic/matches/{id})."));
    }
}

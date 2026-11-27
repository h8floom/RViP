package com.example.demo;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Profile("docker")
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @Profile("!docker")
    public RestTemplate localRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean(name = "reportDownloadRestTemplate", defaultCandidate = false)
    @Profile("docker")
    @LoadBalanced
    public RestTemplate dockerReportDownloadRestTemplate(RestTemplateBuilder builder) {
        return buildDownloadClient(builder);
    }

    @Bean(name = "reportDownloadRestTemplate", defaultCandidate = false)
    @Profile("!docker")
    public RestTemplate localReportDownloadRestTemplate(RestTemplateBuilder builder) {
        return buildDownloadClient(builder);
    }

    private RestTemplate buildDownloadClient(RestTemplateBuilder builder) {
        return builder.connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}

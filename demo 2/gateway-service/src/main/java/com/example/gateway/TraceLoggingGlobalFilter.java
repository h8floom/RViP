package com.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Logs the entry and completion of requests passing through Gateway. */
@Component
public class TraceLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceLoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        String path = exchange.getRequest().getURI().getPath();

        return Mono.defer(() -> {
            log.info("Gateway received request method={} path={} traceId={} spanId={}",
                    method, path, MDC.get("traceId"), MDC.get("spanId"));

            return chain.filter(exchange)
                    .doOnSuccess(ignored -> {
                        HttpStatusCode status = exchange.getResponse().getStatusCode();
                        log.info("Gateway completed request method={} path={} status={} traceId={} spanId={}",
                                method, path,
                                status != null ? status.value() : "unknown",
                                MDC.get("traceId"), MDC.get("spanId"));
                    })
                    .doOnError(error -> log.error(
                            "Gateway request failed method={} path={} traceId={} spanId={}",
                            method, path, MDC.get("traceId"), MDC.get("spanId"), error));
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

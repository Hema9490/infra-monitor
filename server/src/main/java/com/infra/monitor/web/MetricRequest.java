package com.infra.monitor.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload posted by the agent. */
public record MetricRequest(
        @NotBlank @Size(max = 100) String host,
        @DecimalMin("0.0") @DecimalMax("100.0") double cpuPercent,
        @DecimalMin("0.0") @DecimalMax("100.0") double memoryPercent,
        @DecimalMin("0.0") @DecimalMax("100.0") double diskPercent,
        @Min(0) long uptimeSeconds) {}

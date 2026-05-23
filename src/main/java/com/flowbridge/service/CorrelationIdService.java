package com.flowbridge.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CorrelationIdService {

    public String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}

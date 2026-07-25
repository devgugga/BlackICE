package dev.blackice.api;

import java.util.List;

public record SessionResponse(String subject, String username, List<String> roles) {}

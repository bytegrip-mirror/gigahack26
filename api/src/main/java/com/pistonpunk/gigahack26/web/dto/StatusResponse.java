package com.pistonpunk.gigahack26.web.dto;

import java.time.Instant;

public record StatusResponse(String service, String postgres, String mongo, Instant checkedAt) {
}

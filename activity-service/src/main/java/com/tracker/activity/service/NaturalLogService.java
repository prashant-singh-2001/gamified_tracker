package com.tracker.activity.service;

import com.tracker.activity.dto.NaturalLogDraftResponse;
import org.springframework.http.ResponseEntity;

public interface NaturalLogService {

    ResponseEntity<NaturalLogDraftResponse> parseNaturalLog(Long userId, String text);
}

package com.tracker.gateway.client;

import com.tracker.gateway.dto.ActivityLogResponse;
import com.tracker.gateway.dto.ActivityResponse;
import com.tracker.gateway.dto.AddActivityLogRequest;
import com.tracker.gateway.dto.AddActivityRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "activity-service")
public interface ActivityClient {

    @GetMapping("/activity/{name}")
    ResponseEntity<ActivityResponse> getActivity(@PathVariable("name") String name);

    @PostMapping("/activity/")
    ResponseEntity<ActivityResponse> addActivity(@RequestBody AddActivityRequest request);

    @GetMapping("/activitylog/{id}")
    ResponseEntity<ActivityLogResponse> getActivityLog(@PathVariable("id") Long id);

    @PostMapping("/activitylog/")
    ResponseEntity<ActivityLogResponse> addActivityLog(@RequestBody AddActivityLogRequest request);

    @GetMapping("/activitylog/user/{id}")
    ResponseEntity<List<ActivityLogResponse>> getAllActivityForUser(@PathVariable("id") Long id);
}

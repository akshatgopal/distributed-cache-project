package com.distributed.distributed_cache_project.api;


import com.distributed.distributed_cache_project.service.CacheService;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/cache")
public class CacheController {
    private final CacheService cacheService;

    public CacheController(CacheService cacheService){
        this.cacheService = cacheService;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getAll() {
        Map<String, Object> allEntries = cacheService.getAllCacheEntries();
        return new ResponseEntity<>(allEntries, HttpStatus.OK);
    }

    @GetMapping("/{key}")
    public ResponseEntity<Object> get(@PathVariable String key){
        Object value = cacheService.get(key);
        if(value != null){
            return new ResponseEntity<>(value, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PostMapping("/{key}")
    public ResponseEntity<String> put(@PathVariable String key, @RequestBody CachePutRequest request){
        cacheService.put(key, request.getValue(), request.getTtlMillis());
        return new ResponseEntity<>("Key '" + key + "' stored successfully.", HttpStatus.CREATED);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<String> delete(@PathVariable String key) {
        cacheService.delete(key);
        return new ResponseEntity<>("Key '" + key + "' deleted.", HttpStatus.NO_CONTENT);
    }

    @Data
    public static class CachePutRequest {
        private Object value;
        private long ttlMillis;
    }
}



package com.rabbit.app.service;

import com.rabbit.app.mapper.AuditLogMapper;
import com.rabbit.app.model.AuditLog;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void write(AuditLog log) {
        if (log == null) {
            return;
        }
        try {
            auditLogMapper.insert(log);
        } catch (Exception ignored) {
        }
    }

    public List<AuditLog> listPage(Long houseId, Long userId, String path, Integer status, Date from, Date to, int page, int pageSize) {
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 50 : pageSize;
        if (size > 200) {
            size = 200;
        }
        int offset = (p - 1) * size;
        return auditLogMapper.selectPage(houseId, userId, path, status, from, to, offset, size);
    }

    public List<AuditLog> listExportPage(Long houseId, Long userId, String path, Integer status, Date from, Date to, int offset, int limit) {
        int lim = limit <= 0 ? 1000 : limit;
        if (lim > 5000) {
            lim = 5000;
        }
        int off = Math.max(0, offset);
        return auditLogMapper.selectPage(houseId, userId, path, status, from, to, off, lim);
    }
}

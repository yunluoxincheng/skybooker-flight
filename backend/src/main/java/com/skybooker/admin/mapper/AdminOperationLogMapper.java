package com.skybooker.admin.mapper;

import com.skybooker.admin.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminOperationLogMapper {

    void insertLog(AdminOperationLog log);
}

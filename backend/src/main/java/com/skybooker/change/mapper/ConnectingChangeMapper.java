package com.skybooker.change.mapper;
import com.skybooker.change.entity.ConnectingChangeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ConnectingChangeMapper {
    void insert(ConnectingChangeRecord record);
    ConnectingChangeRecord findByOrderAndRequest(@Param("orderId") Long orderId, @Param("clientRequestId") String clientRequestId);
    ConnectingChangeRecord findByUserAndRequest(@Param("userId") Long userId, @Param("clientRequestId") String clientRequestId);
    List<ConnectingChangeRecord> findByOrderId(@Param("orderId") Long orderId);
    void insertSnapshotsFromOrder(@Param("changeRecordId") Long changeRecordId,
                                  @Param("snapshotType") String snapshotType,
                                  @Param("orderId") Long orderId);
}

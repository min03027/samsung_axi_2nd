package com.ssa.lms.care.repository;

import com.ssa.lms.care.entity.LearnerCareRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LearnerCareRecordRepository extends JpaRepository<LearnerCareRecord, Long> {
    @Query("""
            select r from LearnerCareRecord r
              join fetch r.trainee t
              join fetch r.author a
            where t.id in :traineeIds
            order by r.createdAt desc, r.id desc
            """)
    List<LearnerCareRecord> findByTraineeIds(@Param("traineeIds") Collection<Long> traineeIds);

    @Query("""
            select r from LearnerCareRecord r
              join fetch r.trainee t
              join fetch r.author a
            where t.id = :traineeId
            order by r.createdAt desc, r.id desc
            """)
    List<LearnerCareRecord> findByTraineeId(@Param("traineeId") Long traineeId);
}

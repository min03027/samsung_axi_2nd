package com.ssa.lms.demand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DemandSignalRepository extends JpaRepository<DemandSignal,Long>{List<DemandSignal> findAllByOrderByObservedOnDescIdDesc();}

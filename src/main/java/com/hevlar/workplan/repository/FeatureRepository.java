package com.hevlar.workplan.repository;

import com.hevlar.workplan.domain.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
}

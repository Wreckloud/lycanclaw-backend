package com.lycanclaw.backend.recommendation.repository;

import com.lycanclaw.backend.recommendation.entity.RecommendationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRuleRepository extends JpaRepository<RecommendationRuleEntity, String> {
}

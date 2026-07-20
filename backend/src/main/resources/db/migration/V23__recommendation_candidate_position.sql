ALTER TABLE assistant_recommendation_item
    ADD COLUMN candidate_position INT NULL;

CREATE INDEX idx_assistant_recommendation_candidate_position
    ON assistant_recommendation_item (recommendation_id, candidate_position);

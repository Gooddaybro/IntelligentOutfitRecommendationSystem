ALTER TABLE behavior_event
    ADD COLUMN recommendation_id VARCHAR(64) NULL;

ALTER TABLE behavior_event
    ADD CONSTRAINT fk_behavior_event_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES assistant_recommendation(recommendation_id);

CREATE INDEX idx_behavior_recommendation_type_time
    ON behavior_event (recommendation_id, event_type, event_time);

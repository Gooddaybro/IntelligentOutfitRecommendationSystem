ALTER TABLE chat_demand_state
    ADD COLUMN pending_clarification_json LONGTEXT NULL AFTER effective_intent_json;

ALTER TABLE chat_demand_transition
    ADD COLUMN pending_clarification_json LONGTEXT NULL AFTER effective_intent_json;

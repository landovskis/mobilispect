-- Add Spring Batch 5.x compatible sequences
-- Spring Batch 5.x uses simplified sequence names for ID generation
-- This migration adds the BATCH_JOB_SEQ and BATCH_STEP_SEQ sequences
-- that Spring Batch 5.x expects, in addition to the existing individual sequences

-- Create the unified job sequence (used by Spring Batch 5.x DefaultDataFieldMaxValueIncrementer)
CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_SEQ START WITH 1 INCREMENT BY 1;

-- Create the unified step sequence (used by Spring Batch 5.x DefaultDataFieldMaxValueIncrementer)
CREATE SEQUENCE IF NOT EXISTS BATCH_STEP_SEQ START WITH 1 INCREMENT BY 1;

-- Note: We keep the old sequences (batch_job_instance_seq, batch_job_execution_seq, batch_step_execution_seq)
-- for backward compatibility, but Spring Batch 5.x will primarily use BATCH_JOB_SEQ and BATCH_STEP_SEQ

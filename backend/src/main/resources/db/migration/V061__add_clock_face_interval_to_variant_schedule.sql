-- Add clock_face_interval_minutes column to variant_schedule table
-- This column stores the detected clock-face scheduling interval in minutes
-- (10, 12, 15, 20, 30, or 60), or NULL if no regular pattern detected.

ALTER TABLE variant_schedule
ADD COLUMN clock_face_interval_minutes INTEGER NULL;

COMMENT ON COLUMN variant_schedule.clock_face_interval_minutes IS
    'Detected clock-face scheduling interval in minutes (10, 12, 15, 20, 30, or 60), or NULL if no regular pattern';

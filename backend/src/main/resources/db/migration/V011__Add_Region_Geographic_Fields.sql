-- Add Geographic Fields to Metropolitan Regions
-- Version: 1.0.0
-- Adds adm0_name (country) and adm1_name (state/province) fields to support
-- proper geographic hierarchy while keeping region name as city name

-- Add adm0_name (country) and adm1_name (state/province) columns
ALTER TABLE metropolitan_regions
    ADD COLUMN adm0_name VARCHAR(255),
    ADD COLUMN adm1_name VARCHAR(255);

-- Add comments for documentation
COMMENT ON COLUMN metropolitan_regions.name IS 'City name (primary identifier for users)';
COMMENT ON COLUMN metropolitan_regions.adm0_name IS 'Country name (ISO 3166-1 administrative level 0)';
COMMENT ON COLUMN metropolitan_regions.adm1_name IS 'State/Province name (ISO 3166-2 administrative level 1)';

-- Update seed data with geographic information for existing regions
UPDATE metropolitan_regions SET adm0_name = 'Canada', adm1_name = 'Quebec' WHERE region_onestop_id = 'r-9q8y-montreal';
UPDATE metropolitan_regions SET adm0_name = 'Canada', adm1_name = 'Ontario' WHERE region_onestop_id = 'r-9q9-toronto';
UPDATE metropolitan_regions SET adm0_name = 'Canada', adm1_name = 'British Columbia' WHERE region_onestop_id = 'r-9q5-vancouver';
UPDATE metropolitan_regions SET adm0_name = 'Canada', adm1_name = 'Ontario' WHERE region_onestop_id = 'r-9q8z-ottawa';
UPDATE metropolitan_regions SET adm0_name = 'United States of America', adm1_name = 'California' WHERE region_onestop_id = 'r-f25c-sanfranciscobayarea';

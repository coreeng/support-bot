CREATE TABLE bank_holidays (
  holiday_date DATE PRIMARY KEY,
  name TEXT
);

INSERT INTO bank_holidays (holiday_date, name) VALUES
-- 2023
('2023-01-02', 'New Year''s Day (Substitute)'),
('2023-04-07', 'Good Friday'),
('2023-04-10', 'Easter Monday'),
('2023-05-01', 'Early May'),
('2023-05-08', 'Coronation Bank Holiday'),
('2023-05-29', 'Spring Bank'),
('2023-08-28', 'Summer Bank'),
('2023-12-25', 'Christmas'),
('2023-12-26', 'Boxing Day'),

-- 2024
('2024-01-01', 'New Year''s Day'),
('2024-03-29', 'Good Friday'),
('2024-04-01', 'Easter Monday'),
('2024-05-06', 'Early May'),
('2024-05-27', 'Spring Bank'),
('2024-08-26', 'Summer Bank'),
('2024-12-25', 'Christmas'),
('2024-12-26', 'Boxing Day'),

-- 2025
('2025-01-01', 'New Year''s Day'),
('2025-04-18', 'Good Friday'),
('2025-04-21', 'Easter Monday'),
('2025-05-05', 'Early May'),
('2025-05-26', 'Spring Bank'),
('2025-08-25', 'Summer Bank'),
('2025-12-25', 'Christmas'),
('2025-12-26', 'Boxing Day'),

-- 2026
('2026-01-01', 'New Year''s Day'),
('2026-04-03', 'Good Friday'),
('2026-04-06', 'Easter Monday'),
('2026-05-04', 'Early May'),
('2026-05-25', 'Spring Bank'),
('2026-08-31', 'Summer Bank'),
('2026-12-25', 'Christmas'),
('2026-12-28', 'Boxing Day (Substitute)');

CREATE TABLE support_calendar (
    hour_ts    TIMESTAMPTZ NOT NULL,
    work_hour  BOOLEAN NOT NULL DEFAULT TRUE,
    type       TEXT DEFAULT 'business hour',

    CONSTRAINT working_hours_pkey PRIMARY KEY (hour_ts)
);

INSERT INTO support_calendar (hour_ts)
SELECT generate_series(
   '2023-01-01 00:00:00 Europe/London'::timestamptz,
   '2027-01-01 00:00:00 Europe/London'::timestamptz,
   '1 hour'::interval
);

UPDATE support_calendar
SET    work_hour = FALSE,
       type      = 'off hour'
WHERE  NOT (
    EXTRACT(DOW FROM hour_ts AT TIME ZONE 'Europe/London') BETWEEN 1 AND 5
    AND (hour_ts AT TIME ZONE 'Europe/London')::time >= TIME '08:00'
    AND (hour_ts AT TIME ZONE 'Europe/London')::time <  TIME '18:00'
);

UPDATE support_calendar
SET    work_hour = FALSE,
       type      = 'bank holiday'
WHERE  hour_ts::date IN (
    SELECT holiday_date FROM bank_holidays
);

SELECT EXTRACT(YEAR FROM hour_ts) AS year,type,count(*)
FROM support_calendar
GROUP BY year,type order by year,type;

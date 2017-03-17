ALTER TABLE Products ADD PeriodCount INTEGER;
ALTER TABLE Products ADD PeriodType TEXT;
ALTER TABLE Products ADD LastBuyDate INTEGER;

UPDATE Products SET PeriodType = 'WEEKS';
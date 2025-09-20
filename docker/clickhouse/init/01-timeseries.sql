SET allow_experimental_time_series_table = 1;

CREATE DATABASE IF NOT EXISTS metrics;

CREATE TABLE IF NOT EXISTS metrics.prom_rw
    ENGINE = TimeSeries;

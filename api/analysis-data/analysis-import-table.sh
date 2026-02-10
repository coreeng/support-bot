#!/bin/bash

user=postgres
database=flywaydb

# Load the data
psql -U ${user} -d ${database} -h localhost -p 5432 -W -c "\copy analysis(ticket_id, driver, category, feature, summary) FROM 'analysis.tsv' DELIMITER E'\t' CSV HEADER;"
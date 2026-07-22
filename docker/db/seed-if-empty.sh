#!/bin/sh
# Load sample data, but only when the database has no records yet, so that
# restarting the stack does not pile up duplicate rows.
set -e

export MYSQL_PWD=localpw

count=$(mysql -hdb -uroot -N -e "SELECT COUNT(*) FROM jointacct.records" 2>/dev/null || echo 0)

if [ "$count" = "0" ]; then
    mysql -hdb -uroot jointacct < /seed/seed.sql
    echo "[seed] sample data loaded"
else
    echo "[seed] records already present ($count rows); skipping"
fi

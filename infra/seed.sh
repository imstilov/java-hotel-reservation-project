#!/bin/bash
set -euo pipefail

CONTAINER="${CONTAINER:-java-hotel-reservation-project-postgres-1}"
PGUSER="${PGUSER:-postgres}"
PGDB="${PGDB:-postgres}"

USERS_TOTAL=4000000
USERS_BATCH=500000

RESERVATIONS_TOTAL=10000000
RESERVATIONS_BATCH=500000

psql() {
    docker exec -i "$CONTAINER" psql -U "$PGUSER" -d "$PGDB" "$@"
}

echo "================================================"
echo " Seed: ${USERS_TOTAL} users + ${RESERVATIONS_TOTAL} reservations"
echo "================================================"
echo ""

# ── Users ──────────────────────────────────────────────────────────────────────
USERS_BATCHES=$(( USERS_TOTAL / USERS_BATCH ))
echo "[1/2] Inserting ${USERS_TOTAL} users in ${USERS_BATCHES} batches..."

for (( b=0; b<USERS_BATCHES; b++ )); do
    OFFSET=$(( b * USERS_BATCH ))
    echo "  batch $(( b + 1 ))/${USERS_BATCHES}  (rows $((OFFSET+1)) – $((OFFSET+USERS_BATCH)))"
    psql -c "
        INSERT INTO users (email, first_name, last_name, created_at)
        SELECT
            'seed_' || ($OFFSET + i) || '@example.com',
            'First' || (($OFFSET + i) % 500 + 1),
            'Last'  || (($OFFSET + i) % 300 + 1),
            NOW() - (floor(random() * 1095)::INT || ' days')::INTERVAL
        FROM generate_series(1, $USERS_BATCH) AS s(i);
    "
done
echo ""

# ── Resolve user ID range ──────────────────────────────────────────────────────
read -r MIN_UID MAX_UID < <(
    psql -t -A -F' ' -c "SELECT MIN(id), MAX(id) FROM users;"
)
UID_RANGE=$(( MAX_UID - MIN_UID ))
echo "User ID range: ${MIN_UID} – ${MAX_UID}  (range: ${UID_RANGE})"
echo ""

# ── Reservations ───────────────────────────────────────────────────────────────
RESERVATIONS_BATCHES=$(( RESERVATIONS_TOTAL / RESERVATIONS_BATCH ))
echo "[2/2] Inserting ${RESERVATIONS_TOTAL} reservations in ${RESERVATIONS_BATCHES} batches..."

for (( b=0; b<RESERVATIONS_BATCHES; b++ )); do
    echo "  batch $(( b + 1 ))/${RESERVATIONS_BATCHES}"
    psql -c "
        INSERT INTO reservations (user_id, room_id, start_date, end_date, status)
        SELECT
            ${MIN_UID} + (random() * ${UID_RANGE})::BIGINT,
            (floor(random() * 1000) + 1)::INT,
            d,
            d + (floor(random() * 13)::INT + 1),
            (ARRAY['PENDING','APPROVED','CANCELED'])[floor(random() * 3 + 1)::INT]
        FROM (
            SELECT CURRENT_DATE - (floor(random() * 730)::INT) AS d
            FROM generate_series(1, ${RESERVATIONS_BATCH})
        ) sub;
    "
done
echo ""

# ── Summary ────────────────────────────────────────────────────────────────────
echo "================================================"
echo " Done! Final counts:"
psql -c "
    SELECT 'users'        AS \"table\", COUNT(*) AS rows FROM users
    UNION ALL
    SELECT 'reservations', COUNT(*)                     FROM reservations;
"

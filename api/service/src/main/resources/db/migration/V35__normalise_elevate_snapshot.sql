ALTER TABLE elevate_products
    ADD COLUMN slug TEXT,
    ADD COLUMN name TEXT,
    ADD COLUMN customer TEXT,
    ADD COLUMN created_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN last_updated_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE elevate_products
   SET slug = payload ->> 'slug',
       name = payload ->> 'name',
       customer = payload ->> 'customer',
       created_at = (payload ->> 'createdAt')::TIMESTAMP,
       last_updated_at = (payload ->> 'lastUpdatedAt')::TIMESTAMP;

ALTER TABLE elevate_products
    ALTER COLUMN slug SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN last_updated_at SET NOT NULL;

ALTER TABLE elevate_users
    ADD COLUMN product_id TEXT,
    ADD COLUMN name TEXT,
    ADD COLUMN description TEXT,
    ADD COLUMN created_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN last_updated_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE elevate_users
   SET product_id = payload ->> 'productId',
       name = payload ->> 'name',
       description = payload ->> 'description',
       created_at = (payload ->> 'createdAt')::TIMESTAMP,
       last_updated_at = (payload ->> 'lastUpdatedAt')::TIMESTAMP;

ALTER TABLE elevate_users
    ALTER COLUMN product_id SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN last_updated_at SET NOT NULL;

ALTER TABLE elevate_journeys
    ADD COLUMN slug TEXT,
    ADD COLUMN name TEXT,
    ADD COLUMN product_id TEXT,
    ADD COLUMN product_slug TEXT,
    ADD COLUMN user_description TEXT,
    ADD COLUMN primary_problems TEXT,
    ADD COLUMN created_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN last_updated_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE elevate_journeys
   SET slug = payload ->> 'slug',
       name = payload ->> 'name',
       product_id = payload ->> 'productId',
       product_slug = payload ->> 'productSlug',
       user_description = payload ->> 'userDescription',
       primary_problems = payload ->> 'primaryProblems',
       created_at = (payload ->> 'createdAt')::TIMESTAMP,
       last_updated_at = (payload ->> 'lastUpdatedAt')::TIMESTAMP;

ALTER TABLE elevate_journeys
    ALTER COLUMN slug SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN product_id SET NOT NULL,
    ALTER COLUMN product_slug SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN last_updated_at SET NOT NULL;

-- Existing V34 snapshot rows are retained during migration, while every newly written
-- snapshot must preserve Elevate's journey-to-product invariant.
ALTER TABLE elevate_journeys
    ADD CONSTRAINT elevate_journeys_product_id_fkey
    FOREIGN KEY (product_id) REFERENCES elevate_products(resource_id) NOT VALID;

CREATE TABLE elevate_journey_users (
    journey_id TEXT NOT NULL REFERENCES elevate_journeys(resource_id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    PRIMARY KEY (journey_id, user_id)
);

CREATE TABLE elevate_integrity_items (
    type TEXT NOT NULL CHECK (type IN (
        'ORPHAN_USER',
        'MISSING_ASSIGNMENT',
        'CROSS_PRODUCT_ASSIGNMENT'
    )),
    journey_id TEXT,
    journey_name TEXT,
    journey_product_id TEXT,
    user_id UUID,
    user_name TEXT,
    user_product_id TEXT,
    sort_name TEXT NOT NULL,
    sort_id TEXT NOT NULL,
    search_text TEXT NOT NULL
);

INSERT INTO elevate_journey_users (journey_id, user_id)
SELECT journey.resource_id,
       user_id.value::UUID
  FROM elevate_journeys journey
 CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(journey.payload -> 'userIds', '[]'::JSONB)) user_id(value)
ON CONFLICT DO NOTHING;

CREATE VIEW elevate_integrity_item_source AS
SELECT integrity.type,
       integrity.journey_id,
       integrity.journey_name,
       integrity.journey_product_id,
       integrity.user_id,
       integrity.user_name,
       integrity.user_product_id,
       LOWER(COALESCE(integrity.journey_name, integrity.user_name, '')) AS sort_name,
       CONCAT_WS(CHR(31), integrity.journey_id, integrity.user_id::TEXT) AS sort_id,
       LOWER(CONCAT_WS(
           ' ',
           integrity.journey_name,
           integrity.journey_id,
           integrity.user_name,
           integrity.user_id::TEXT)) AS search_text
  FROM (
        SELECT 'ORPHAN_USER' AS type,
               NULL::TEXT AS journey_id,
               NULL::TEXT AS journey_name,
               NULL::TEXT AS journey_product_id,
               u.resource_id AS user_id,
               u.name AS user_name,
               u.product_id AS user_product_id
          FROM elevate_users u
          LEFT JOIN elevate_products p ON p.resource_id = u.product_id
         WHERE p.resource_id IS NULL
        UNION ALL
        SELECT 'MISSING_ASSIGNMENT' AS type,
               j.resource_id AS journey_id,
               j.name AS journey_name,
               j.product_id AS journey_product_id,
               ju.user_id AS user_id,
               NULL::TEXT AS user_name,
               NULL::TEXT AS user_product_id
          FROM elevate_journey_users ju
          JOIN elevate_journeys j ON j.resource_id = ju.journey_id
          LEFT JOIN elevate_users u ON u.resource_id = ju.user_id
         WHERE u.resource_id IS NULL
        UNION ALL
        SELECT 'CROSS_PRODUCT_ASSIGNMENT' AS type,
               j.resource_id AS journey_id,
               j.name AS journey_name,
               j.product_id AS journey_product_id,
               u.resource_id AS user_id,
               u.name AS user_name,
               u.product_id AS user_product_id
          FROM elevate_journey_users ju
          JOIN elevate_journeys j ON j.resource_id = ju.journey_id
          JOIN elevate_users u ON u.resource_id = ju.user_id
         WHERE u.product_id <> j.product_id
       ) integrity;

INSERT INTO elevate_integrity_items
       (type, journey_id, journey_name, journey_product_id, user_id, user_name, user_product_id,
        sort_name, sort_id, search_text)
SELECT type, journey_id, journey_name, journey_product_id, user_id, user_name, user_product_id,
       sort_name, sort_id, search_text
  FROM elevate_integrity_item_source;

ALTER TABLE elevate_sync_state ADD COLUMN snapshot_version UUID;

UPDATE elevate_sync_state
   SET snapshot_version = gen_random_uuid()
 WHERE last_sync_success_at IS NOT NULL
    OR EXISTS (SELECT 1 FROM elevate_products)
    OR EXISTS (SELECT 1 FROM elevate_users)
    OR EXISTS (SELECT 1 FROM elevate_journeys);

CREATE INDEX elevate_products_name_id_idx ON elevate_products (LOWER(name), resource_id);
CREATE INDEX elevate_products_slug_idx ON elevate_products (LOWER(slug), resource_id);
CREATE INDEX elevate_journeys_product_name_id_idx ON elevate_journeys (product_id, LOWER(name), resource_id);
CREATE INDEX elevate_journeys_name_id_idx ON elevate_journeys (LOWER(name), resource_id);
CREATE INDEX elevate_users_product_name_id_idx ON elevate_users (product_id, LOWER(name), resource_id);
CREATE INDEX elevate_users_name_id_idx ON elevate_users (LOWER(name), resource_id);
CREATE INDEX elevate_journey_users_user_journey_idx ON elevate_journey_users (user_id, journey_id);
CREATE INDEX elevate_integrity_items_type_sort_idx
    ON elevate_integrity_items (type, sort_name, sort_id);
CREATE INDEX elevate_integrity_items_name_sort_idx
    ON elevate_integrity_items (sort_name, sort_id, type);

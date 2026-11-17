CREATE FUNCTION pg_temp.expect_error(statement TEXT, expected TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE actual TEXT;
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual = RETURNED_SQLSTATE;
    END;
    IF actual IS DISTINCT FROM expected THEN
        RAISE EXCEPTION 'Expected SQLSTATE %, got % for %', expected, actual, statement;
    END IF;
END $$;

INSERT INTO app_user(id,name,email,password_hash,role) VALUES
('10000000-0000-4000-8000-000000000001','Seller','seller@example.com','test-only-hash','SELLER'),
('10000000-0000-4000-8000-000000000002','Buyer','buyer@example.com','test-only-hash','BUYER');
INSERT INTO media_asset(id,owner_user_id,storage_key,content_type,byte_size,width,height,state) VALUES
('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','test/image','image/png',1024,32,32,'READY');
INSERT INTO seller_profile(id,user_id,store_name,description,logo_asset_id) VALUES
('30000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','Test Store','','20000000-0000-4000-8000-000000000001');
INSERT INTO product(id,seller_id,category_id,title,description,price,colour,listing_status) VALUES
('40000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000001','Dress','',123.45,'red','DRAFT');
INSERT INTO product_size VALUES ('40000000-0000-4000-8000-000000000001','M');
INSERT INTO product_image VALUES ('50000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001',0,'Front');
INSERT INTO product_tag(id,product_id,tag,source,status) VALUES
('60000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000001','Party Wear','SELLER','APPROVED');
INSERT INTO wishlist_item(user_id,product_id) VALUES ('10000000-0000-4000-8000-000000000002','40000000-0000-4000-8000-000000000001');
INSERT INTO review(id,reviewer_id,product_id,rating) VALUES
('70000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000002','40000000-0000-4000-8000-000000000001',5);
INSERT INTO report(id,reporter_id,review_id,reason) VALUES
('80000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000002','70000000-0000-4000-8000-000000000001','SPAM');
INSERT INTO admin_audit_event(id,actor_id,action,target_type,target_id,reason,before_state,after_state) VALUES
('90000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','REVIEW_MODERATION','REVIEW','70000000-0000-4000-8000-000000000001','Test only','{"status":"PENDING"}','{"status":"APPROVED"}');

SELECT pg_temp.expect_error($s$UPDATE app_user SET email='UPPER@example.com'$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE app_user SET role='ROOT'$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE product SET price=-1$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE product SET colour='Red'$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE product SET stock_quantity=-1$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE media_asset SET byte_size=5242881$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE product_size SET size_code='m'$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE product_tag SET normalized_tag='wrong'$s$, '428C9');
SELECT pg_temp.expect_error($s$UPDATE product_image SET display_order=10$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE review SET rating=6$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE report SET product_id='40000000-0000-4000-8000-000000000001'$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE report SET status='RESOLVED'$s$, '23514');
SELECT pg_temp.expect_error($s$UPDATE admin_audit_event SET after_state='[]'$s$, '23514');
SELECT pg_temp.expect_error($s$INSERT INTO category(id,name,slug) VALUES(gen_random_uuid(),'dresses','test-duplicate')$s$, '23505');
SELECT pg_temp.expect_error($s$INSERT INTO wishlist_item(user_id,product_id) SELECT user_id,product_id FROM wishlist_item$s$, '23505');
SELECT pg_temp.expect_error($s$INSERT INTO review(id,reviewer_id,product_id,rating) SELECT gen_random_uuid(),reviewer_id,product_id,5 FROM review$s$, '23505');
SELECT pg_temp.expect_error($s$INSERT INTO report(id,reporter_id,review_id,reason) SELECT gen_random_uuid(),reporter_id,review_id,'SPAM' FROM report$s$, '23505');
SELECT pg_temp.expect_error($s$DELETE FROM app_user WHERE role='SELLER'$s$, '23001');

DO $$ BEGIN
    IF (SELECT count(*) FROM category) <> 6 THEN RAISE EXCEPTION 'Category seeds missing'; END IF;
    IF (SELECT count(*) FROM foundation_metadata) <> 1 THEN RAISE EXCEPTION 'Foundation missing'; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uq_product_image_order' AND condeferrable) THEN RAISE EXCEPTION 'Image order is not deferrable'; END IF;
END $$;
DELETE FROM wishlist_item;
DELETE FROM report;
DELETE FROM review;
DELETE FROM product;
DO $$ BEGIN
    IF EXISTS (SELECT FROM product_size) OR EXISTS (SELECT FROM product_tag) OR EXISTS (SELECT FROM product_image) THEN
        RAISE EXCEPTION 'Product child cascade failed';
    END IF;
END $$;
SELECT 'All 18 rejection cases, seeds, deferrable order and child cascades passed' AS sql_result;
ROLLBACK;
SELECT count(*) AS tables_after_rollback FROM information_schema.tables WHERE table_schema='public';

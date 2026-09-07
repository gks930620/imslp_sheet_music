-- 파일 테이블 샘플 데이터 (필요시 사용), 실제 파일이 찾으려하지말고. 서버키는용.  (아무 sql쿼리없으면 에러)
 --테스트는 직접 화면에서  파일 저장해보기  
 INSERT INTO files (created_at,file_size,ref_id,updated_at,content_type,file_path,original_file_name,stored_file_name,ref_type,file_usage) VALUES
  ('2026-01-08 18:06:27.000000',1024,1,'2026-01-08 18:06:27.000000','image/jpeg','uploads/','sample_thumb.jpeg','sample.jpeg','COMMUNITY','THUMBNAIL');


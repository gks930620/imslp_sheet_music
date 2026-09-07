-- H2 전용(dev/test 프로파일): 시드가 명시 ID로 INSERT하면 IDENTITY 시퀀스가 갱신되지 않아
-- 이후 신규 INSERT(회원가입/글작성 등)에서 PK 충돌이 발생한다. 시퀀스를 시드 최대값 위로 리셋.
-- (prod 프로파일은 sql.init.mode=never 라 이 스크립트가 실행되지 않음 → MySQL과 무관)
ALTER TABLE users ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE community ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE comment ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE files ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE chat_room ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE refresh_entity ALTER COLUMN id RESTART WITH 1000;

package com.test.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // orphan 파일 정리 배치(§5-3-1 ③) 스케줄링 활성화
public class SheetMusicApplication {

	public static void main(String[] args)
	{
		// .env 파일은 spring-dotenv 가 자동 로드한다.
		// (과거엔 여기서 KAKAO/GOOGLE client_id 를 stdout 으로 출력했는데,
		//  자격증명 힌트가 로그에 잔류하므로 제거함)
		SpringApplication.run(SheetMusicApplication.class, args);
	}

}

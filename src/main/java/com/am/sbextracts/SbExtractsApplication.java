package com.am.sbextracts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;

@SpringBootApplication
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@EnableFeignClients
@EnableCaching
public class SbExtractsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SbExtractsApplication.class, args);
	}
}

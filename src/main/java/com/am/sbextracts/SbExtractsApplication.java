package com.am.sbextracts;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.asynchttpclient.AsyncHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;

@SpringBootApplication
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class SbExtractsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SbExtractsApplication.class, args);
	}
}

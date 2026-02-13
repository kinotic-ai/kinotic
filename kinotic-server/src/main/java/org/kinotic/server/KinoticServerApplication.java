package org.kinotic.server;

import org.kinotic.rpc.api.annotations.EnableKinoticRpc;
import org.kinotic.rpc.gateway.api.annotations.EnableContinuumGateway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@EnableKinoticRpc
@EnableContinuumGateway
public class KinoticServerApplication {
	public static void main(String[] args) {
		SpringApplication.run(KinoticServerApplication.class, args);
	}
}

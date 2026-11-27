package com.udea.metria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ConfiguracionBasePruebas.class)
class MetriaApplicationTests {

	@Test
	void contextLoads() {
	}

}

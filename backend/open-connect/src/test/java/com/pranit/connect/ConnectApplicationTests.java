package com.pranit.connect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
class ConnectApplicationTests {

    @Test
    void contextLoads() {
        var modules = ApplicationModules.of(ConnectApplication.class);
        modules.verify();
    }

}

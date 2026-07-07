package com.skybooker.common;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 所有继承本类的集成测试在类结束后清理 Spring context,避免跨测试类复用同一个 context
 * 导致有状态 bean(如 InMemoryLoginRateLimiter)的计数残留污染后续测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ai.llm.enabled=false",
        "ai.llm.base-url=",
        "ai.llm.api-key=",
        "ai.llm.model=",
        "mail.provider=log"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {
}

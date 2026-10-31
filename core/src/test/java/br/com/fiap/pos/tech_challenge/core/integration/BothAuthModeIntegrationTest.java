package br.com.fiap.pos.tech_challenge.core.integration;

import br.com.fiap.pos.tech_challenge.core.application.port.out.CustomerRepository;
import br.com.fiap.pos.tech_challenge.core.application.port.out.UserRepository;
import br.com.fiap.pos.tech_challenge.core.domain.enums.DocumentType;
import br.com.fiap.pos.tech_challenge.core.domain.enums.UserRole;
import br.com.fiap.pos.tech_challenge.core.domain.model.Customer;
import br.com.fiap.pos.tech_challenge.core.domain.model.User;
import br.com.fiap.pos.tech_challenge.core.infrastructure.security.TokenUtility;
import br.com.fiap.pos.tech_challenge.core.infrastructure.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code security.auth.mode=BOTH}: JWT de funcionário e de cliente (Lambda) convivem.
 *
 * @author johncgo
 * @since 2026-10-31
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.auth.mode=BOTH",
        "security.auth.customer.issuer=" + CustomerTokens.ISSUER,
        "security.auth.customer.audience=" + CustomerTokens.AUDIENCE,
        "security.auth.customer.public-key=classpath:certs/test-customer-public.pem"
})
@Testcontainers(disabledWithoutDocker = true)
class BothAuthModeIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("tech_challenge_both_auth_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired WebApplicationContext context;
    @Autowired CustomerRepository customerRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TokenUtility tokenUtility;
    @MockitoBean JavaMailSender mailSender;

    MockMvc mockMvc;

    private UUID customerUuid;
    private User staff;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Customer customer = new Customer();
        customer.setDocumentType(DocumentType.CPF);
        customer.setDocument(randomCpf());
        customer.setName("Cliente " + suffix);
        customer.setEmail("cliente." + suffix + "@example.com");
        customerUuid = customerRepository.save(customer).getUuid();

        staff = staffUser(suffix);
    }

    @Test
    void staffSignin_stillWorks() throws Exception {
        mockMvc.perform(post("/api/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + staff.getLogin() + "\",\"password\":\"Test@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenValue").isNotEmpty());
    }

    @Test
    void staffToken_reachesStaffRoutes() throws Exception {
        String token = bearer(tokenUtility.generate(new UserDetailsImpl(staff)).tokenValue());

        mockMvc.perform(get("/api/service-orders").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    void customerToken_getsOwnProfile() throws Exception {
        mockMvc.perform(get("/api/customer/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CustomerTokens.valid(customerUuid))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(customerUuid.toString()));
    }

    @Test
    void customerToken_isForbiddenOnStaffRoutes() throws Exception {
        String token = bearer(CustomerTokens.valid(customerUuid));

        mockMvc.perform(get("/api/customers").param("document", "x").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffToken_cannotAccessCustomerArea() throws Exception {
        String token = bearer(tokenUtility.generate(new UserDetailsImpl(staff)).tokenValue());

        mockMvc.perform(get("/api/customer/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    private User staffUser(String suffix) {
        User user = new User();
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setEmail("staff." + suffix + "@tech.com");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        user.setLogin("staff." + suffix);
        user.setPassword(passwordEncoder.encode("Test@123"));
        user.setPhone("11999999999");
        user.setRole(UserRole.ATTENDANT);
        user.setActive(true);
        return userRepository.save(user);
    }

    private static String randomCpf() {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) d[i] = random.nextInt(10);
        for (int j = 9; j < 11; j++) {
            int sum = 0;
            for (int i = 0; i < j; i++) sum += d[i] * (j + 1 - i);
            int digit = 11 - (sum % 11);
            d[j] = digit >= 10 ? 0 : digit;
        }
        var cpf = new StringBuilder();
        for (int digit : d) cpf.append(digit);
        return cpf.toString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}

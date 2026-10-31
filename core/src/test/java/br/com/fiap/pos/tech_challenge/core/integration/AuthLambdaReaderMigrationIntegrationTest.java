package br.com.fiap.pos.tech_challenge.core.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Changeset 025: usuário {@code auth_lambda_reader} da Lambda de autenticação.
 * Com a senha informada, o usuário lê só as colunas de {@code customers} usadas no login por CPF.
 *
 * @author johncgo
 * @since 2026-10-31
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.liquibase.parameters.authReaderPassword=" + AuthLambdaReaderMigrationIntegrationTest.READER_PASSWORD)
@Testcontainers(disabledWithoutDocker = true)
class AuthLambdaReaderMigrationIntegrationTest {

    static final String READER_PASSWORD = "reader-test-pw";
    static final String CPF = "52998224725";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("tech_challenge_reader_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean JavaMailSender mailSender;

    @Autowired JdbcTemplate jdbc;

    private void insertCustomer() {
        jdbc.update("""
                INSERT INTO customers (_uuid, _document_type, _document, _name, _email, _phone, _created_at, _version, _active)
                VALUES (?, 'CPF', '529.982.247-25', 'Cliente Leitura', 'leitura@email.com', '11999990000', NOW(), 0, true)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID());
    }

    private Connection readerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "auth_lambda_reader", READER_PASSWORD);
    }

    @Test
    void readerRunsTheSameQueryAsTheLambda() throws SQLException {
        insertCustomer();

        try (Connection c = readerConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT _uuid, _name, _active
                       FROM customers
                      WHERE _document_type = 'CPF'
                        AND regexp_replace(_document, '\\D', '', 'g') = ?
                     """)) {
            ps.setString(1, CPF);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("_name")).isEqualTo("Cliente Leitura");
                assertThat(rs.getBoolean("_active")).isTrue();
            }
        }
    }

    @Test
    void readerCannotSeeOtherCustomerColumns() throws SQLException {
        try (Connection c = readerConnection(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("SELECT _email FROM customers"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void readerCannotReadOtherTablesNorWrite() throws SQLException {
        try (Connection c = readerConnection(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("SELECT _login FROM users"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.executeUpdate("UPDATE customers SET _active = false"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }
}

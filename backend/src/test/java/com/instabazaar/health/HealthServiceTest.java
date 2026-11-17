package com.instabazaar.health;

import com.instabazaar.health.repository.HealthRepository;
import com.instabazaar.health.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HealthServiceTest {
    private final HealthRepository repository = mock(HealthRepository.class);
    private final HealthService service = new HealthService(repository);

    @Test
    void requiresDatabaseAndMigrationForUpStatus() {
        when(repository.databaseResponds()).thenReturn(true);
        when(repository.foundationExists()).thenReturn(true);
        var health = service.check();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.checks().migrations()).isEqualTo("APPLIED");
    }

    @Test
    void reportsDatabaseFailureWithoutLeakingTheException() {
        when(repository.databaseResponds()).thenThrow(new DataAccessResourceFailureException("private detail"));
        var health = service.check();
        assertThat(health.status()).isEqualTo("DOWN");
        assertThat(health.checks().database()).isEqualTo("DOWN");
        assertThat(health.toString()).doesNotContain("private detail");
        verify(repository, never()).foundationExists();
    }

    @Test
    void distinguishesMissingMigrationFromAnUnavailableDatabase() {
        when(repository.databaseResponds()).thenReturn(true);
        when(repository.foundationExists()).thenThrow(new DataAccessResourceFailureException("table missing"));
        var health = service.check();
        assertThat(health.status()).isEqualTo("DOWN");
        assertThat(health.checks().database()).isEqualTo("UP");
        assertThat(health.checks().migrations()).isEqualTo("UNAVAILABLE");
    }
}

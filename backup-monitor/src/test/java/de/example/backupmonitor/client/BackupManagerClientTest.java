package de.example.backupmonitor.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.example.backupmonitor.auth.CfTokenServiceRegistry;
import de.example.backupmonitor.config.MonitoringConfig;
import de.example.backupmonitor.model.BackupJob;
import de.example.backupmonitor.model.BackupPlan;
import de.example.backupmonitor.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class BackupManagerClientTest {

    private static final String MANAGER_ID = "mgr-1";
    private static final String INSTANCE_ID = "inst-001";

    @Mock
    private CfTokenServiceRegistry tokenRegistry;

    private BackupManagerClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        when(tokenRegistry.getToken(MANAGER_ID)).thenReturn("test-bearer-token");

        MonitoringConfig config = new MonitoringConfig();
        MonitoringConfig.ManagerConfig manager = new MonitoringConfig.ManagerConfig();
        manager.setId(MANAGER_ID);
        manager.setUrl("http://localhost:" + wm.getHttpPort());
        config.setManagers(List.of(manager));

        client = new BackupManagerClient(config, tokenRegistry);
    }

    // ── getBackupPlan ──────────────────────────────────────────────────────────

    @Test
    void getBackupPlan_planExists_returnsPlan() {
        stubFor(get(urlPathEqualTo("/backupPlans/byInstance/" + INSTANCE_ID))
                .willReturn(okJson("""
                        {"content":[{"id":"plan-1","paused":false}],"totalElements":1}
                        """)));

        Optional<BackupPlan> result = client.getBackupPlan(MANAGER_ID, INSTANCE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getIdAsString()).isEqualTo("plan-1");
        assertThat(result.get().isPaused()).isFalse();
    }

    @Test
    void getBackupPlan_emptyContent_returnsEmpty() {
        stubFor(get(urlPathEqualTo("/backupPlans/byInstance/" + INSTANCE_ID))
                .willReturn(okJson("""
                        {"content":[],"totalElements":0}
                        """)));

        assertThat(client.getBackupPlan(MANAGER_ID, INSTANCE_ID)).isEmpty();
    }

    @Test
    void getBackupPlan_serverError_returnsEmpty() {
        stubFor(get(urlPathEqualTo("/backupPlans/byInstance/" + INSTANCE_ID))
                .willReturn(serverError()));

        assertThat(client.getBackupPlan(MANAGER_ID, INSTANCE_ID)).isEmpty();
    }

    // ── getLatestJob ───────────────────────────────────────────────────────────

    @Test
    void getLatestJob_jobFound_returnsJob() {
        stubFor(get(urlPathEqualTo("/backupJobs/byInstance/" + INSTANCE_ID))
                .willReturn(okJson("""
                        {"content":[{"id":"job-42","status":"SUCCEEDED"}],"totalElements":1}
                        """)));

        Optional<BackupJob> result = client.getLatestJob(MANAGER_ID, INSTANCE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getIdAsString()).isEqualTo("job-42");
        assertThat(result.get().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void getLatestJob_emptyContent_returnsEmpty() {
        stubFor(get(urlPathEqualTo("/backupJobs/byInstance/" + INSTANCE_ID))
                .willReturn(okJson("""
                        {"content":[],"totalElements":0}
                        """)));

        assertThat(client.getLatestJob(MANAGER_ID, INSTANCE_ID)).isEmpty();
    }

    // ── getLatestBackupJob ─────────────────────────────────────────────────────

    @Test
    void getLatestBackupJob_succeededJobFound_returnsJob() {
        stubFor(get(urlPathEqualTo("/backupJobs/byInstance/" + INSTANCE_ID + "/filtered"))
                .withQueryParam("jobStatus", equalTo("SUCCEEDED"))
                .willReturn(okJson("""
                        {"content":[{"id":"job-99","status":"SUCCEEDED"}],"totalElements":1}
                        """)));

        Optional<BackupJob> result = client.getLatestBackupJob(MANAGER_ID, INSTANCE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    // ── getJobById ─────────────────────────────────────────────────────────────

    @Test
    void getJobById_found_returnsJob() {
        stubFor(get(urlEqualTo("/backupJobs/job-77"))
                .willReturn(okJson("""
                        {"id":"job-77","status":"FAILED"}
                        """)));

        Optional<BackupJob> result = client.getJobById(MANAGER_ID, "job-77");

        assertThat(result).isPresent();
        assertThat(result.get().getIdAsString()).isEqualTo("job-77");
        assertThat(result.get().getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void getJobById_notFound_returnsEmpty() {
        stubFor(get(urlEqualTo("/backupJobs/missing-job"))
                .willReturn(notFound()));

        assertThat(client.getJobById(MANAGER_ID, "missing-job")).isEmpty();
    }

    // ── Auth ───────────────────────────────────────────────────────────────────

    @Test
    void bearerToken_isSentWithEveryRequest() {
        stubFor(get(urlPathEqualTo("/backupPlans/byInstance/" + INSTANCE_ID))
                .willReturn(okJson("""
                        {"content":[],"totalElements":0}
                        """)));

        client.getBackupPlan(MANAGER_ID, INSTANCE_ID);

        verify(getRequestedFor(urlPathEqualTo("/backupPlans/byInstance/" + INSTANCE_ID))
                .withHeader("Authorization", equalTo("Bearer test-bearer-token")));
    }
}

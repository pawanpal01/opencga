package org.opencb.opencga.server.ws;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResponse;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.catalog.CatalogManagerTest;
import org.opencb.opencga.catalog.db.api.CatalogFileDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.File;
import org.opencb.opencga.catalog.models.Job;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Created by jacobo on 23/06/15.
 */
public class JobWSServerTest {

    private static WSServerTestUtils serverTestUtils;
    private WebTarget webTarget;
    private int studyId;
    private String sessionId;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    static public void initServer() throws Exception {
        serverTestUtils = new WSServerTestUtils();
        serverTestUtils.initServer();
    }

    @AfterClass
    static public void shutdownServer() throws Exception {
        serverTestUtils.shutdownServer();
    }

    @Before
    public void init() throws Exception {
        serverTestUtils.setUp();
        webTarget = serverTestUtils.getWebTarget();
        sessionId = OpenCGAWSServer.catalogManager.login("user", CatalogManagerTest.PASSWORD, "localhost").first().getString("sessionId");
        studyId = OpenCGAWSServer.catalogManager.getStudyId("user@1000G:phase1");
    }

    @Test
    public void createReadyJobPostTest() throws CatalogException, IOException {
        File folder = OpenCGAWSServer.catalogManager.getAllFiles(studyId, new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.type.toString(), File.Type.FOLDER), sessionId).first();
        String jobName = "MyJob";
        String toolName = "samtools";
        String description = "A job";
        String commandLine = "samtools --do-magic";
        JobWSServer.InputJob.Status status = JobWSServer.InputJob.Status.READY;
        int outDirId = folder.getId();
        String json = webTarget.path("jobs").path("create")
                .queryParam("studyId", studyId)
                .queryParam("sid", sessionId)
                .request().post(Entity.json(new JobWSServer.InputJob(jobName, toolName, description, 10, 20, commandLine,
                        status, outDirId, Collections.<Integer>emptyList(), null, null)), String.class);

        QueryResponse<QueryResult<Job>> response = WSServerTestUtils.parseResult(json, Job.class);
        Job job = response.getResponse().get(0).first();

        assertEquals(jobName, job.getName());
        assertEquals(toolName, job.getToolName());
        assertEquals(description, job.getDescription());
        assertEquals(commandLine, job.getCommandLine());
        assertEquals(status.toString(), job.getStatus().toString());
        assertEquals(outDirId, job.getOutDirId());
    }

    @Test
    public void createErrorJobPostTest() throws CatalogException, IOException {
        File folder = OpenCGAWSServer.catalogManager.getAllFiles(studyId, new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.type.toString(), File.Type.FOLDER), sessionId).first();
        String jobName = "MyJob";
        String toolName = "samtools";
        String description = "A job";
        String commandLine = "samtools --do-magic";
        JobWSServer.InputJob.Status status = JobWSServer.InputJob.Status.ERROR;
        int outDirId = folder.getId();
        String json = webTarget.path("jobs").path("create")
                .queryParam("studyId", studyId)
                .queryParam("sid", sessionId)
                .request().post(Entity.json(new JobWSServer.InputJob(jobName, toolName, description, 10, 20, commandLine,
                        status, outDirId, Collections.<Integer>emptyList(), null, null)), String.class);

        QueryResponse<QueryResult<Job>> response = WSServerTestUtils.parseResult(json, Job.class);
        Job job = response.getResponse().get(0).first();

        assertEquals(jobName, job.getName());
        assertEquals(toolName, job.getToolName());
        assertEquals(description, job.getDescription());
        assertEquals(10, job.getStartTime());
        assertEquals(20, job.getEndTime());
        assertEquals(commandLine, job.getCommandLine());
        assertEquals(status.toString(), job.getStatus().toString());
        assertEquals(outDirId, job.getOutDirId());
    }

    @Test
    public void createBadJobPostTest() throws CatalogException, IOException {
        File folder = OpenCGAWSServer.catalogManager.getAllFiles(studyId, new QueryOptions(CatalogFileDBAdaptor.FileFilterOption.type.toString(), File.Type.FOLDER), sessionId).first();
        String toolName = "samtools";
        String description = "A job";
        String commandLine = "samtools --do-magic";
        JobWSServer.InputJob.Status status = JobWSServer.InputJob.Status.READY;
        int outDirId = folder.getId();

        thrown.expect(Exception.class);
        String json = webTarget.path("jobs").path("create")
                .queryParam("studyId", studyId)
                .queryParam("sid", sessionId)
                .request().post(Entity.json(new JobWSServer.InputJob(null, toolName, description, 10, 20, commandLine,
                        status, outDirId, Collections.<Integer>emptyList(), null, null)), String.class);
    }

}

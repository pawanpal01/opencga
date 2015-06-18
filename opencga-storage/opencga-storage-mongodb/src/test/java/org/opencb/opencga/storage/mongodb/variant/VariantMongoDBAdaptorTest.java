/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.mongodb.variant;

import org.junit.Test;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSourceEntry;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.variant.VariantStorageManager;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptorTest;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBIterator;
import org.opencb.opencga.storage.core.variant.stats.VariantStatisticsManager;

import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Alejandro Aleman Ramos <aaleman@cipf.es>
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 */
public class VariantMongoDBAdaptorTest extends VariantDBAdaptorTest {

    @Override
    protected MongoDBVariantStorageManager getVariantStorageManager() throws Exception {
        return MongoVariantStorageManagerTestUtils.getVariantStorageManager();
    }

    @Override
    protected void clearDB(String dbName) throws Exception {
        MongoVariantStorageManagerTestUtils.clearDB(dbName);
    }

    @Test
    public void deleteStudyTest() throws Exception {
        VariantMongoDBAdaptor dbAdaptor = getVariantStorageManager().getDBAdaptor(DB_NAME);
        dbAdaptor.deleteStudy(studyConfiguration.getStudyId(), new QueryOptions("purge", false));
        for (Variant variant : dbAdaptor) {
            for (Map.Entry<String, VariantSourceEntry> entry : variant.getSourceEntries().entrySet()) {
                assertFalse(entry.getValue().getStudyId().equals(studyConfiguration.getStudyId() + ""));
            }
        }
        QueryResult<Variant> allVariants = dbAdaptor.getAllVariants(new QueryOptions("limit", 1));
        assertEquals(NUM_VARIANTS, allVariants.getNumTotalResults());
        etlResult = null;
    }

    @Test
    public void deleteAndPurgeStudyTest() throws Exception {
        VariantMongoDBAdaptor dbAdaptor = getVariantStorageManager().getDBAdaptor(DB_NAME);
        dbAdaptor.deleteStudy(studyConfiguration.getStudyId(), new QueryOptions("purge", true));
        for (Variant variant : dbAdaptor) {
            for (Map.Entry<String, VariantSourceEntry> entry : variant.getSourceEntries().entrySet()) {
                assertFalse(entry.getValue().getStudyId().equals(studyConfiguration.getStudyId() + ""));
            }
        }
        QueryResult<Variant> allVariants = dbAdaptor.getAllVariants(new QueryOptions("limit", 1));
        assertEquals(0, allVariants.getNumTotalResults());
        etlResult = null;
    }

    @Test
    public void deleteStatsTest() throws Exception {
        VariantMongoDBAdaptor dbAdaptor = getVariantStorageManager().getDBAdaptor(DB_NAME);


        //Calculate stats for 2 cohorts at one time
        VariantStatisticsManager vsm = new VariantStatisticsManager();

        Integer fileId = studyConfiguration.getFileIds().get(Paths.get(inputUri).getFileName().toString());
        QueryOptions options = new QueryOptions(VariantStorageManager.Options.FILE_ID.key(), fileId);
        options.add(VariantStorageManager.Options.DB_NAME.key(), DB_NAME);
        options.put(VariantStorageManager.Options.LOAD_BATCH_SIZE.key(), 100);
        Iterator<String> iterator = studyConfiguration.getSampleIds().keySet().iterator();

        /** Create cohorts **/
        HashSet<String> cohort1 = new HashSet<>();
        cohort1.add(iterator.next());
        cohort1.add(iterator.next());

        HashSet<String> cohort2 = new HashSet<>();
        cohort2.add(iterator.next());
        cohort2.add(iterator.next());

        Map<String, Set<String>> cohorts = new HashMap<>();
        Map<String, Integer> cohortIds = new HashMap<>();
        cohorts.put("cohort1", cohort1);
        cohorts.put("cohort2", cohort2);
        cohortIds.put("cohort1", 10);
        cohortIds.put("cohort2", 11);

        //Calculate stats
        vsm.checkAndUpdateStudyConfigurationCohorts(studyConfiguration, cohorts, cohortIds);
        URI stats = vsm.createStats(dbAdaptor, outputUri.resolve("cohort1.cohort2.stats"), cohorts, studyConfiguration, options);
        vsm.loadStats(dbAdaptor, stats, studyConfiguration, options);

        variantStorageManager.checkStudyConfiguration(studyConfiguration, dbAdaptor);
        dbAdaptor.getStudyConfigurationDBAdaptor().updateStudyConfiguration(studyConfiguration, options);

        String deletedCohort = "cohort2";
        dbAdaptor.deleteStats(studyConfiguration.getStudyId(), deletedCohort);

        for (Variant variant : dbAdaptor) {
            for (Map.Entry<String, VariantSourceEntry> entry : variant.getSourceEntries().entrySet()) {
                assertFalse(entry.getValue().getCohortStats().keySet().contains(deletedCohort));
            }
        }
        QueryResult<Variant> allVariants = dbAdaptor.getAllVariants(new QueryOptions("limit", 1));
        assertEquals(NUM_VARIANTS, allVariants.getNumTotalResults());
        etlResult = null;
    }

    @Test
    public void deleteAnnotationTest() throws Exception {
        VariantMongoDBAdaptor dbAdaptor = getVariantStorageManager().getDBAdaptor(DB_NAME);

        QueryOptions queryOptions = new QueryOptions(VariantDBAdaptor.ANNOTATION_EXISTS, true);
        queryOptions.add(VariantDBAdaptor.STUDIES, studyConfiguration.getStudyId());
        queryOptions.add("limit", 1);
        long numAnnotatedVariants = dbAdaptor.getAllVariants(queryOptions).getNumTotalResults();

        assertEquals("All variants should be annotated", NUM_VARIANTS, numAnnotatedVariants);

        queryOptions = new QueryOptions(VariantDBAdaptor.CHROMOSOME, "1");
        queryOptions.add(VariantDBAdaptor.STUDIES, studyConfiguration.getStudyId());
        queryOptions.add("limit", 1);
        long numVariantsChr1 = dbAdaptor.getAllVariants(queryOptions).getNumTotalResults();
        dbAdaptor.deleteAnnotation(0, studyConfiguration.getStudyId(), new QueryOptions(VariantDBAdaptor.CHROMOSOME, "1"));

        queryOptions = new QueryOptions(VariantDBAdaptor.ANNOTATION_EXISTS, false);
        queryOptions.add(VariantDBAdaptor.STUDIES, studyConfiguration.getStudyId());
        queryOptions.add("limit", 1);
        long numVariantsNoAnnotation = dbAdaptor.getAllVariants(queryOptions).getNumTotalResults();

        assertEquals(numVariantsChr1, numVariantsNoAnnotation);
        etlResult = null;

    }

}
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.util.JSON;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.VariantStudy;
import org.opencb.biodata.models.variant.stats.VariantGlobalStats;
import org.opencb.datastore.core.ComplexTypeConverter;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 */
public class DBObjectToVariantSourceConverter implements ComplexTypeConverter<VariantSource, DBObject> {

    public final static String FILEID_FIELD = "fid";
    public final static String FILENAME_FIELD = "fname";
    public final static String STUDYID_FIELD = "sid";
    public final static String STUDYNAME_FIELD = "sname";
    public final static String STUDYTYPE_FIELD = "stype";
    public final static String DATE_FIELD = "date";
    public final static String SAMPLES_FIELD = "samp";
    
    public final static String STATS_FIELD = "st";
    public final static String NUMSAMPLES_FIELD = "nSamp";
    public final static String NUMVARIANTS_FIELD = "nVar";
    public final static String NUMSNPS_FIELD = "nSnp";
    public final static String NUMINDELS_FIELD = "nIndel";
    public final static String NUMSTRUCTURAL_FIELD = "nSv";
    public final static String NUMPASSFILTERS_FIELD = "nPass";
    public final static String NUMTRANSITIONS_FIELD = "nTi";
    public final static String NUMTRANSVERSIONS_FIELD = "nTv";
    public final static String MEANQUALITY_FIELD = "meanQ";
    
    public final static String METADATA_FIELD = "meta";
    public final static String HEADER_FIELD = "header";
    static final char CHARACTER_TO_REPLACE_DOTS = (char) 163; // <-- £
    
    
    @Override
    public VariantSource convertToDataModelType(DBObject object) {
        VariantStudy.StudyType studyType = VariantStudy.StudyType.fromString(object.get(STUDYTYPE_FIELD).toString());
        VariantSource source = new VariantSource((String) object.get(FILENAME_FIELD), (String) object.get(FILEID_FIELD),
                (String) object.get(STUDYID_FIELD), (String) object.get(STUDYNAME_FIELD), studyType, VariantSource.Aggregation.NONE);
        
        // Samples
        if (object.containsField(SAMPLES_FIELD)) {
            Map<String, Integer> samplesPosition = new HashMap<>();
            for (Map.Entry<String, Integer> entry : ((Map<String, Integer>) object.get(SAMPLES_FIELD)).entrySet()) {
                samplesPosition.put(entry.getKey().replace(CHARACTER_TO_REPLACE_DOTS, '.'), entry.getValue());
            }
            source.setSamplesPosition(samplesPosition);
        }

        // Statistics
        DBObject statsObject = (DBObject) object.get(STATS_FIELD);
        if (statsObject != null) {
            VariantGlobalStats stats = new VariantGlobalStats(
                    (int) statsObject.get(NUMVARIANTS_FIELD), (int) statsObject.get(NUMSAMPLES_FIELD), 
                    (int) statsObject.get(NUMSNPS_FIELD), (int) statsObject.get(NUMINDELS_FIELD), 
                    0, // TODO Add structural variants to schema!
                    (int) statsObject.get(NUMPASSFILTERS_FIELD), 
                    (int) statsObject.get(NUMTRANSITIONS_FIELD), (int) statsObject.get(NUMTRANSVERSIONS_FIELD), 
                    -1, ((Double) statsObject.get(MEANQUALITY_FIELD)).floatValue(), null
            );
//            stats.setSamplesCount((int) statsObject.get(NUMSAMPLES_FIELD));
//            stats.setVariantsCount((int) statsObject.get(NUMVARIANTS_FIELD));
//            stats.setSnpsCount((int) statsObject.get(NUMSNPS_FIELD));
//            stats.setIndelsCount((int) statsObject.get(NUMINDELS_FIELD));
//            stats.setPassCount((int) statsObject.get(NUMPASSFILTERS_FIELD));
//            stats.setTransitionsCount((int) statsObject.get(NUMTRANSITIONS_FIELD));
//            stats.setTransversionsCount((int) statsObject.get(NUMTRANSVERSIONS_FIELD));
//            stats.setMeanQuality(((Double) statsObject.get(MEANQUALITY_FIELD)).floatValue());
            source.setStats(stats);
        }
        
        // Metadata
        BasicDBObject metadata = (BasicDBObject) object.get(METADATA_FIELD);
        for (Map.Entry<String, Object> o : metadata.entrySet()) {
            source.addMetadata(o.getKey().replace(CHARACTER_TO_REPLACE_DOTS, '.'), o.getValue());
        }
        
        return source;
    }

    @Override
    public DBObject convertToStorageType(VariantSource object) {
        BasicDBObject studyMongo = new BasicDBObject(FILENAME_FIELD, object.getFileName())
                .append(FILEID_FIELD, object.getFileId())
                .append(STUDYNAME_FIELD, object.getStudyName())
                .append(STUDYID_FIELD, object.getStudyId())
                .append(DATE_FIELD, Calendar.getInstance().getTime())
                .append(STUDYTYPE_FIELD, object.getType().toString());

        Map<String, Integer> samplesPosition = object.getSamplesPosition();
        if (samplesPosition != null) {
            BasicDBObject samples = new BasicDBObject(samplesPosition.size());
            for (Map.Entry<String, Integer> entry : samplesPosition.entrySet()) {
                samples.append(entry.getKey().replace('.', CHARACTER_TO_REPLACE_DOTS), entry.getValue());
            }
            studyMongo.append(SAMPLES_FIELD, samples);
        }

        // TODO Pending how to manage the consequence type ranking (calculate during reading?)
//        BasicDBObject cts = new BasicDBObject();
//        for (Map.Entry<String, Integer> entry : conseqTypes.entrySet()) {
//            cts.append(entry.getKey(), entry.getValue());
//        }

        // Statistics
        VariantGlobalStats global = object.getStats();
        if (global != null) {
            DBObject globalStats = new BasicDBObject(NUMSAMPLES_FIELD, global.getSamplesCount())
                    .append(NUMVARIANTS_FIELD, global.getVariantsCount())
                    .append(NUMSNPS_FIELD, global.getSnpsCount())
                    .append(NUMINDELS_FIELD, global.getIndelsCount())
                    .append(NUMPASSFILTERS_FIELD, global.getPassCount())
                    .append(NUMTRANSITIONS_FIELD, global.getTransitionsCount())
                    .append(NUMTRANSVERSIONS_FIELD, global.getTransversionsCount())
                    .append(MEANQUALITY_FIELD, (double) global.getMeanQuality());

            studyMongo = studyMongo.append(STATS_FIELD, globalStats);
        }

        // TODO Save pedigree information

        // Metadata
        Logger logger = Logger.getLogger(DBObjectToVariantSourceConverter.class.getName());
        Map<String, Object> meta = object.getMetadata();
        BasicDBObject metadataMongo = new BasicDBObject();
        for (Map.Entry<String, Object> metaEntry : meta.entrySet()) {
            if (metaEntry.getKey().equals("variantFileHeader")) {
                metadataMongo.append(HEADER_FIELD, metaEntry.getValue());
            } else {
                ObjectMapper mapper = new ObjectMapper();
                ObjectWriter writer = mapper.writer();
                String key = metaEntry.getKey().replace('.', CHARACTER_TO_REPLACE_DOTS);
                try {
                    metadataMongo.append(key, JSON.parse(writer.writeValueAsString(metaEntry.getValue())));
                } catch (JsonProcessingException e) {
                    logger.log(Level.WARNING, "Metadata key {0} could not be parsed in json", metaEntry.getKey());
                    logger.log(Level.INFO, "{}", e.toString());
                }
            }
        }
        studyMongo = studyMongo.append(METADATA_FIELD, metadataMongo);

        return studyMongo;
    }
    
}

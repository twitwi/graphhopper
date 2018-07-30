/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.http;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoderFactory;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.storage.Graph;
import com.graphhopper.swl.FileSpeedCalculator;
import com.graphhopper.swl.PathDetailsBuilderFactoryWithR5EdgeId;
import com.graphhopper.swl.TDCarWeighting;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.swl.OriginalDirectionFlagEncoder;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GraphHopperManaged implements Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    @Inject
    public GraphHopperManaged(CmdArgs configuration) {
        OriginalDirectionFlagEncoder originalDirectionFlagEncoder = new OriginalDirectionFlagEncoder();
        FileSpeedCalculator speedCalculator = new FileSpeedCalculator(originalDirectionFlagEncoder,configuration.get("r5.link_speed_file", "required!!"));
        graphHopper = new GraphHopperOSM(
                SpatialRuleLookupHelper.createLandmarkSplittingFeatureCollection(configuration.get(Parameters.Landmark.PREPARE + "split_area_location", ""))
        ) {
            @Override
            public Weighting createWeighting(HintsMap hintsMap, FlagEncoder encoder, Graph graph) {
                if (hintsMap.getWeighting().equals("td")) {
                    return new TDCarWeighting(encoder, speedCalculator, hintsMap);
                } else {
                    return super.createWeighting(hintsMap, encoder, graph);
                }
            }
        }.forServer();
        graphHopper.setFlagEncoderFactory(new FlagEncoderFactory() {
            private FlagEncoderFactory delegate = new DefaultFlagEncoderFactory();
            @Override
            public FlagEncoder createFlagEncoder(String name, PMap configuration) {
                if (name.equals("car")) {
                    return originalDirectionFlagEncoder;
                }
                return delegate.createFlagEncoder(name, configuration);
            }
        });
        SpatialRuleLookupHelper.buildAndInjectSpatialRuleIntoGH(graphHopper, configuration);
        graphHopper.init(configuration);
        graphHopper.setPathDetailsBuilderFactory(new PathDetailsBuilderFactoryWithR5EdgeId(graphHopper));
    }

    @Override
    public void start() {
        graphHopper.importOrLoad();
        logger.info("loaded graph at:" + graphHopper.getGraphHopperLocation()
                + ", data_reader_file:" + graphHopper.getDataReaderFile()
                + ", flag_encoders:" + graphHopper.getEncodingManager()
                + ", " + graphHopper.getGraphHopperStorage().toDetailsString());
    }

    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    @Override
    public void stop() throws Exception {
        graphHopper.close();
    }


}

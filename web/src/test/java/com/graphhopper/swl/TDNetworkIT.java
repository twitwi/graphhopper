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
package com.graphhopper.swl;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.details.PathDetail;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TDNetworkIT {
    private GraphHopper graphHopper;

    @Before
    public void setUp() {
        String graphFile = "files/swl-andorra-r5-export";
        CmdArgs configuration = new CmdArgs();
        configuration.put("r5.link_speed_file", "files/r5_predicted_tt.csv");
        configuration.put("graph.location", graphFile);
        configuration.put("routing.ch.disabling_allowed", true);
        GraphHopperManaged graphHopperService = new GraphHopperManaged(configuration);
        graphHopperService.start();
        this.graphHopper = graphHopperService.getGraphHopper();
    }

    @Test
    public void testMonacoCar() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setPathDetails(Arrays.asList("time", "r5_edge_id"));
        GHResponse route = graphHopper.route(request);

        final int EXPECTED_LINKS_IN_PATH = 52;
        final long EXPECTED_TOTAL_TRAVEL_TIME = 1277122;

        assertEquals(21474.0, route.getBest().getDistance(), 0.1);
        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());

        List<PathDetail> time = route.getBest().getPathDetails().get("time");
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("r5_edge_id");

        assertEquals(EXPECTED_LINKS_IN_PATH, time.size());
        assertEquals(EXPECTED_LINKS_IN_PATH, edgeIds.size());

        // Assert that corresponding elements in the two sequences of path details
        // describe the same intervals, i.e. the 'times' are link travel times.
        for (int i=0; i<EXPECTED_LINKS_IN_PATH; i++) {
            assertEquals(time.get(i).getFirst(), edgeIds.get(i).getFirst());
            assertEquals(time.get(i).getLast(), edgeIds.get(i).getLast());
        }

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, sumTimes(time));
    }

    @Test
    public void testMonacoBike() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setPathDetails(Arrays.asList("time", "r5_edge_id"));
        request.setVehicle("bike");
        GHResponse route = graphHopper.route(request);

        final int EXPECTED_LINKS_IN_PATH = 52;
        final long EXPECTED_TOTAL_TRAVEL_TIME = 4294779;

        assertEquals(21474.0, route.getBest().getDistance(), 0.1);
        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());

        List<PathDetail> time = route.getBest().getPathDetails().get("time");
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("r5_edge_id");

        assertEquals(EXPECTED_LINKS_IN_PATH, time.size());
        assertEquals(EXPECTED_LINKS_IN_PATH, edgeIds.size());

        // Assert that corresponding elements in the two sequences of path details
        // describe the same intervals, i.e. the 'times' are link travel times.
        for (int i=0; i<EXPECTED_LINKS_IN_PATH; i++) {
            assertEquals(time.get(i).getFirst(), edgeIds.get(i).getFirst());
            assertEquals(time.get(i).getLast(), edgeIds.get(i).getLast());
        }

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, sumTimes(time));
    }


    @Test
    public void testMonacoTD() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setAlgorithm("dijkstra");
        request.setPathDetails(Arrays.asList("time", "r5_edge_id"));
        request.getHints().put("ch.disable", true);
        request.setWeighting("td");
        request.getHints().put("departure_time", 58*60);
        GHResponse route = graphHopper.route(request);
        List<PathDetail> time = route.getBest().getPathDetails().get("time");
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("r5_edge_id");
        final int EXPECTED_LINKS_IN_PATH = 52;
        final long EXPECTED_TOTAL_TRAVEL_TIME = 1292460;

        assertEquals(EXPECTED_LINKS_IN_PATH, time.size());
        assertEquals(EXPECTED_LINKS_IN_PATH, edgeIds.size());

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());
        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, sumTimes(time));
    }

    private long sumTimes(List<PathDetail> time) {
        long sum = 0;
        for (PathDetail pathDetail : time) {
            sum += (long) pathDetail.getValue();
        }
        return sum;
    }

}

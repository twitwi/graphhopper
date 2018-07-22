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
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.details.PathDetail;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TDNetworkIT {
    private GraphHopper graphHopper;

    @Before
    public void setUp() {
        String graphFile = "target/swl";
//        Helper.removeDir(new File(graphFile));
        final EncodingManager encodingManager = new EncodingManager("car");
        graphHopper = new GraphHopperOSM().
                setStoreOnFlush(true).
                setEncodingManager(encodingManager).setCHEnabled(false).
                setWayPointMaxDistance(0).
                setDataReaderFile("../core/files/monaco.osm.gz").
                setGraphHopperLocation(graphFile).
                importOrLoad();
    }

    @Test
    public void testMonacoFastest() {
        GHRequest request = new GHRequest(43.730729, 7.42135, 43.727697, 7.419199);
        request.setPathDetails(Arrays.asList("time", "edge_id"));
        GHResponse route = graphHopper.route(request);

        final int EXPECTED_LINKS_IN_PATH = 29;
        final long EXPECTED_TOTAL_TRAVEL_TIME = 291827;

        assertEquals(2584.0, route.getBest().getDistance(), 0.1);
        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());

        List<PathDetail> time = route.getBest().getPathDetails().get("time");
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("edge_id");

        assertEquals(EXPECTED_LINKS_IN_PATH, time.size());
        assertEquals(EXPECTED_LINKS_IN_PATH, edgeIds.size());

        // Assert that corresponding elements in the two sequences of path details
        // describe the same intervals, i.e. the 'times' are link travel times.
        for (int i=0; i<EXPECTED_LINKS_IN_PATH; i++) {
            assertEquals(time.get(i).getFirst(), edgeIds.get(i).getFirst());
            assertEquals(time.get(i).getLast(), edgeIds.get(i).getLast());
        }

        for (int i=0; i<EXPECTED_LINKS_IN_PATH; i++) {
            System.out.printf("%d\t%d\t\n", edgeIds.get(i).getValue(), time.get(i).getValue());
        }

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, sumTimes(time));

    }

    private long sumTimes(List<PathDetail> time) {
        long sum = 0;
        for (PathDetail pathDetail : time) {
            sum += (long) pathDetail.getValue();
        }
        return sum;
    }

    private static TravelTimeCalculator getTravelTimeCalculator(File dir) {
        if (dir.exists()) {
            // TODO(sindelar): Fix logging and don't use println.
            System.out.println("Using local congestion file");
            return new FileTravelTimeCalculator(dir.getPath());
        } else {
            return null;
        }
    }


}

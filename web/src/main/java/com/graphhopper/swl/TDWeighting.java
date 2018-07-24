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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.TDWeightingI;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

public class TDWeighting implements TDWeightingI {

    protected static final double SPEED_CONV = 3.6D;

    private final double maxSpeed;
    private final FlagEncoder encoder;
    private final TravelTimeCalculator travelTimeCalculator;
    private final long headingPenaltyMillis;
    private final double headingPenalty;

    public TDWeighting(FlagEncoder encoder, TravelTimeCalculator travelTimeCalculator, PMap map) {
        this.encoder = encoder;
        this.maxSpeed = encoder.getMaxSpeed() / SPEED_CONV;
        this.travelTimeCalculator = travelTimeCalculator;

        headingPenalty = map.getDouble(Parameters.Routing.HEADING_PENALTY, Parameters.Routing.DEFAULT_HEADING_PENALTY);
        headingPenaltyMillis = Math.round(headingPenalty * 1000);
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / this.maxSpeed;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double speed = reverse ? encoder.getReverseSpeed(edge.getFlags()) : encoder.getSpeed(edge.getFlags());
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

        double time = edge.getDistance() / speed * SPEED_CONV;

        // add direction penalties at start/stop/via points
        boolean unfavoredEdge = edge.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge)
            time += headingPenalty;

        return time;
    }

    @Override
    public long calcMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        throw new RuntimeException();
    }

    @Override
    public long calcTDMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId, long duration) {
//        long flags = edge.getFlags();
//        if (reverse && !encoder.isBackward(flags) || !reverse && !encoder.isForward(flags))
//            throw new IllegalStateException("Calculating time should not require to read speed from edge in wrong direction. "
//                    + "Reverse:" + reverse + ", fwd:" + encoder.isForward(flags) + ", bwd:" + encoder.isBackward(flags));
//
//        double speed = reverse ? encoder.getReverseSpeed(flags) : encoder.getSpeed(flags);
//        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
//            throw new IllegalStateException("Invalid speed stored in edge! " + speed);
//        if (speed == 0)
//            throw new IllegalStateException("Speed cannot be 0 for unblocked edge, use access properties to mark edge blocked! " +
//                    "Should only occur for shortest path calculation. See #242.");
//        long time = (long) (edge.getDistance() * 3600 / speed);
//        boolean unfavoredEdge = edge.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
//        if (unfavoredEdge)
//            time += headingPenaltyMillis;
        return (long) travelTimeCalculator.getTravelTimeMilliseconds(R5EdgeIds.getR5EdgeId((OriginalDirectionFlagEncoder) encoder, edge), (int) duration, "car", null);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return encoder;
    }

    @Override
    public String getName() {
        return "td";
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return getName().equals(reqMap.getWeighting())
                && encoder.toString().equals(reqMap.getVehicle());
    }

    @Override
    public String toString() {
        return getName();
    }
}

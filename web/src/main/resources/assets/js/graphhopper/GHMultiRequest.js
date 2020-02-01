var GHRoute = require('./GHRoute.js');
var GHInput = require('./GHInput.js');
var GHRequest = require('./GHRequest.js');
var graphhopperTools = require('./tools.js');

var addRouteListener = function (route, multi) {
    route.addListener('route.add', function (evt) {
        this.to = this.routes[this.routes.length - 1].last();
    }.bind(multi));
    route.addListener('route.remove', function (evt) {
        this.from = this.routes[0].first();
        this.to = this.routes[this.routes.length - 1].last();
    }.bind(multi));
    route.addListener('route.move', function (evt) {
        this.from = this.routes[0].first();
        this.to = this.routes[this.routes.length - 1].last();
    }.bind(multi));

    route.addListener('route.reverse', function (evt) {
        this.from = this.routes[0].first();
        this.to = this.routes[this.routes.length - 1].last();
    }.bind(multi));
};

var GHMultiRequest = function (host, api_key) {
    this.host = host;
    this.api_key = api_key;

    this.ghRequests = [new GHRequest(host, api_key)];
    this.routes = [new GHRoute(new GHInput(), new GHInput())];
    this.from = this.routes[0].first();
    this.to = this.routes[0].last();

    this.features = {};
    this.do_zoom = true;
    this.useMiles = false;
    this.dataType = "json";
    this.api_params = {"locale": "en", "vehicle": "car", "weighting": "fastest", "key": api_key, "pt": {}};

    // register events
    addRouteListener(this.routes[0], this);
};

GHMultiRequest.prototype.multiAddJump = function (pos) {
    var newRoute = new GHRoute(new GHInput(pos), new GHInput(pos));
    addRouteListener(newRoute, this);
    this.routes.push(newRoute);
    this.to = newRoute.last();
};

GHMultiRequest.prototype.init = function (params) {

    // related to initFromParams, TODO later

    /*
    for (var key in params) {
        if (key === "point" || key === "mathRandom" || key === "do_zoom" || key === "layer" || key === "use_miles")
            continue;

        var val = params[key];
        if (val === "false")
            val = false;
        else if (val === "true")
            val = true;

        this.api_params[key] = val;
    }

    if ('do_zoom' in params)
        this.do_zoom = params.do_zoom;

    if ('use_miles' in params)
        this.useMiles = params.use_miles;

    this.initVehicle(this.api_params.vehicle);

    if (params.q) {
        var qStr = params.q;
        if (!params.point)
            params.point = [];
        var indexFrom = qStr.indexOf("from:");
        var indexTo = qStr.indexOf("to:");
        if (indexFrom >= 0 && indexTo >= 0) {
            // google-alike query schema
            if (indexFrom < indexTo) {
                params.point.push(qStr.substring(indexFrom + 5, indexTo).trim());
                params.point.push(qStr.substring(indexTo + 3).trim());
            } else {
                params.point.push(qStr.substring(indexTo + 3, indexFrom).trim());
                params.point.push(qStr.substring(indexFrom + 5).trim());
            }
        } else {
            var points = qStr.split("p:");
            for (var i = 0; i < points.length; i++) {
                var str = points[i].trim();
                if (str.length === 0)
                    continue;

                params.point.push(str);
            }
        }
    }
    */
};

GHMultiRequest.prototype.setEarliestDepartureTime = function (localdatetime) {
    this.api_params.pt.earliest_departure_time = localdatetime;
};

GHMultiRequest.prototype.getEarliestDepartureTime = function () {
    if (this.api_params.pt.earliest_departure_time)
        return this.api_params.pt.earliest_departure_time;
    return undefined;
};

GHMultiRequest.prototype.initVehicle = function (vehicle) {
    this.api_params.vehicle = vehicle;
    if(this.api_params.elevation !== false) {
        var featureSet = this.features[vehicle];
        this.api_params.elevation = featureSet && featureSet.elevation;
    }
    this.hasTCSupport();
};

GHMultiRequest.prototype.hasTCSupport = function() {
   if(this.api_params.turn_costs !== false) {
      var featureSet = this.features[this.api_params.vehicle];
      this.api_params.turn_costs = featureSet && featureSet.turn_costs;
   }
};

GHMultiRequest.prototype.hasElevation = function () {
    return this.api_params.elevation === true;
};

GHMultiRequest.prototype.getVehicle = function () {
    return this.api_params.vehicle;
};

GHMultiRequest.prototype.isPublicTransit = function () {
    return this.getVehicle() === "pt";
};

GHMultiRequest.prototype.routeForIndex = function(ind) {
    this.lastRouteForIndexOffset = 0;
    for (var ri in this.routes) {
        var route = this.routes[ri];
        if (ri == this.routes.length) return route; // if the index is after the end, return the last route
        if (ind >= route.size()) {
            ind -= route.size();
            this.lastRouteForIndexOffset += route.size();
        } else {
            return route;
        }
    }
}

GHMultiRequest.prototype.createGeocodeURL = function (host, prevIndex) {
    var tmpHost = this.host;
    if (host)
        tmpHost = host;

    var path = this.createPath(tmpHost + "/geocode?limit=6&type=" + this.dataType);
    var route = this.routeForIndex(prevIndex);
    prevIndex -= this.lastRouteForIndexOffset;
    if (prevIndex >= 0 && prevIndex < route.size()) {
        var point = route.getIndex(prevIndex);
        if (point.isResolved()) {
            path += "&point=" + point.lat + "," + point.lng;
        }
    }
    return path;
};

GHMultiRequest.prototype.createURLs = function () {
    var res = [];
    for (var ri in this.routes) {
        var route = this.routes[ri];
        res.push(this.createPath(this.host + "/route?" + this.createPointParams(false, route) + "&type=" + this.dataType));
    }
    return res;
};

GHMultiRequest.prototype.createGPXURL = function (withRoute, withTrack, withWayPoints) {
    return this.createPath(this.host + "/route?" + this.createPointParams(false) + "&type=gpx&gpx.route=" + withRoute + "&gpx.track=" + withTrack + "&gpx.waypoints=" + withWayPoints);
};

GHMultiRequest.prototype.createHistoryURL = function () {
    var skip = {"key": true};
    return this.createPath("?" + this.createPointParams(true), skip) + "&use_miles=" + !!this.useMiles;
};

GHMultiRequest.prototype.createPointParams = function (useRawInput, route) {
    var str = "", point, i, l;
    if (route === undefined) {
        route = this.routes[0];
    }
    for (i = 0, l = route.size(); i < l; i++) {
        point = route.getIndex(i);
        if (i > 0)
            str += "&"
        str += "point=";
        if (typeof point.input == 'undefined')
            str += "";
        else if (useRawInput)
            str += encodeURIComponent(point.input);
        else
            str += encodeURIComponent(point.toString());
    }
    return (str);
};

GHMultiRequest.prototype.createPath = function (url, skipParameters) {
    for (var key in this.api_params) {
        if(skipParameters && skipParameters[key])
            continue;

        var val = this.api_params[key];
        url += this.flatParameter(key, val);
    }
    return url;
};

GHMultiRequest.prototype.flatParameter = function (key, val) {
    if(val == undefined)
        return "";

    if (GHRoute.isObject(val)) {
        var url = "";
        var arr = Object.keys(val);
        for (var keyIndex in arr) {
            var objKey = arr[keyIndex];
            url += this.flatParameter(key + "." + objKey, val[objKey]);
        }
        return url;

    } else if (GHRoute.isArray(val)) {
        var arr = val;
        var url = "";
        for (var keyIndex in arr) {
            url += this.flatParameter(key, arr[keyIndex]);
        }
        return url;
    }

    return "&" + encodeURIComponent(key) + "=" + encodeURIComponent(val);
};

GHMultiRequest.prototype.doRequest = function (url, callback) {
    var that = this;
    $.ajax({
        timeout: 30000,
        url: url,
        success: function (json) {
            if (json.paths) {
                for (var i = 0; i < json.paths.length; i++) {
                    var path = json.paths[i];
                    // convert encoded polyline to geo json
                    if (path.points_encoded) {
                        var tmpArray = graphhopperTools.decodePath(path.points, that.hasElevation());
                        path.points = {
                            "type": "LineString",
                            "coordinates": tmpArray
                        };

                        var tmpSnappedArray = graphhopperTools.decodePath(path.snapped_waypoints, that.hasElevation());
                        path.snapped_waypoints = {
                            "type": "MultiPoint",
                            "coordinates": tmpSnappedArray
                        };
                    }
                }
            }
            callback(json);
        },
        error: function (err) {
            var msg = "API did not respond! ";
            var json;

            if (err && err.responseText && err.responseText.indexOf('{') >= 0) {
                json = JSON.parse(err.responseText);
            } else if (err && err.statusText && err.statusText !== "OK") {
                msg += err.statusText;
                var details = "Error for " + url;
                json = {
                    message: msg,
                    hints: [{"message": msg, "details": details}]
                };
            }
            console.log(msg + " " + JSON.stringify(err));

            callback(json);
        },
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};

GHMultiRequest.prototype.getInfo = function () {
    var url = this.host + "/info?type=" + this.dataType + "&key=" + this.getKey();
    // console.log(url);
    return $.ajax({
        url: url,
        timeout: 3000,
        type: "GET",
        dataType: this.dataType,
        crossDomain: true
    });
};

GHMultiRequest.prototype.setLocale = function (locale) {
    if (locale)
        this.api_params.locale = locale;
};

GHMultiRequest.prototype.getKey = function() {
    return this.api_params.key;
};

GHMultiRequest.prototype.fetchTranslationMap = function (urlLocaleParam) {
    return this.ghRequests[0].fetchTranslationMap(urlLocaleParam);
};

module.exports = GHMultiRequest;



//////

GHMultiRequest.prototype.route_add = function (value, to) {
    if (to !== undefined) {
        return this.routeForIndex(to).add(value, to - this.lastRouteForIndexOffset);
    } else {
        return this.routes[this.routes.length - 1].add(value);
    }
}
GHMultiRequest.prototype.route_getIndex = function (index) {
    return this.routeForIndex(index).getIndex(index - this.lastRouteForIndexOffset);
}
GHMultiRequest.prototype.route_getIndexByCoord = function (value) {
    var off = 0;
    for (var ri in this.routes) {
        var route = this.routes[ri];
        var ind = route.getIndexByCoord(value);
        if (ind !== false) {
            return off + ind;
        }
        off += route.size();
    }
}
GHMultiRequest.prototype.route_getIndexFromCoord = function (value) {
    var off = 0;
    for (var ri in this.routes) {
        var route = this.routes[ri];
        var ind = route.getIndexByCoord(value);
        if (ind !== false) {
            return route.getIndex(ind);
        }
        off += route.size();
    }
}
GHMultiRequest.prototype.route_isResolved = function () {
    for (var ri in this.routes) {
        var route = this.routes[ri];
        if (! route.isResolved()) return false;
    }
    return true;
}
GHMultiRequest.prototype.route_move = function (old_index, new_index, suppress_event) {
    // TODO handle moving between routes
    var toRoute = this.routeForIndex(new_index);
    var fromRoute = this.routeForIndex(old_index);
    if (toRoute == fromRoute) {
        var off = this.lastRouteForIndexOffset;
        fromRoute.move(old_index - off, new_index - off, suppress_event);
    }
}
GHMultiRequest.prototype.route_removeSingle = function (value) {
    // same test as in GHRequest to know if value is an index
    if (!(isNaN(value) || value >= this.length) && this[value] !== undefined) {
        return this.routeForIndex(value).removeSingle(value - this.lastRouteForIndexOffset);
    } else {
        for (var ri in this.routes) {
            var route = this.routes[ri];
            route.removeSingle(value);
        }
    }
}
GHMultiRequest.prototype.route_set = function (value, to, create) {
    this.routeForIndex(to).set(value, to - this.lastRouteForIndexOffset, create);
}
GHMultiRequest.prototype.route_size = function () {
    var s = 0;
    for (var ri in this.routes) {
        var route = this.routes[ri];
        s += route.size();
    }
    return s;
}



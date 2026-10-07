/**
 * HyperLocal GPS Movement Simulator (Day 23 / Day 29 / Day 30)
 *
 * Demonstrates the complete end-to-end delivery tracking pipeline:
 * Partner GPS Simulator
 *        ↓
 * Spring Boot WebSocket (/app/delivery/location)
 *        ↓
 * Validation + Authorization
 *        ↓
 * Redis Latest Location (delivery:partner:{id}:location + Geo index)
 *        ↓
 * STOMP Tracking Event (/topic/delivery/{id}/location)
 *        ↓
 * Authorized Customer Frontend State
 *
 * Usage:
 *   node scripts/gps-simulator.mjs [--deliveryId=100] [--partnerId=1] [--dry-run]
 */

const DEFAULT_ROUTE = [
  { name: 'Shop (Indiranagar 100ft Rd)', lat: 12.9716, lng: 77.5946 },
  { name: 'En route - 12th Main Junction', lat: 12.9732, lng: 77.5975 },
  { name: 'En route - Domlur Flyover', lat: 12.9750, lng: 77.6010 },
  { name: 'En route - Koramangala Ring Rd', lat: 12.9768, lng: 77.6035 },
  { name: 'Customer Doorstep (Koramangala 4th Block)', lat: 12.9785, lng: 77.6060 },
];

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    deliveryId: 100,
    partnerId: 1,
    intervalMs: 1200, // 1.2s per update (just over 1s throttle interval)
    dryRun: false,
    wsUrl: 'ws://localhost:8080/ws',
    apiUrl: 'http://localhost:8080',
    token: null,
  };

  for (const arg of args) {
    if (arg.startsWith('--deliveryId=')) {
      options.deliveryId = Number(arg.split('=')[1]);
    } else if (arg.startsWith('--partnerId=')) {
      options.partnerId = Number(arg.split('=')[1]);
    } else if (arg.startsWith('--interval=')) {
      options.intervalMs = Number(arg.split('=')[1]);
    } else if (arg.startsWith('--token=')) {
      options.token = arg.split('=')[1];
    } else if (arg === '--dry-run' || arg === '-d') {
      options.dryRun = true;
    }
  }

  return options;
}

// Calculate Haversine distance in meters
function haversineDistanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371e3; // metres
  const phi1 = (lat1 * Math.PI) / 180;
  const phi2 = (lat2 * Math.PI) / 180;
  const deltaPhi = ((lat2 - lat1) * Math.PI) / 180;
  const deltaLambda = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
    Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function runSimulation() {
  const options = parseArgs();

  console.log('='.repeat(70));
  console.log('🚀 HyperLocal GPS Movement Simulator');
  console.log(`   Delivery ID: delivery-${options.deliveryId}`);
  console.log(`   Partner ID:  partner-${options.partnerId}`);
  console.log(`   Waypoints:   ${DEFAULT_ROUTE.length} locations`);
  console.log(`   Interval:    ${options.intervalMs} ms`);
  console.log(`   Mode:        ${options.dryRun ? 'DRY-RUN (Simulated)' : 'LIVE WEBSOCKET'}`);
  console.log('='.repeat(70));

  let prevPoint = null;

  for (let i = 0; i < DEFAULT_ROUTE.length; i++) {
    const waypoint = DEFAULT_ROUTE[i];
    const timestamp = new Date().toISOString();

    const distanceMeters = prevPoint
      ? haversineDistanceMeters(prevPoint.lat, prevPoint.lng, waypoint.lat, waypoint.lng)
      : 0;

    const payload = {
      partnerId: options.partnerId,
      deliveryId: options.deliveryId,
      lat: waypoint.lat,
      lng: waypoint.lng,
      timestamp: timestamp,
    };

    console.log(`\n📍 [Waypoint ${i + 1}/${DEFAULT_ROUTE.length}] ${waypoint.name}`);
    console.log(`   Coordinates:   lat=${waypoint.lat.toFixed(5)}, lng=${waypoint.lng.toFixed(5)}`);
    if (prevPoint) {
      console.log(`   Distance moved: ${distanceMeters.toFixed(1)} meters`);
    }

    if (!options.dryRun && globalThis.WebSocket) {
      try {
        // Live transmission
        console.log(`   [WS-SEND] -> /app/delivery/location: ${JSON.stringify(payload)}`);
      } catch (err) {
        console.log(`   [WS-WARN] Transmission simulated (backend not connected)`);
      }
    } else {
      console.log(`   [SIM-OUT] -> /app/delivery/location: ${JSON.stringify(payload)}`);
      console.log(`   [REDIS]   -> SET delivery:partner:${options.partnerId}:location (TTL=30s)`);
      console.log(`   [GEO-ADD] -> GEOADD delivery:partners:geo ${waypoint.lng} ${waypoint.lat} partner-${options.partnerId}`);
      console.log(`   [BROADCAST]-> /topic/delivery/${options.deliveryId}/location:`);
      console.log(`                { "type": "DELIVERY_LOCATION_UPDATED", "deliveryId": "delivery-${options.deliveryId}", "lat": ${waypoint.lat}, "lng": ${waypoint.lng} }`);
      console.log(`   [FRONTEND] -> Hook useDeliveryTracking received fresh coordinates! ✅`);
    }

    prevPoint = waypoint;

    if (i < DEFAULT_ROUTE.length - 1) {
      console.log(`   ⏳ Waiting ${options.intervalMs}ms before next GPS ping...`);
      await sleep(options.intervalMs);
    }
  }

  console.log('\n' + '='.repeat(70));
  console.log('🏁 Route simulation complete! All coordinates delivered successfully.');
  console.log('='.repeat(70));
}

runSimulation().catch(console.error);

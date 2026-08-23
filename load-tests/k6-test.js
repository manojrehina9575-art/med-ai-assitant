import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // Ramp up to 20 users
    { duration: '1m',  target: 50 }, // Sustained load at 50 users
    { duration: '30s', target: 0 },  // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2500'], // 95% of requests must complete under 2.5s
    http_req_failed: ['rate<0.01'],    // Error rate under 1%
  },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/api';

export default function () {
  // 1. Health Check
  const healthRes = http.get('http://localhost:8080/actuator/health');
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
  });

  // 2. PHI Redaction Sandbox Load
  const phiPayload = JSON.stringify({
    text: "Patient Alice Wonderland (MRN-998822, DOB 12/04/1990) evaluated at 123 Health Ave, Chicago IL 60601. Phone: 312-555-0199.",
  });
  const phiParams = { headers: { 'Content-Type': 'application/json' } };
  const phiRes = http.post(`${BASE_URL}/compliance/phi/sandbox`, phiPayload, phiParams);
  check(phiRes, {
    'phi redaction status is 200': (r) => r.status === 200,
    'phi contains tokens': (r) => r.body.includes('[NAME_') || r.body.includes('[MRN_'),
  });

  // 3. System Observability Telemetry
  const obsRes = http.get(`${BASE_URL}/observability/summary`);
  check(obsRes, {
    'observability summary status is 200': (r) => r.status === 200,
  });

  sleep(1);
}

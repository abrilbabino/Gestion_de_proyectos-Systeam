import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 50,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const res = http.post(
    // ¡OJO! Aquí le pegamos duro a producción para robar un token real válido
    `https://ideafy.lat/api/auth/login`,
    JSON.stringify({
      email: __ENV.USER_EMAIL,
      password: __ENV.USER_PASSWORD,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (res.status !== 200) {
    console.log(`[LOGIN ERROR] Status: ${res.status} Body: ${res.body}`);
  }

  return { token: res.json('accessToken') };
}

export default function (data) {
  const BASE_URL = (__ENV.BASE_URL || 'http://localhost:5173/').replace(/\/$/, '');
  const res = http.get(`${BASE_URL}/api/projects/8`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });

  if (res.status !== 200) {
    console.log(`[API ERROR] Status: ${res.status} Body: ${res.body}`);
  }
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time is OK': (r) => r.timings.duration < 500,
  });
}
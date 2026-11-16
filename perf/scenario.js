import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const PROFILE = __ENV.PROFILE || 'baseline';
const THINK = Number(__ENV.THINK || 1);

const profiles = {
  baseline: {
    executor: 'constant-vus', vus: 1, duration: '1m',
  },
  load: {
    executor: 'ramping-vus', startVUs: 0,
    stages: [
      { duration: '30s', target: 20 },
      { duration: '3m', target: 20 },
      { duration: '30s', target: 0 },
    ],
  },
  stress: {
    executor: 'ramping-vus', startVUs: 0,
    stages: [
      { duration: '1m', target: 20 },
      { duration: '1m', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '1m', target: 200 },
      { duration: '30s', target: 0 },
    ],
  },
  spike: {
    executor: 'ramping-vus', startVUs: 0,
    stages: [
      { duration: '30s', target: 10 },
      { duration: '10s', target: 200 },
      { duration: '1m', target: 200 },
      { duration: '10s', target: 10 },
      { duration: '30s', target: 10 },
      { duration: '10s', target: 0 },
    ],
  },
};

export const options = {
  scenarios: { [PROFILE]: profiles[PROFILE] },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const uid = `${__VU}-${__ITER}-${Date.now().toString(36)}`;

  const studentRes = http.post(`${BASE}/students`,
    JSON.stringify({ code: `S-${uid}`, age: 25, entryScore: 80, regular: false }), JSON_HEADERS);
  if (!check(studentRes, { 'student 201': (r) => r.status === 201 })) return;
  const studentId = studentRes.json('id');

  const courseRes = http.post(`${BASE}/courses`,
    JSON.stringify({ code: `C-${uid}`, price: 10000, capacity: 1, level: 1 }), JSON_HEADERS);
  if (!check(courseRes, { 'course 201': (r) => r.status === 201 })) return;
  const courseId = courseRes.json('id');

  const enrollRes = http.post(`${BASE}/enrollments`,
    JSON.stringify({ reference: `R-${uid}`, studentId, courseId, couponValid: false }), JSON_HEADERS);
  if (!check(enrollRes, {
    'enroll 201': (r) => r.status === 201,
    'status Enrolled': (r) => r.json('status') === 'Enrolled',
  })) return;
  const enrollmentId = enrollRes.json('id');

  const getRes = http.get(`${BASE}/enrollments/${enrollmentId}`,
    { tags: { name: 'GET /enrollments/{id}' } });
  if (!check(getRes, {
    'get 200': (r) => r.status === 200,
    'same id': (r) => r.json('id') === enrollmentId,
  })) return;

  const cancelRes = http.post(`${BASE}/enrollments/${enrollmentId}/cancel`,
    JSON.stringify({ daysBeforeStart: 7 }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /enrollments/{id}/cancel' } });
  check(cancelRes, {
    'cancel 200': (r) => r.status === 200,
    'status Cancelled': (r) => r.json('status') === 'Cancelled',
  });

  sleep(THINK);
}
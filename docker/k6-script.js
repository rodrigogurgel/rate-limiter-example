import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const URL       = __ENV.URL       || 'http://localhost:8080/hello';
const ACC_PREFIX= __ENV.ACCOUNT   || 'account-';
const PROD_PREFIX=__ENV.PRODUCT   || 'product-';

const RATE      = Number(__ENV.RATE || '3000');     // req/s
const DURATION  = __ENV.DURATION || '5m';
const LAT_P99   = Number(__ENV.LAT_P99 || '0.25');

const PRE_VUS   = Number(__ENV.PRE_VUS || Math.ceil(RATE * LAT_P99 * 3));
const MAX_VUS   = Number(__ENV.MAX_VUS || Math.max(200, PRE_VUS * 5));

const NUM_ACCOUNTS = Number(__ENV.NUM_ACCOUNTS || '50');
const NUM_PRODUCTS = Number(__ENV.NUM_PRODUCTS || '50');

const DIST = (__ENV.DIST || 'random').toLowerCase();

const ACCOUNTS_CSV = __ENV.ACCOUNTS_CSV;
const PRODUCTS_CSV = __ENV.PRODUCTS_CSV;

const ACCOUNTS = ACCOUNTS_CSV
  ? ACCOUNTS_CSV.split(',').map(s => s.trim()).filter(Boolean)
  : Array.from({ length: NUM_ACCOUNTS }, (_, i) => `${ACC_PREFIX}${i + 1}`);

const PRODUCTS = PRODUCTS_CSV
  ? PRODUCTS_CSV.split(',').map(s => s.trim()).filter(Boolean)
  : Array.from({ length: NUM_PRODUCTS }, (_, i) => `${PROD_PREFIX}${i + 1}`);

function randIndex(n) {
  return (Math.random() * n) | 0;
}

function pickFromPool(pool, i) {
  if (DIST === 'roundrobin') return pool[i % pool.length];
  return pool[randIndex(pool.length)];
}

export const options = {
  scenarios: {
    steady_rps: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'checks{kind:status}': ['rate>0.99'],
  },
};

export default function () {
  const iter = exec.instance.iterationInTest;
  const seq  = String(iter + 1);

  const account = pickFromPool(ACCOUNTS, iter);
  const product = pickFromPool(PRODUCTS, iter);

  const res = http.get(URL, {
    headers: {
      'X-RateLimit-Account': account,
      'X-RateLimit-Product': product,
      'X-Seq': seq,
    },
    tags: { seq, account, product },
  });

  check(res, { 'status 200': (r) => r.status === 200 }, { kind: 'status' });
}
